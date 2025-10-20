package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import com.example.tiendamascotas.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class PlaceholderChatRepository : ChatRepository {

    private val _users = MutableStateFlow(
        listOf(
            ChatUser(uid = "demo1", displayName = "Demo 1"),
            ChatUser(uid = "demo2", displayName = "Demo 2")
        )
    )
    override fun observeUsers(): Flow<List<ChatUser>> = _users.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> =
        _messages.asStateFlow().map { list -> list.filter { it.peerUid == peerUid } }

    override suspend fun sendText(peerUid: String, body: String): Result<Unit> {
        _messages.value = _messages.value + ChatMessage(
            id = System.currentTimeMillis().toString(),
            peerUid = peerUid,
            fromMe = true,
            text = body,
            timestamp = System.currentTimeMillis()
        )
        return Result.success(Unit)
    }
}
