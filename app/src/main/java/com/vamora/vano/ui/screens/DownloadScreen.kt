package com.vamora.vano.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamora.vano.R
import com.vamora.vano.data.ModelId
import com.vamora.vano.data.VanoViewModel
import com.vamora.vano.ui.theme.VanoGreen

@Composable
fun DownloadScreen(
    viewModel: VanoViewModel,
    onContinue: () -> Unit
) {
    val available by viewModel.modelManager.available.collectAsState()
    val downloadStates by viewModel.modelManager.downloadStates.collectAsState()
    val selected by viewModel.selectedModel.collectAsState()

    val hasModel = available.isNotEmpty()

    // Force dark surface like rest of app – fixes light windowBackground showing through
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(Modifier.height(24.dp))
        // Logo
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.size(84.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Use drawable vano_logo
                Image(
                    painter = painterResource(id = R.drawable.vano_logo),
                    contentDescription = "Vano logo",
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Welcome to Vano", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Choose a model to download. You can change it later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(20.dp))

        ModelId.entries.forEach { model ->
            val state = downloadStates[model]
            val isDownloading = state?.isDownloading == true
            val progress = state?.progress ?: 0f
            val downloaded = model.let { viewModel.modelManager.isDownloaded(it) }
            val isSelected = selected == model.fileName

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, VanoGreen) else null
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                if (downloaded) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VanoGreen, modifier = Modifier.size(18.dp))
                                }
                            }
                            Text(model.sizeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (downloaded) {
                                viewModel.modelManager.getFileSizeLabel(model)?.let {
                                    Text("Downloaded • $it", style = MaterialTheme.typography.labelSmall, color = VanoGreen)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (isDownloading) {
                        Column {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = VanoGreen,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val ds = state
                                Text(
                                    if (ds != null && ds.totalBytes > 0) {
                                        val mbDown = ds.downloadedBytes / (1024f*1024f)
                                        val mbTotal = ds.totalBytes / (1024f*1024f)
                                        String.format("%.1f / %.1f MB • %.0f%%", mbDown, mbTotal, progress*100)
                                    } else "${(progress*100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                TextButton(onClick = { viewModel.cancelDownload(model) }) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cancel")
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (downloaded) {
                                Button(
                                    onClick = { viewModel.setSelectedModel(model) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) VanoGreen else MaterialTheme.colorScheme.surface,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isSelected) "Selected" else "Select")
                                }
                                OutlinedButton(onClick = { viewModel.deleteModel(model) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.download(model) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = VanoGreen, contentColor = MaterialTheme.colorScheme.background)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download")
                                }
                            }
                        }
                        state?.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onContinue,
            enabled = hasModel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VanoGreen, contentColor = MaterialTheme.colorScheme.background, disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(if (hasModel) "Continue to chat" else "Download a model to continue", fontWeight = FontWeight.SemiBold)
        }
        if (!hasModel) {
            Text("At least one model is required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(32.dp))
        }
    }
}
