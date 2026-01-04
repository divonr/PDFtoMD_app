package com.divonr.pdftomd.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.divonr.pdftomd.ui.MainViewModel
import java.io.File

@Composable
fun AppScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showProjectList by remember { mutableStateOf(false) }

    if (uiState.error != null) {
        Toast.makeText(context, uiState.error, Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    if (showSettings) {
        SettingsScreen(viewModel = viewModel, onBack = { showSettings = false })
    } else if (showProjectList) {
        ProjectListScreen(
            projects = uiState.projects,
            onProjectClick = {
                viewModel.loadProject(it)
                showProjectList = false
            },
            onDeleteProject = { viewModel.deleteProject(it) },
            onBack = { showProjectList = false }
        )
    } else {
        if (uiState.apiKey.isNullOrEmpty()) {
             ApiKeyScreen(onSave = { viewModel.saveApiKey(it) })
        } else {
            if (uiState.pdfFile == null) {
                UploadScreen(
                    onPdfSelected = { viewModel.loadPdf(it) },
                    onLoadSession = { viewModel.loadSession(it) },
                    onManageProjectsClick = { showProjectList = true },
                    onSettingsClick = { showSettings = true }
                )
            } else {
                SplitScreen(
                    pdfFile = uiState.pdfFile!!,
                    markdownContent = uiState.markdownContent,
                    onMarkdownChange = { viewModel.updateMarkdown(it) },
                    isLoading = uiState.isLoading,
                    onSettingsClick = { showSettings = true },
                    onBackClick = { viewModel.closeProject() },
                    onRequeryClick = { viewModel.retryGemini() },
                    onSaveClick = {
                        viewModel.saveCurrentProject("Project ${System.currentTimeMillis()}")
                    }
                )
            }
        }
    }
}

@Composable
fun ApiKeyScreen(onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Enter Google Gemini API Key", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API Key") }, singleLine = true)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { if (key.isNotBlank()) onSave(key) }) {
                Text("Save & Continue")
            }
        }
    }
}

@Composable
fun UploadScreen(
    onPdfSelected: (Uri) -> Unit,
    onLoadSession: (List<Uri>) -> Unit,
    onManageProjectsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val pdfLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPdfSelected(uri)
    }
    
    val sessionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onLoadSession(uris)
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Button(onClick = { pdfLauncher.launch("application/pdf") }) {
                Text("Upload PDF to Start")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onManageProjectsClick) {
                 Text("Manage Projects")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { sessionLauncher.launch(arrayOf("application/pdf", "text/plain")) }) {
                Text("Load Legacy Session")
            }
        }
    }
}

@Composable
fun SplitScreen(
    pdfFile: File,
    markdownContent: String,
    onMarkdownChange: (String) -> Unit,
    isLoading: Boolean,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit,
    onRequeryClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = markdownContent))
    }

    LaunchedEffect(markdownContent) {
        if (textFieldValue.text != markdownContent) {
             textFieldValue = textFieldValue.copy(text = markdownContent)
        }
    }

    // Scroll state for the text editor container
    val scrollState = rememberScrollState()

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val selection = textFieldValue.selection

    val popupPosition by remember(selection, textLayoutResult, scrollState.value) {
        derivedStateOf {
            if (selection.collapsed || textLayoutResult == null) return@derivedStateOf null

            val layout = textLayoutResult!!
            val startOffset = selection.min.coerceIn(0, layout.layoutInput.text.length)

            try {
                val boundingBox = layout.getBoundingBox(startOffset)
                // Calculate position relative to the scrollable container's visible area
                val y = boundingBox.top.toInt() - scrollState.value
                IntOffset(boundingBox.left.toInt(), y)
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
             Row {
                 IconButton(onClick = onBackClick) {
                     Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                 }
                 IconButton(onClick = onSettingsClick) {
                     Icon(Icons.Default.Settings, contentDescription = "Settings")
                 }
                 IconButton(onClick = onRequeryClick) {
                     Icon(Icons.Default.Refresh, contentDescription = "Re-process PDF")
                 }
             }

             Row {
                 Button(onClick = onSaveClick) {
                     Text("Save")
                 }
                 Spacer(modifier = Modifier.width(8.dp))
                 Button(onClick = {
                      val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, markdownContent)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Export Markdown")
                    context.startActivity(shareIntent)
                 }) {
                     Text("Export")
                 }
             }
        }
    
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PdfViewer(
                pdfFile = pdfFile,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Suppress system text toolbar
                    val emptyToolbar = remember { EmptyTextToolbar() }
                    CompositionLocalProvider(LocalTextToolbar provides emptyToolbar) {
                         Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(16.dp)
                        ) {
                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = { newValue ->
                                    textFieldValue = newValue
                                    if (newValue.text != markdownContent) {
                                        onMarkdownChange(newValue.text)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                onTextLayout = { textLayoutResult = it },
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            text = "Markdown will appear here...",
                                            style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    if (!selection.collapsed && popupPosition != null) {
                        Popup(
                            offset = popupPosition!!,
                            alignment = Alignment.TopStart,
                            properties = PopupProperties(focusable = false)
                        ) {
                            val yOffset = (-50).dp + 16.dp
                            val xOffset = 16.dp

                            Box(modifier = Modifier.offset(x = xOffset, y = yOffset)) {
                                FloatingTextToolbar(
                                    onBoldClick = {
                                        val newVal = MarkdownUtils.applyBold(textFieldValue)
                                        textFieldValue = newVal
                                        onMarkdownChange(newVal.text)
                                    },
                                    onItalicClick = {
                                        val newVal = MarkdownUtils.applyItalic(textFieldValue)
                                        textFieldValue = newVal
                                        onMarkdownChange(newVal.text)
                                    },
                                    onQuoteClick = {
                                        val newVal = MarkdownUtils.toggleQuote(textFieldValue)
                                        textFieldValue = newVal
                                        onMarkdownChange(newVal.text)
                                    },
                                    onCopyClick = {
                                        val selectedText = textFieldValue.text.substring(selection.min, selection.max)
                                        clipboardManager.setText(AnnotatedString(selectedText))
                                        // Clear selection? Typically copy keeps selection,
                                        // but floating toolbar logic usually dismisses after action.
                                        // Let's dismiss to be consistent with other buttons.
                                        textFieldValue = textFieldValue.copy(selection = TextRange(selection.max))
                                    },
                                    onPasteClick = {
                                        val clipboardText = clipboardManager.getText()?.text
                                        if (clipboardText != null) {
                                            val text = textFieldValue.text
                                            val start = selection.min
                                            val end = selection.max
                                            val newText = text.replaceRange(start, end, clipboardText)
                                            val newCursor = start + clipboardText.length

                                            textFieldValue = TextFieldValue(
                                                text = newText,
                                                selection = TextRange(newCursor)
                                            )
                                            onMarkdownChange(newText)
                                        }
                                    },
                                    onDismiss = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom Toolbar to disable system menu
class EmptyTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() {}
    override fun showMenu(
        rect: Rect,
        onCopy: (() -> Unit)?,
        onPaste: (() -> Unit)?,
        onCut: (() -> Unit)?,
        onSelectAll: (() -> Unit)?
    ) {
        // Do nothing to suppress system menu
    }
}
