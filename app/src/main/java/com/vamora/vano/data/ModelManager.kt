package com.vamora.vano.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f, // 0..1
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
)

class ModelManager(private val context: Context) {
    private val modelsDir: File get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    private val _downloadStates = MutableStateFlow<Map<ModelId, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<ModelId, DownloadState>> = _downloadStates

    private val _available = MutableStateFlow<Set<ModelId>>(emptySet())
    val available: StateFlow<Set<ModelId>> = _available

    @Volatile private var cancelFlag = false

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val set = ModelId.entries.filter { File(modelsDir, it.fileName).exists() }.toSet()
        _available.value = set
    }

    fun getModelFile(id: ModelId): File = File(modelsDir, id.fileName)

    fun isDownloaded(id: ModelId): Boolean = getModelFile(id).exists()

    fun getFileSizeLabel(id: ModelId): String? {
        val f = getModelFile(id)
        return if (f.exists()) formatBytes(f.length()) else null
    }

    suspend fun download(id: ModelId) = withContext(Dispatchers.IO) {
        cancelFlag = false
        _downloadStates.value = _downloadStates.value.toMutableMap().apply { this[id] = DownloadState(isDownloading = true, progress = 0f) }
        val dest = getModelFile(id)
        val tmp = File(modelsDir, id.fileName + ".tmp")
        try {
            val url = URL(id.url)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            conn.connect()
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var downloaded: Long = 0
                    while (input.read(buffer).also { read = it } != -1) {
                        if (cancelFlag) throw Exception("Cancelled")
                        output.write(buffer, 0, read)
                        downloaded += read
                        val prog = if (total > 0) downloaded.toFloat() / total else 0f
                        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                            this[id] = DownloadState(isDownloading = true, progress = prog, downloadedBytes = downloaded, totalBytes = total)
                        }
                    }
                }
            }
            if (tmp.exists()) tmp.renameTo(dest)
            _downloadStates.value = _downloadStates.value.toMutableMap().apply { this[id] = DownloadState(isDownloading = false, progress = 1f) }
            refresh()
        } catch (e: Exception) {
            tmp.delete()
            _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                this[id] = DownloadState(isDownloading = false, error = e.message ?: "Download failed")
            }
        }
    }

    fun cancel(id: ModelId) {
        cancelFlag = true
        val tmp = File(modelsDir, id.fileName + ".tmp")
        tmp.delete()
        _downloadStates.value = _downloadStates.value.toMutableMap().apply { this[id] = DownloadState(isDownloading = false) }
    }

    suspend fun delete(id: ModelId) = withContext(Dispatchers.IO) {
        getModelFile(id).delete()
        File(modelsDir, id.fileName + ".tmp").delete()
        _downloadStates.value = _downloadStates.value.toMutableMap().apply { this[id] = DownloadState(isDownloading = false) }
        refresh()
    }

    suspend fun deleteAllAndClearData(chatRepository: ChatRepository, preferencesManager: PreferencesManager) = withContext(Dispatchers.IO) {
        ModelId.entries.forEach { delete(it) }
        chatRepository.clearAll()
        preferencesManager.clearAll()
        // delete models dir leftover
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()
        refresh()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}
