package com.example.tiendamascotas.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiendamascotas.assistant.GeminiProvider
import com.example.tiendamascotas.assistant.data.PetBotRepository
import com.google.ai.client.generativeai.type.GoogleGenerativeAIException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val fromUser: Boolean)
data class ChatUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("¡Hola! Soy PetBot 🐾 ¿En qué te ayudo con tu mascota?", false)
    ),
    val isSending: Boolean = false
)

class PetBotViewModel : ViewModel() {

    private val repo = PetBotRepository(GeminiProvider.model)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    fun send(userText: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, messages = it.messages + ChatMessage(userText, true)) }

            val result = repo.ask(
                prompt = userText,
                fallbackModelProvider = { GeminiProvider.fallback() }
            )

            val reply = result.getOrElse { e ->
                when (e) {
                    is GoogleGenerativeAIException -> "⚠️ Error del servidor: ${e.message}"
                    else -> "⚠️ No pude responder. ${e.message ?: ""}"
                }.trim()
            }

            _state.update { it.copy(isSending = false, messages = it.messages + ChatMessage(reply, false)) }
        }
    }
}
