package com.divonr.pdftomd.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.divonr.pdftomd.ui.MainViewModel
import java.io.File

@Composable
fun AppScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    if (uiState.error != null) {
        Toast.makeText(context, uiState.error, Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    if (uiState.apiKey.isNullOrEmpty()) {
        ApiKeyScreen(onSave = { viewModel.saveApiKey(it) })
    } else {
        if (uiState.pdfFile == null) {
            UploadScreen(
                onPdfSelected = { viewModel.loadPdf(it) },
                onLoadSession = { viewModel.loadSession(it) }
            )
        } else {
            SplitScreen(
                pdfFile = uiState.pdfFile!!,
                markdownContent = uiState.markdownContent,
                onMarkdownChange = { viewModel.updateMarkdown(it) },
                isLoading = uiState.isLoading
            )
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

// UploadScreen Update
@Composable
fun UploadScreen(onPdfSelected: (Uri) -> Unit, onLoadSession: (List<Uri>) -> Unit) {
    val pdfLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPdfSelected(uri)
    }
    
    val sessionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onLoadSession(uris)
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { pdfLauncher.launch("application/pdf") }) {
                Text("Upload PDF to Start")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { sessionLauncher.launch(arrayOf("application/pdf", "text/plain")) }) {
                Text("Load Session (PDF + TXT)")
            }
        }
    }
}

@Composable
fun SplitScreen(
    pdfFile: File,
    markdownContent: String,
    onMarkdownChange: (String) -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar / Actions
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
             Button(onClick = {
                 // Manual Save trigger (already auto-saving but good validation)
                 Toast.makeText(context, "Saved.", Toast.LENGTH_SHORT).show()
             }) {
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
    
        // Top Half: PDF (Weight 1f)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PdfViewer(
                pdfFile = pdfFile,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Bottom Half: Editor (Weight 1f)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                TextField(
                    value = markdownContent,
                    onValueChange = onMarkdownChange,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { Text("Markdown will appear here...") }
                )
            }
        }
    }
}
