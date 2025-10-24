// FILE: app/src/main/java/com/example/tiendamascotas/domain/repository/ChatRepository.kt
package com.example.tiendamascotas.domain.repository

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import kotlinx.coroutines.flow.Flow

/** Perfil público básico mostrado en UI (reutilizado por UserRepository). */
data class UserPublic(
    val uid: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val email: String? = null
)

/** Hilo visible en la lista de chats (enriquecido con nombre/foto si aplica). */
data class ChatThread(
    val peerUid: String,
    val lastMessage: String? = null,
    val updatedAt: Long? = null,
    val unreadCount: Int? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
)

/** Presencia (RTDB). */
data class UserPresence(
    val isOnline: Boolean,
    val lastSeen: Long?
)

/** Fila combinada opcional (PRONT 10). */
interface ChatRepository {

    // ==== Búsqueda / conversación ====
    fun observeUsers(query: String): Flow<List<ChatUser>>
    fun observeConversation(peerUid: String): Flow<List<ChatMessage>>
    suspend fun sendText(peerUid: String, text: String): Result<Unit>

    // NUEVO PRONT 11: marcar leído
    suspend fun markThreadRead(peerUid: String)

    // ==== Perfiles públicos (Firestore) ====
    fun observeUserPublic(uid: String): Flow<UserPublic?>
    fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>>
    fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>>

    // (PRONT 10) filas combinadas opcionales
    data class ChatRow(
        val peerUid: String,
        val displayName: String?,
        val photoUrl: String?,
        val lastMessage: String?,
        val updatedAt: Long?,
        val unreadCount: Int?
    )
    fun observeChatRows(currentUid: String): Flow<List<ChatRow>>

    // ==== Presencia & Typing (RTDB) ====
    fun observePresence(uid: String): Flow<UserPresence>
    suspend fun setTyping(peerUid: String, typing: Boolean)
    fun observeTyping(peerUid: String): Flow<Boolean>

    // Hooks globales (app lifecycle)
    suspend fun setPresenceOnline()
    suspend fun setPresenceOffline()
}
