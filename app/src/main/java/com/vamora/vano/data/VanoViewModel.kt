package com.vamora.vano.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VanoViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    val preferences = PreferencesManager(context)
    val chatRepository = ChatRepository(context)
    val modelManager = ModelManager(context)
    private val llama = LlamaInferenceManager()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel

    private val _chatsLoaded = MutableStateFlow(false)
    val chatsLoaded: StateFlow<Boolean> = _chatsLoaded

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _inferenceError = MutableStateFlow<String?>(null)
    val inferenceError: StateFlow<String?> = _inferenceError

    init {
        viewModelScope.launch {
            preferences.selectedModelFile.collectLatest { _selectedModel.value = it }
        }
        viewModelScope.launch {
            chatRepository.load()
            modelManager.refresh()
            // auto-select first available if none selected
            if (_selectedModel.value == null) {
                val available = modelManager.available.value
                if (available.isNotEmpty()) {
                    val first = available.first()
                    preferences.setSelectedModel(first.fileName)
                }
            }
            _chatsLoaded.value = true
        }
        viewModelScope.launch {
            modelManager.available.collectLatest { avail ->
                if (_selectedModel.value == null && avail.isNotEmpty()) {
                    preferences.setSelectedModel(avail.first().fileName)
                }
                if (_selectedModel.value != null && avail.none { it.fileName == _selectedModel.value }) {
                    // selected model removed, pick another if exists else null
                    val newPick = avail.firstOrNull()?.fileName
                    preferences.setSelectedModel(newPick)
                }
            }
        }
    }

    fun setSelectedModel(id: ModelId) {
        viewModelScope.launch {
            // unload previous to free RAM before switching
            if (_selectedModel.value != id.fileName) llama.unload()
            preferences.setSelectedModel(id.fileName)
        }
    }

    fun download(id: ModelId) {
        viewModelScope.launch { modelManager.download(id) }
    }

    fun cancelDownload(id: ModelId) = modelManager.cancel(id)

    fun deleteModel(id: ModelId) {
        viewModelScope.launch { modelManager.delete(id) }
    }

    fun deleteAllData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            llama.unload()
            modelManager.deleteAllAndClearData(chatRepository, preferences)
            onDone()
        }
    }

    fun createChat(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val chat = chatRepository.createChat()
            onCreated(chat.id)
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch { chatRepository.deleteChat(id) }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch { chatRepository.deleteMessage(chatId, messageId) }
    }

    fun sendMessage(chatId: String, userText: String, attachmentName: String? = null, attachmentText: String? = null) {
        viewModelScope.launch {
            val displayText = if (userText.isBlank() && !attachmentText.isNullOrBlank()) "Attached ${attachmentName ?: "file"} for analysis" else userText
            val userMsg = ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = "user",
                content = userText.ifBlank { if (!attachmentText.isNullOrBlank()) "[File: $attachmentName]" else "" },
                timestamp = System.currentTimeMillis(),
                attachmentName = attachmentName,
                attachmentText = attachmentText?.take(15000)
            )
            chatRepository.addMessage(chatId, userMsg, newTitle = displayText.take(40))
            val selectedFile = _selectedModel.value
            if (selectedFile == null) {
                val err = ChatMessage(java.util.UUID.randomUUID().toString(), "assistant", "No model selected. Go to Settings and select a downloaded model.", System.currentTimeMillis())
                chatRepository.addMessage(chatId, err)
                return@launch
            }
            val modelId = ModelId.fromFileName(selectedFile)
            if (modelId == null || !modelManager.isDownloaded(modelId)) {
                val err = ChatMessage(java.util.UUID.randomUUID().toString(), "assistant", "Selected model not found. Please re-download it.", System.currentTimeMillis())
                chatRepository.addMessage(chatId, err)
                return@launch
            }
            _isGenerating.value = true
            _inferenceError.value = null
            // Create placeholder assistant message for streaming
            val assistantId = java.util.UUID.randomUUID().toString()
            val placeholder = ChatMessage(assistantId, "assistant", "▌", System.currentTimeMillis())
            chatRepository.addMessage(chatId, placeholder)

            try {
                val modelFile = modelManager.getModelFile(modelId)
                val loaded = llama.ensureLoaded(modelFile)
                if (!loaded) throw IllegalStateException("Failed to load GGUF (OOM? unsupported device). Try Vano mini.")

                // Build history before current user turn (exclude placeholder + current user msg)
                val chat = chatRepository.getChat(chatId)
                val history = chat?.messages?.filterNot { it.id == assistantId || it.id == userMsg.id } ?: emptyList()
                // For file analysis, append file block to prompt
                val promptWithFile = if (!attachmentText.isNullOrBlank()) {
                    "${userText.ifBlank { "Analyze this file" }}\n\n[Attached file: $attachmentName]\n${attachmentText.take(12000)}\n[End of file]"
                } else userText

                val modelTag = when (modelId) {
                    ModelId.VANO -> "vano Q4_0"
                    ModelId.VANO_MINI -> "vano Q3_K_M"
                }
                // Try real inference; on any native failure fallback to dummy so chat still works
                try {
                    val result = llama.generate(history, promptWithFile)
                    chatRepository.replaceMessageFull(
                        chatId, assistantId, result.text,
                        modelName = modelTag,
                        tokens = result.tokens,
                        genTimeMs = result.genTimeMs,
                        tokensPerSec = result.tokensPerSec
                    )
                } catch (e: Exception) {
                    _inferenceError.value = e.message
                    val label = modelId.displayName
                    val fallback = fallbackResponse(userText, label, e.message)
                    chatRepository.replaceMessageFull(chatId, assistantId, fallback, modelName = modelTag)
                }

            } catch (e: Exception) {
                chatRepository.replaceMessage(chatId, assistantId, "Error: ${e.message}")
                _inferenceError.value = e.message
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun fallbackResponse(prompt: String, model: String, error: String?): String {
        return """$model (fallback) — you said: "$prompt"

On-device inference failed${if (error != null) ": $error" else ""}.

The model is downloaded but couldn't run (likely low RAM for the 1.55 GB Q4_0). Try "Vano mini" (1.38 GB) or close other apps and retry. Once loaded, answers are fully offline and private."""
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { llama.unload() }
    }
}
