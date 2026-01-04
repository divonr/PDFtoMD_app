package com.divonr.pdftomd.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divonr.pdftomd.data.GeminiRepository
import com.divonr.pdftomd.data.PdfRepository
import com.divonr.pdftomd.data.PreferenceManager
import com.divonr.pdftomd.data.ProjectEntity
import com.divonr.pdftomd.data.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class UiState(
    val apiKey: String? = null,
    val savedApiKeys: Set<String> = emptySet(),
    val activeModelId: String = PreferenceManager.DEFAULT_MODEL,
    val pdfFile: File? = null,
    val markdownContent: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentProjectId: Long? = null,
    val projects: List<ProjectEntity> = emptyList()
)

class MainViewModel(
    private val preferenceManager: PreferenceManager,
    private val geminiRepository: GeminiRepository,
    private val pdfRepository: PdfRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Combine flows to update state
            combine(
                preferenceManager.apiKey,
                preferenceManager.savedApiKeys,
                preferenceManager.modelId,
                projectRepository.allProjects
            ) { apiKey, savedKeys, modelId, projects ->
                UiState(
                    apiKey = apiKey,
                    savedApiKeys = savedKeys,
                    activeModelId = modelId,
                    projects = projects
                )
            }.collect { newState ->
                // Preserve current session state (pdf, md, etc)
                _uiState.value = _uiState.value.copy(
                    apiKey = newState.apiKey,
                    savedApiKeys = newState.savedApiKeys,
                    activeModelId = newState.activeModelId,
                    projects = newState.projects
                )
            }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            preferenceManager.saveApiKey(key)
        }
    }

    fun setActiveApiKey(key: String) {
        viewModelScope.launch {
            preferenceManager.setActiveApiKey(key)
        }
    }

    fun setModelId(id: String) {
        viewModelScope.launch {
            preferenceManager.setModelId(id)
        }
    }

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentProjectId = null)
            try {
                // Generate a unique filename to store permanently
                val fileName = "doc_${System.currentTimeMillis()}.pdf"
                val file = pdfRepository.copyPdfToInternalStorage(uri, fileName)
                _uiState.value = _uiState.value.copy(pdfFile = file)
                
                // Trigger Gemini
                retryGemini()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }
    
    fun retryGemini() {
        viewModelScope.launch {
            val file = _uiState.value.pdfFile
            val apiKey = _uiState.value.apiKey
            val modelId = _uiState.value.activeModelId

            if (file != null && apiKey != null) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                generateMarkdown(file, apiKey, modelId)
            } else if (apiKey == null) {
                _uiState.value = _uiState.value.copy(error = "API Key missing")
            }
        }
    }

    private suspend fun generateMarkdown(pdfFile: File, apiKey: String, modelId: String) {
        try {
            val bytes = pdfFile.readBytes()
            val markdown = geminiRepository.generateMarkdownFromPdf(apiKey, modelId, bytes)
            _uiState.value = _uiState.value.copy(markdownContent = markdown, isLoading = false)
            // Save project update if exists
            val currentId = _uiState.value.currentProjectId
            if (currentId != null) {
                // We should update the existing project if it's already saved
                 val currentProject = projectRepository.getProject(currentId)
                 if (currentProject != null) {
                     projectRepository.saveProject(currentProject.copy(markdownContent = markdown, lastModified = System.currentTimeMillis()))
                 }
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
        }
    }

    fun updateMarkdown(content: String) {
        _uiState.value = _uiState.value.copy(markdownContent = content)
        // Auto-save logic if it's an existing project
        viewModelScope.launch {
            val currentId = _uiState.value.currentProjectId
            if (currentId != null) {
                val currentProject = projectRepository.getProject(currentId)
                if (currentProject != null) {
                    projectRepository.saveProject(currentProject.copy(markdownContent = content, lastModified = System.currentTimeMillis()))
                }
            }
        }
    }
    
    fun saveCurrentProject(name: String = "Untitled Project") {
         viewModelScope.launch {
             try {
                 val pdfFile = _uiState.value.pdfFile ?: return@launch
                 val content = _uiState.value.markdownContent

                 // If updating existing
                 val currentId = _uiState.value.currentProjectId
                 if (currentId != null) {
                     val existing = projectRepository.getProject(currentId)
                     if (existing != null) {
                         projectRepository.saveProject(existing.copy(markdownContent = content, lastModified = System.currentTimeMillis()))
                         _uiState.value = _uiState.value.copy(error = "Saved")
                         return@launch
                     }
                 }

                 // Creating new
                 val project = ProjectEntity(
                     name = name,
                     pdfUriString = pdfFile.absolutePath,
                     markdownContent = content
                 )
                 val id = projectRepository.saveProject(project)
                 _uiState.value = _uiState.value.copy(currentProjectId = id, error = "Project Saved")
             } catch (e: Exception) {
                 _uiState.value = _uiState.value.copy(error = "Failed to save project: ${e.message}")
             }
         }
    }

    fun loadProject(project: ProjectEntity) {
        viewModelScope.launch {
             try {
                 val pdfFile = File(project.pdfUriString)
                 if (pdfFile.exists()) {
                     _uiState.value = _uiState.value.copy(
                         pdfFile = pdfFile,
                         markdownContent = project.markdownContent,
                         currentProjectId = project.id,
                         isLoading = false
                     )
                 } else {
                     _uiState.value = _uiState.value.copy(error = "PDF File not found")
                 }
             } catch (e: Exception) {
                 _uiState.value = _uiState.value.copy(error = "Error loading project")
             }
        }
    }

    fun closeProject() {
        _uiState.value = _uiState.value.copy(pdfFile = null, markdownContent = "", currentProjectId = null)
    }

    fun loadSession(uris: List<Uri>) {
        // Legacy: kept for compatibility if needed, but we prefer Projects now
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentProjectId = null)
            try {
                var pdfFound = false
                var mdFound = false
                
                uris.forEach { uri ->
                    val path = uri.path?.lowercase() ?: ""
                    val mimeType = pdfRepository.getMimeType(uri)
                    if (mimeType?.contains("pdf") == true || path.endsWith(".pdf")) {
                         val file = pdfRepository.copyPdfToInternalStorage(uri, "session_doc.pdf")
                         _uiState.value = _uiState.value.copy(pdfFile = file)
                         pdfFound = true
                    } else if (mimeType?.startsWith("text") == true || path.endsWith(".txt") || path.endsWith(".md")) {
                         val file = pdfRepository.copyFileToInternalStorage(uri, "session_doc.md")
                         val content = file.readText()
                         _uiState.value = _uiState.value.copy(markdownContent = content)
                         mdFound = true
                    }
                }
                
                if (!pdfFound && !mdFound) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Could not identify PDF or Text file")
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }
    
    fun deleteProject(id: Long) {
        viewModelScope.launch {
            try {
                // Optional: Delete associated PDF file if we want to clean up storage
                // For now, let's just delete the record
                projectRepository.deleteProject(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete project")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

class MainViewModelFactory(
    private val preferenceManager: PreferenceManager,
    private val geminiRepository: GeminiRepository,
    private val pdfRepository: PdfRepository,
    private val projectRepository: ProjectRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(preferenceManager, geminiRepository, pdfRepository, projectRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
