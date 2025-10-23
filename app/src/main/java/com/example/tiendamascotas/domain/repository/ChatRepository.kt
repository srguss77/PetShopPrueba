// FILE: app/src/main/java/com/example/tiendamascotas/domain/repository/ChatRepository.kt
package com.example.tiendamascotas.domain.repository

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import kotlinx.coroutines.flow.Flow

/** Perfil público básico mostrado en UI. */
data class UserPublic(
    val uid: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val email: String? = null
)

/** Hilo visible en la lista de chats (enriquecido con nombre/foto). */
data class ChatThread(
    val peerUid: String,
    val lastMessage: String? = null,
    val updatedAt: Long? = null,
    val unreadCount: Int? = null,
    // Enriquecimiento (nombre/foto)
    val displayName: String? = null,
    val photoUrl: String? = null
)

/** Presencia (RTDB). */
data class UserPresence(
    val isOnline: Boolean,
    val lastSeen: Long?
)

interface ChatRepository {

    // ===== Búsqueda / conversación existentes =====
    fun observeUsers(query: String): Flow<List<ChatUser>>
    fun observeConversation(peerUid: String): Flow<List<ChatMessage>>
    suspend fun sendText(peerUid: String, text: String): Result<Unit>

    // ===== Perfiles públicos (Firestore) =====
    fun observeUserPublic(uid: String): Flow<UserPublic?>
    fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>>
    fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>>

    // ===== Presencia & Typing (RTDB) =====
    fun observePresence(uid: String): Flow<UserPresence>
    suspend fun setTyping(peerUid: String, typing: Boolean)
    fun observeTyping(peerUid: String): Flow<Boolean>

    // Hooks globales (app lifecycle)
    suspend fun setPresenceOnline()
    suspend fun setPresenceOffline()
}
