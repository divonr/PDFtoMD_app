package com.divonr.pdftomd.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divonr.pdftomd.data.GeminiRepository
import com.divonr.pdftomd.data.PdfRepository
import com.divonr.pdftomd.data.PreferenceManager
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
    val error: String? = null
)

class MainViewModel(
    private val preferenceManager: PreferenceManager,
    private val geminiRepository: GeminiRepository,
    private val pdfRepository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Combine flows to update state
            combine(
                preferenceManager.apiKey,
                preferenceManager.savedApiKeys,
                preferenceManager.modelId
            ) { apiKey, savedKeys, modelId ->
                Triple(apiKey, savedKeys, modelId)
            }.collect { (apiKey, savedKeys, modelId) ->
                _uiState.value = _uiState.value.copy(
                    apiKey = apiKey,
                    savedApiKeys = savedKeys,
                    activeModelId = modelId
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Copy to internal storage so we can always access it
                val file = pdfRepository.copyPdfToInternalStorage(uri, "current_doc.pdf")
                _uiState.value = _uiState.value.copy(pdfFile = file)
                
                // Trigger Gemini
                val apiKey = _uiState.value.apiKey
                val modelId = _uiState.value.activeModelId
                if (apiKey != null) {
                    generateMarkdown(file, apiKey, modelId)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "API Key missing")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }
    
    // For loading existing session (if we implement session restoration fully)
    fun loadExistingSession(pdfFile: File, markdownFile: File) {
         viewModelScope.launch {
             try {
                 val content = markdownFile.readText()
                 _uiState.value = _uiState.value.copy(pdfFile = pdfFile, markdownContent = content)
             } catch (e: Exception) {
                 _uiState.value = _uiState.value.copy(error = "Failed to restore session")
             }
         }
    }

    private suspend fun generateMarkdown(pdfFile: File, apiKey: String, modelId: String) {
        try {
            val bytes = pdfFile.readBytes()
            val markdown = geminiRepository.generateMarkdownFromPdf(apiKey, modelId, bytes)
            _uiState.value = _uiState.value.copy(markdownContent = markdown, isLoading = false)
            // Auto-save
            saveMarkdown(markdown)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
        }
    }

    fun updateMarkdown(content: String) {
        _uiState.value = _uiState.value.copy(markdownContent = content)
        // Debounce save in real app, here simple save
        viewModelScope.launch {
             pdfRepository.saveTextToFile(content, "current_doc.md")
        }
    }
    
    fun saveMarkdown(content: String) {
        viewModelScope.launch {
            pdfRepository.saveTextToFile(content, "current_doc.md")
        }
    }

    fun loadSession(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                var pdfFound = false
                var mdFound = false
                
                uris.forEach { uri ->
                    val path = uri.path?.lowercase() ?: ""
                    // Simple heuristic or content resolver type check could be better
                    // But assume order or try to detect content? 
                    // Let's assume user picks PDF and Text. 
                    // Best way: Check MimeType
                    val mimeType = pdfRepository.getMimeType(uri) // We need helper or just use string check
                    if (mimeType?.contains("pdf") == true || path.endsWith(".pdf")) {
                         val file = pdfRepository.copyPdfToInternalStorage(uri, "current_doc.pdf")
                         _uiState.value = _uiState.value.copy(pdfFile = file)
                         pdfFound = true
                    } else if (mimeType?.startsWith("text") == true || path.endsWith(".txt") || path.endsWith(".md")) {
                         val file = pdfRepository.copyFileToInternalStorage(uri, "current_doc.md")
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
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

class MainViewModelFactory(
    private val preferenceManager: PreferenceManager,
    private val geminiRepository: GeminiRepository,
    private val pdfRepository: PdfRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(preferenceManager, geminiRepository, pdfRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
