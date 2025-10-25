// FILE: app/src/main/java/com/example/tiendamascotas/domain/repository/ChatRepository.kt
package com.example.tiendamascotas.domain.repository

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import kotlinx.coroutines.flow.Flow

data class UserPublic(
    val uid: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val email: String? = null
)

data class ChatThread(
    val peerUid: String,
    val lastMessage: String? = null,
    val updatedAt: Long? = null,
    val unreadCount: Int? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
)


data class UserPresence(
    val isOnline: Boolean,
    val lastSeen: Long?
)


interface ChatRepository {


    fun observeUsers(query: String): Flow<List<ChatUser>>
    fun observeConversation(peerUid: String): Flow<List<ChatMessage>>
    suspend fun sendText(peerUid: String, text: String): Result<Unit>


    suspend fun markThreadRead(peerUid: String)


    fun observeUserPublic(uid: String): Flow<UserPublic?>
    fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>>
    fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>>


    data class ChatRow(
        val peerUid: String,
        val displayName: String?,
        val photoUrl: String?,
        val lastMessage: String?,
        val updatedAt: Long?,
        val unreadCount: Int?
    )
    fun observeChatRows(currentUid: String): Flow<List<ChatRow>>


    fun observePresence(uid: String): Flow<UserPresence>
    suspend fun setTyping(peerUid: String, typing: Boolean)
    fun observeTyping(peerUid: String): Flow<Boolean>

    suspend fun setPresenceOnline()
    suspend fun setPresenceOffline()
}
