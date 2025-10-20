package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class PlaceholderChatRepository : ChatRepository {

    // Solo mostramos el chat fijo "PetShop IA"
    private val allUsers = listOf(
        ChatUser(
            uid = "petshop_bot",
            displayName = "PetShop IA",
            photoUrl = null,
            email = null
        )
    )

    // Mensajes en memoria por peerUid
    private val threads = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()

    override fun observeUsers(query: String): Flow<List<ChatUser>> {
        val q = query.trim().lowercase()
        val base = if (q.isBlank()) allUsers else allUsers.filter {
            it.displayName.lowercase().contains(q)
                    || (it.email ?: "").lowercase().contains(q)
                    || it.uid.lowercase().contains(q)
        }
        // Exponemos como Flow simple (StateFlow interno por si lo quieres extender)
        val state = MutableStateFlow(base)
        return state
    }

    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> {
        val flow = threads.getOrPut(peerUid) {
            // Mensaje inicial opcional
            MutableStateFlow(
                listOf(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        fromUid = "petshop_bot",
                        text = "¡Hola! Soy PetShop IA 😊 ¿En qué te ayudo?",
                        createdAt = System.currentTimeMillis() - 1_000L
                    )
                )
            )
        }
        // Orden cronológico ascendente
        return flow.map { it.sortedBy { m -> m.createdAt } }
    }

    override suspend fun sendText(peerUid: String, text: String): Result<Unit> {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: "me"
        val flow = threads.getOrPut(peerUid) { MutableStateFlow(emptyList()) }
        val newMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            fromUid = myUid,
            text = text.trim(),
            createdAt = System.currentTimeMillis()
        )
        flow.value = flow.value + newMsg
        return Result.success(Unit)
    }
}
