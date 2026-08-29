package com.vamora.vano.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SmartToy
import dev.jeziellago.compose.markdowntext.MarkdownText
import com.vamora.vano.ui.theme.VanoGreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamora.vano.data.VanoViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    viewModel: VanoViewModel,
    onBack: () -> Unit
) {
    val chats by viewModel.chatRepository.chats.collectAsState()
    val chat = chats.find { it.id == chatId }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val selected by viewModel.selectedModel.collectAsState()
    val context = LocalContext.current
    var attachedName by remember { mutableStateOf<String?>(null) }
    var attachedText by remember { mutableStateOf<String?>(null) }
    var isReadingFile by remember { mutableStateOf(false) }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // Block photos – Vano is text-only (no vision)
            val type = context.contentResolver.getType(uri) ?: ""
            var displayNameTmp = uri.lastPathSegment?.substringAfterLast("/") ?: "file"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) displayNameTmp = cursor.getString(idx) ?: displayNameTmp
            }
            val lower = displayNameTmp.lowercase()
            val isPhoto = type.startsWith("image/") || type.startsWith("video/") ||
                    lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".gif")
            if (isPhoto) {
                Toast.makeText(context, "Photos not supported — Vano is text-only. Pick a .txt/.md/.pdf/.json file.", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            isReadingFile = true
            // read in coroutine
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    val name = displayNameTmp
                    val resolver = context.contentResolver
                    var displayName = name
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cursor.moveToFirst()) displayName = cursor.getString(idx) ?: name
                    }
                    resolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        // Limit to ~15k chars to fit 8192 context
                        val text = try {
                            String(bytes, Charsets.UTF_8).take(15000)
                        } catch (_: Exception) { "[Binary file, ${bytes.size} bytes – cannot display as text]" }
                        val finalText = if (bytes.size > 15000) text + "\n[Truncated]" else text
                        withContext(Dispatchers.Main) {
                            attachedName = displayName
                            attachedText = finalText
                            isReadingFile = false
                            Toast.makeText(context, "Attached $displayName", Toast.LENGTH_SHORT).show()
                        }
                    } ?: withContext(Dispatchers.Main) { isReadingFile = false }
                } catch (e: Exception) {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        isReadingFile = false
                    }
                }
            }
        }
    }

    LaunchedEffect(chat?.messages?.size) {
        if ((chat?.messages?.size ?: 0) > 0) {
            scope.launch { listState.animateScrollToItem(chat!!.messages.size - 1) }
        }
    }

    if (chat == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Chat not found") }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(chat.title, fontWeight = FontWeight.SemiBold, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (isGenerating) "Vano is thinking…" else (selected?.let { it } ?: "No model"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (chat.messages.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                                Text("Start the conversation", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Text("Ask anything — Vano is running locally on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(chat.messages) { msg ->
                        val isUser = msg.role == "user"
                        val isStreamingPlaceholder = msg.content == "▌" && !isUser
                        val context = LocalContext.current
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 340.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp)
                                ) {
                                    if (isStreamingPlaceholder) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    } else {
                                        Column {
                                            if (msg.attachmentName != null) {
                                                Surface(shape = RoundedCornerShape(8.dp), color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface) {
                                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(msg.attachmentName, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                                    }
                                                }
                                                if (msg.content.isNotBlank()) Spacer(Modifier.height(6.dp))
                                            }
                                            if (isUser) {
                                                Text(
                                                    msg.content.ifBlank { if (msg.attachmentName != null) "[File attached]" else "" },
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            } else {
                                                MarkdownText(
                                                    markdown = msg.content.ifBlank { "[File attached]" },
                                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    linkColor = VanoGreen,
                                                    isTextSelectable = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // Show attached file chip below bubble if present (for user)
                            if (msg.attachmentName != null && !isStreamingPlaceholder) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Text(
                                        "${msg.attachmentName} • ${msg.attachmentText?.length ?: 0} chars",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!isStreamingPlaceholder) {
                                Spacer(Modifier.height(4.dp))
                                // Model badge + stats like screenshot
                                if (!isUser) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (msg.modelName != null) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                tonalElevation = 0.dp,
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Row(
                                                    Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Spacer(Modifier.width(4.dp))
                                                    // split "vano Q4_0" -> vano + chip
                                                    val parts = msg.modelName.split(" ")
                                                    Text(parts.firstOrNull() ?: msg.modelName, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    if (parts.size > 1) {
                                                        Spacer(Modifier.width(4.dp))
                                                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) {
                                                            Text(parts.drop(1).joinToString(" "), Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (msg.tokens != null) {
                                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(3.dp))
                                            Text("${msg.tokens} tokens", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        if (msg.genTimeMs != null) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(3.dp))
                                            Text(String.format("%.1fs", msg.genTimeMs / 1000f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        if (msg.tokensPerSec != null) {
                                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(3.dp))
                                            Text(String.format("%.2f t/s", msg.tokensPerSec), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                // Copy / Delete row – like screenshot icons under message
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Vano", msg.content))
                                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteMessage(chatId, msg.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (isGenerating) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Generating…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            // Attached file preview (8192 context)
            if (attachedName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(attachedName!!, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), maxLines = 1)
                                Text("${attachedText?.length ?: 0} chars • 8192 ctx", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { attachedName = null; attachedText = null }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (isReadingFile) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Reading file...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = { pickFileLauncher.launch("*/*") },
                    enabled = !isGenerating && !isReadingFile,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach file", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(4.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (isGenerating) "Wait for response…" else if (attachedName != null) "Ask about file..." else "Message...") },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 4,
                    enabled = !isGenerating,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val hasText = input.isNotBlank()
                        val hasFile = attachedName != null && attachedText != null
                        if ((hasText || hasFile) && !isGenerating) {
                            viewModel.sendMessage(chatId, input.trim(), attachedName, attachedText)
                            input = ""
                            attachedName = null
                            attachedText = null
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = !isGenerating && (input.isNotBlank() || attachedName != null),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
