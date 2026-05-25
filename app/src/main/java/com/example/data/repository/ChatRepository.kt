package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.ChatDao
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.remote.Content
import com.example.data.remote.GenerateContentRequest
import com.example.data.remote.Part
import com.example.data.remote.RetrofitClient
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun createNewSession(title: String): String {
        val session = ChatSession(title = title)
        chatDao.insertSession(session)
        return session.id
    }

    suspend fun updateSessionTitle(sessionId: String, newTitle: String) {
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            chatDao.updateSession(session.copy(title = newTitle, lastActiveTimestamp = System.currentTimeMillis()))
        }
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSessionById(sessionId)
        chatDao.clearSessionMessages(sessionId)
    }

    suspend fun insertMessage(message: ChatMessage) {
        chatDao.insertMessage(message)
        val session = chatDao.getSessionById(message.sessionId)
        if (session != null) {
            chatDao.updateSession(session.copy(lastActiveTimestamp = System.currentTimeMillis()))
        }
    }

    suspend fun sendSupportPrompt(
        sessionId: String,
        userPrompt: String,
        modelName: String = "gemini-3.5-flash",
        systemPrompt: String = "You are a professional enterprise tech support assistant. Keep answers brief, accurate, and direct."
    ): ChatMessage {
        val userMsg = ChatMessage(
            sessionId = sessionId,
            role = "user",
            text = userPrompt
        )
        insertMessage(userMsg)

        val history = chatDao.getMessagesForSessionSync(sessionId)
        val apiContents = history.filter { !it.isError && it.text.isNotBlank() }.map {
            Content(
                role = if (it.role == "user") "user" else "model",
                parts = listOf(Part(text = it.text))
            )
        }

        val startTime = System.currentTimeMillis()
        var responseText = ""
        var isError = false

        try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
                responseText = "Gemini API key is unconfigured. Please configure your GEMINI_API_KEY in the AI Studio Secrets panel to connect to Gemini."
                isError = true
            } else {
                val request = GenerateContentRequest(
                    contents = apiContents,
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                )
                val apiResponse = RetrofitClient.service.generateContent(
                    model = modelName,
                    apiKey = key,
                    request = request
                )
                responseText = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "We apologize, but support is currently experiencing a temporary service interruption."
            }
        } catch (e: Exception) {
            responseText = "Network error: ${e.localizedMessage ?: "Connection reset"}. Please verify your network connection and API key parameters."
            isError = true
        }

        val latency = System.currentTimeMillis() - startTime

        val responseMsg = ChatMessage(
            sessionId = sessionId,
            role = "model",
            text = responseText,
            latencyMs = if (isError) 0 else latency,
            isError = isError
        )
        insertMessage(responseMsg)

        return responseMsg
    }
}
