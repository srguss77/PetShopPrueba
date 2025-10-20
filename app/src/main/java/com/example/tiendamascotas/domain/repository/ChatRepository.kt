package com.example.tiendamascotas.domain.repository

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeUsers(query: String): Flow<List<ChatUser>>
    fun observeConversation(peerUid: String): Flow<List<ChatMessage>>
    suspend fun sendText(peerUid: String, text: String): Result<Unit>
}
