package com.vamora.vano.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ChatRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file: File get() = File(context.filesDir, "chats.json")

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            _chats.value = emptyList()
            return@withContext
        }
        try {
            val text = file.readText()
            if (text.isBlank()) _chats.value = emptyList()
            else _chats.value = json.decodeFromString<List<Chat>>(text)
        } catch (e: Exception) {
            _chats.value = emptyList()
        }
    }

    private suspend fun persist(list: List<Chat>) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(list))
        _chats.value = list
    }

    suspend fun createChat(title: String = "New chat"): Chat {
        val now = System.currentTimeMillis()
        val chat = Chat(id = UUID.randomUUID().toString(), title = title, createdAt = now, updatedAt = now, messages = emptyList())
        val newList = listOf(chat) + _chats.value
        persist(newList)
        return chat
    }

    suspend fun deleteChat(id: String) {
        persist(_chats.value.filterNot { it.id == id })
    }

    suspend fun renameChat(id: String, newTitle: String) {
        persist(_chats.value.map { if (it.id == id) it.copy(title = newTitle, updatedAt = System.currentTimeMillis()) else it })
    }

    suspend fun addMessage(chatId: String, message: ChatMessage, newTitle: String? = null) {
        persist(_chats.value.map { c ->
            if (c.id != chatId) c else {
                val updatedMessages = c.messages + message
                val title = if (c.messages.isEmpty() && newTitle != null) newTitle.take(40) else c.title
                // also if first message and title is default, use message preview
                val finalTitle = if (c.title == "New chat" && c.messages.isEmpty()) newTitle?.take(40) ?: c.title else title
                c.copy(title = finalTitle, messages = updatedMessages, updatedAt = System.currentTimeMillis())
            }
        })
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete()
        _chats.value = emptyList()
    }

    fun getChat(id: String): Chat? = _chats.value.find { it.id == id }

    suspend fun replaceMessage(chatId: String, messageId: String, newContent: String) {
        persist(_chats.value.map { c ->
            if (c.id != chatId) c else c.copy(
                messages = c.messages.map { m -> if (m.id == messageId) m.copy(content = newContent) else m },
                updatedAt = System.currentTimeMillis()
            )
        })
    }

    suspend fun replaceMessageFull(chatId: String, messageId: String, newContent: String, modelName: String? = null, tokens: Int? = null, genTimeMs: Long? = null, tokensPerSec: Float? = null) {
        persist(_chats.value.map { c ->
            if (c.id != chatId) c else c.copy(
                messages = c.messages.map { m -> if (m.id == messageId) m.copy(content = newContent, modelName = modelName ?: m.modelName, tokens = tokens ?: m.tokens, genTimeMs = genTimeMs ?: m.genTimeMs, tokensPerSec = tokensPerSec ?: m.tokensPerSec) else m },
                updatedAt = System.currentTimeMillis()
            )
        })
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        persist(_chats.value.map { c ->
            if (c.id != chatId) c else c.copy(
                messages = c.messages.filterNot { it.id == messageId },
                updatedAt = System.currentTimeMillis()
            )
        })
    }

    suspend fun updateMessageStreaming(chatId: String, messageId: String, newContent: String) {
        // Same as replace but without persisting to disk every token – update in-memory only, persist on finish
        _chats.value = _chats.value.map { c ->
            if (c.id != chatId) c else c.copy(
                messages = c.messages.map { m -> if (m.id == messageId) m.copy(content = newContent) else m }
            )
        }
    }

    suspend fun persistCurrent() {
        persist(_chats.value)
    }
}
