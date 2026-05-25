package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ChatDatabase.getDatabase(application)
    private val repository = ChatRepository(db.chatDao())

    val allSessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val currentMessages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList())
            else repository.getMessagesForSession(sessionId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _userInput = MutableStateFlow("")
    val userInput: StateFlow<String> = _userInput.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _systemInstruction = MutableStateFlow(
        "You are high-speed AI customer service bot for Enterprise support. Please keep answers concise, extremely polite, helpful, and formatted beautifully."
    )
    val systemInstruction: StateFlow<String> = _systemInstruction.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow<Long>(0)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    init {
        viewModelScope.launch {
            allSessions.filter { it.isNotEmpty() }.first()
            if (_currentSessionId.value == null && allSessions.value.isNotEmpty()) {
                _currentSessionId.value = allSessions.value.first().id
            }
        }
    }

    fun onUserInputChange(text: String) {
        _userInput.value = text
    }

    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun updateSystemInstruction(newInstruction: String) {
        _systemInstruction.value = newInstruction
    }

    fun changeModel(model: String) {
        _selectedModel.value = model
    }

    fun createNewSession(title: String = "Helpdesk Support") {
        viewModelScope.launch {
            val newId = repository.createNewSession(title)
            _currentSessionId.value = newId
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                val remaining = allSessions.value.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) {
                    _currentSessionId.value = remaining.first().id
                } else {
                    _currentSessionId.value = null
                }
            }
        }
    }

    fun sendMessage() {
        val prompt = _userInput.value.trim()
        if (prompt.isEmpty()) return

        _userInput.value = ""
        _isTyping.value = true

        viewModelScope.launch {
            try {
                var sessionId = _currentSessionId.value
                if (sessionId == null) {
                    val title = if (prompt.length > 25) prompt.take(25) + "..." else prompt
                    sessionId = repository.createNewSession(title)
                    _currentSessionId.value = sessionId
                }

                val response = repository.sendSupportPrompt(
                    sessionId = sessionId,
                    userPrompt = prompt,
                    modelName = _selectedModel.value,
                    systemPrompt = _systemInstruction.value
                )
                _lastLatencyMs.value = response.latencyMs
            } catch (e: Exception) {
                // Handled in repo
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }
}
