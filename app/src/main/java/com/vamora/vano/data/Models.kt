package com.vamora.vano.data

import kotlinx.serialization.Serializable

enum class ModelId(val displayName: String, val fileName: String, val url: String, val sizeLabel: String, val description: String) {
    VANO(
        displayName = "Vano",
        fileName = "vano-Q4_0.gguf",
        url = "https://github.com/TheVamoraProject/Vano/releases/download/1.0/vano-Q4_0.gguf",
        sizeLabel = "1.55 GB",
        description = "Main model • Best quality • Recommended"
    ),
    VANO_MINI(
        displayName = "Vano mini",
        fileName = "vano-mini-Q3_K_M.gguf",
        url = "https://github.com/TheVamoraProject/Vano/releases/download/mini/vano-mini-Q3_K_M.gguf",
        sizeLabel = "1.38 GB",
        description = "Lightweight • Faster • Lower RAM"
    );

    companion object {
        fun fromFileName(name: String): ModelId? = entries.find { it.fileName == name }
    }
}

@Serializable
data class Chat(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage> = emptyList()
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long,
    val modelName: String? = null, // e.g. "vano Q4_0" or "vano mini Q3_K_M"
    val tokens: Int? = null,
    val genTimeMs: Long? = null,
    val tokensPerSec: Float? = null,
    val attachmentName: String? = null,
    val attachmentText: String? = null
)

fun Chat.preview(): String {
    return messages.lastOrNull()?.content?.take(60) ?: "No messages yet"
}
