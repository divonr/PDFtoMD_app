package com.divonr.pdftomd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // --- API Keys Section ---
            Text(
                text = "API Keys",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(uiState.savedApiKeys.toList().sorted()) { key ->
                    ApiKeyItem(
                        apiKey = key,
                        isActive = key == uiState.apiKey,
                        onSelect = { viewModel.setActiveApiKey(key) }
                    )
                    HorizontalDivider()
                }
                item {
                    TextButton(
                        onClick = { showAddKeyDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add New API Key")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // --- Model ID Section ---
            Text(
                text = "Model ID",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val models = listOf("gemini-2.5-flash", "gemini-3-flash-preview")
            val isCustomModel = uiState.activeModelId !in models
            var customModelText by remember(uiState.activeModelId) { mutableStateOf(if (isCustomModel) uiState.activeModelId else "") }

            Column {
                models.forEach { model ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (model == uiState.activeModelId),
                                onClick = { viewModel.setModelId(model) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (model == uiState.activeModelId),
                            onClick = { viewModel.setModelId(model) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = model)
                    }
                }

                // Custom Model Option
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isCustomModel,
                            onClick = {
                                // Do nothing until text typed, or focus
                            }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCustomModel,
                        onClick = {
                             if (customModelText.isNotEmpty()) viewModel.setModelId(customModelText)
                             // else keep current or focus?
                             // We'll let the text field drive the state if custom is selected conceptually
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Custom:")
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = customModelText,
                        onValueChange = {
                            customModelText = it
                            if (it.isNotEmpty()) viewModel.setModelId(it)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Enter Model ID") }
                    )
                }
            }
        }
    }

    if (showAddKeyDialog) {
        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            title = { Text("Add API Key") },
            text = {
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("API Key") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKey.isNotBlank()) {
                            viewModel.saveApiKey(newKey)
                            newKey = ""
                            showAddKeyDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ApiKeyItem(
    apiKey: String,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isActive) { onSelect() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = maskApiKey(apiKey),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isActive) "Active" else "Disabled",
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Active",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
             Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = "Disabled",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun maskApiKey(key: String): String {
    if (key.length <= 8) return "****"
    return "${key.take(4)}...${key.takeLast(4)}"
}
