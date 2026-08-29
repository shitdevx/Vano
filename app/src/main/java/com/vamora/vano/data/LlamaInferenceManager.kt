package com.vamora.vano.data

import android.util.Log
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device inference using prebuilt llama.cpp AAR (dev.ffmpegkit-maintained:llama-android).
 * Falls back to null if native load fails (e.g. OOM, unsupported ABI).
 */
class LlamaInferenceManager {
    private var model: LlamaModel? = null
    private var loadedPath: String? = null

    @Volatile private var isLoading = false

    suspend fun ensureLoaded(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) return@withContext false
        if (model != null && loadedPath == modelFile.absolutePath) return@withContext true
        if (isLoading) return@withContext false
        isLoading = true
        try {
            // Release previous
            model?.let { try { Llama.releaseModel(it) } catch (_: Exception) {} }
            model = null
            loadedPath = null

            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            // User requested 8192 for file/text analysis – needs ~extra RAM but fits Vano 1.55 GB on 6GB+ devices (OPPO Find X8s+ has 12/16 GB)
            val config = LlamaConfig(
                contextSize = 8192,
                threads = threads,
                gpuLayers = 0, // CPU only – GPU not reliable across devices
                temperature = 0.1f,
                topP = 0.9f,
                topK = 40,
                seed = -1
            )
            Log.i("Llama", "Loading ${modelFile.name} threads=$threads")
            val loaded = Llama.loadModel(modelFile.absolutePath, config)
            model = loaded
            loadedPath = modelFile.absolutePath
            Log.i("Llama", "Loaded ok: ${modelFile.name}")
            true
        } catch (e: Exception) {
            Log.e("Llama", "Load failed: ${e.message}", e)
            model = null
            loadedPath = null
            false
        } finally {
            isLoading = false
        }
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        model?.let { try { Llama.releaseModel(it) } catch (_: Exception) {} }
        model = null
        loadedPath = null
    }

    fun isReady(): Boolean = model != null && model?.isLoaded == true

    data class GenerationResult(
        val text: String,
        val tokens: Int,
        val genTimeMs: Long,
        val tokensPerSec: Float,
        val promptTimeMs: Long
    )

    /**
     * Generate reply for new user prompt given full history.
     * Returns generated text + stats or throws.
     */
    suspend fun generate(history: List<ChatMessage>, newPrompt: String): GenerationResult = withContext(Dispatchers.Default) {
        val m = model ?: throw IllegalStateException("Model not loaded")
        val prompt = buildPrompt(history, newPrompt)
        // Don’t use <|user|> / <|assistant|> – that broke the Vano Q4_0 template and made it echo "Can you tell me more about Vano?"
        // Let llama-android apply the GGUF’s own chat template (Jinja) – we pass plain User/Assistant turns + system separately.
        val system = "You are Vano, a helpful AI assistant created by the Vamora Project. Be friendly, concise, and helpful. If the user says hi/hello, greet them warmly and ask how you can help — don't ask them to tell you about Vano."
        Log.i("Llama", "Inference prompt len=${prompt.length}")
        // User settings: Max tokens 150-250 → use 200, Top-p 0.9 Top-k 40 already in config
        // Note: repeat 1.5 / presence 0.5 / frequency 0.5 not exposed by llama-android 0.1.1 LlamaConfig – kept at llama.cpp defaults (repeat ~1.1). Closest achievable with temp 0.1 + topP 0.9.
        val result = Llama.complete(m, prompt = prompt, systemPrompt = system, maxTokens = 200)
        val text = result.text.trim().ifEmpty { throw IllegalStateException("Empty generation") }
        GenerationResult(
            text = text,
            tokens = result.tokensGenerated,
            genTimeMs = result.generateTimeMs,
            tokensPerSec = result.tokensPerSecond,
            promptTimeMs = result.promptEvalTimeMs
        )
    }

    // Backward compat for callers expecting String
    suspend fun generateText(history: List<ChatMessage>, newPrompt: String): String = generate(history, newPrompt).text

    /**
     * Streaming version – ffmpegkit doesn't stream natively, so we simulate by
     * returning chunks. If you want true token streaming, switch to ljcamargo LlamaAndroid.
     */
    suspend fun generateStreaming(history: List<ChatMessage>, newPrompt: String, onToken: (String) -> Unit): String {
        val full = generate(history, newPrompt).text
        val words = full.split(' ')
        val sb = StringBuilder()
        for (w in words) {
            sb.append(w).append(' ')
            onToken(sb.toString())
            kotlinx.coroutines.delay(30)
        }
        return full
    }

    private fun buildPrompt(history: List<ChatMessage>, newPrompt: String): String {
        // Plain User/Assistant turns – lets llama-android wrap with the GGUF’s Jinja template.
        // Includes attached file content (truncated to fit 8192 context). User requested 8192 for file analysis.
        fun formatMsg(m: ChatMessage): String {
            val base = m.content.trim()
            val att = if (!m.attachmentText.isNullOrBlank()) {
                val txt = m.attachmentText.take(12000) // ~3k tokens, fits 8192
                "\n[Attached file: ${m.attachmentName ?: "file"}]\n$txt\n[End of file]"
            } else ""
            return base + att
        }
        val recent = history.takeLast(12)
        val sb = StringBuilder()
        for (msg in recent) {
            when (msg.role) {
                "user" -> sb.append("User: ").append(formatMsg(msg)).append("\n")
                "assistant" -> sb.append("Assistant: ").append(msg.content.trim()).append("\n")
            }
        }
        // newPrompt is already the current user message content; attachment is handled via history placeholder.
        // For file-only case, caller passes history without placeholder, so append newPrompt separately.
        // Detect if newPrompt already equals last history's content to avoid duplication – caller passes filtered history.
        sb.append("User: ").append(newPrompt.trim()).append("\nAssistant: ")
        return sb.toString()
    }

    // Helper to build prompt with explicit file attachment for current turn
    fun buildPromptWithFile(history: List<ChatMessage>, newPrompt: String, fileName: String?, fileText: String?): String {
        val fileBlock = if (!fileText.isNullOrBlank()) "\n[Attached file: ${fileName ?: "file"}]\n${fileText.take(12000)}\n[End of file]" else ""
        return buildPrompt(history, newPrompt.trim() + fileBlock)
    }
}
