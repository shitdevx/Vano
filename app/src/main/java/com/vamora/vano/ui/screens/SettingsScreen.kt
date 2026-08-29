package com.vamora.vano.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamora.vano.data.ModelId
import com.vamora.vano.data.VanoViewModel

@Composable
fun SettingsScreen(
    viewModel: VanoViewModel,
    onBack: () -> Unit
) {
    val selected by viewModel.selectedModel.collectAsState()
    val available by viewModel.modelManager.available.collectAsState()
    val downloadStates by viewModel.modelManager.downloadStates.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { "1.0" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Model selection", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Text("Choose which downloaded model is used for chat. Green = selected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                ModelId.entries.forEach { model ->
                    val isAvail = available.contains(model)
                    val state = downloadStates[model]
                    val isDownloading = state?.isDownloading == true
                    val isSelected = selected == model.fileName
                    ListItem(
                        headlineContent = { Text(model.displayName) },
                        supportingContent = {
                            Column {
                                Text(model.sizeLabel + " • " + model.description, style = MaterialTheme.typography.labelSmall)
                                if (isAvail) Text("Downloaded", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                else if (isDownloading) LinearProgressIndicator(progress = { state?.progress ?: 0f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                        },
                        trailingContent = {
                            when {
                                isDownloading -> Text("${((state?.progress ?: 0f)*100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                                isAvail -> RadioButton(selected = isSelected, onClick = { viewModel.setSelectedModel(model) })
                                else -> IconButton(onClick = { viewModel.download(model) }) { Icon(Icons.Default.Download, contentDescription = "Download") }
                            }
                        },
                        leadingContent = {
                            if (isAvail && isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Danger zone", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Remove all downloaded models and conversations. This cannot be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove all models and data")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        // App information at bottom
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Vano", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Private, offline AI chat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                InfoRow("Version", versionName ?: "1.0")
                InfoRow("Package", "com.vamora.vano")
                InfoRow("Models", "github.com/TheVamoraProject/Vano")
                Spacer(Modifier.height(8.dp))
                Text("© 2026 Vamora Project • Built with green accent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Remove all data?") },
            text = { Text("This will delete all models (GGUF files) and all chat history. You will need to download a model again.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.deleteAllData()
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}
