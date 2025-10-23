// FILE: app/src/main/java/com/example/tiendamascotas/data/repository/impl/PlaceholderRepository.kt
package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.ChatThread
import com.example.tiendamascotas.domain.repository.UserPresence
import com.example.tiendamascotas.domain.repository.UserPublic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Implementación de respaldo para tests/local sin backend.
 * Devuelve datos vacíos y no-ops, pero cumple el contrato completo del ChatRepository.
 */
class PlaceholderChatRepository : ChatRepository {
    override fun observeUsers(query: String): Flow<List<ChatUser>> = flowOf(emptyList())
    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> = flowOf(emptyList())
    override suspend fun sendText(peerUid: String, text: String): Result<Unit> = Result.success(Unit)

    override fun observeUserPublic(uid: String): Flow<UserPublic?> = flowOf(null)
    override fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>> = flowOf(emptyMap())
    override fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>> = flowOf(emptyList())

    // Presencia & Typing (stubs)
    override fun observePresence(uid: String): Flow<UserPresence> = flowOf(UserPresence(false, null))
    override suspend fun setTyping(peerUid: String, typing: Boolean) { /* no-op */ }
    override fun observeTyping(peerUid: String): Flow<Boolean> = flowOf(false)

    // Hooks de ciclo de vida
    override suspend fun setPresenceOnline() { /* no-op */ }
    override suspend fun setPresenceOffline() { /* no-op */ }
}
