// FILE: app/src/main/java/com/example/tiendamascotas/data/repository/impl/PlaceholderRepository.kt
package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.ChatThread
import com.example.tiendamascotas.domain.repository.UserPresence
import com.example.tiendamascotas.domain.repository.UserPublic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class PlaceholderChatRepository : ChatRepository {
    private val threadsFlow = MutableStateFlow<List<ChatThread>>(emptyList())
    private val typingFlow = MutableStateFlow(false)

    override fun observeUsers(query: String): Flow<List<ChatUser>> = flowOf(emptyList())
    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> = flowOf(emptyList())
    override suspend fun sendText(peerUid: String, text: String): Result<Unit> = Result.success(Unit)

    override fun observeUserPublic(uid: String): Flow<UserPublic?> = flowOf(
        UserPublic(uid = uid, displayName = "Usuario", photoUrl = null)
    )
    override fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>> =
        flowOf(uids.associateWith { uid -> UserPublic(uid = uid, displayName = "Usuario", photoUrl = null) })

    override fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>> = threadsFlow

    override fun observePresence(uid: String) = flowOf(UserPresence(isOnline = false, lastSeen = null))
    override suspend fun setTyping(peerUid: String, typing: Boolean) { typingFlow.value = typing }
    override fun observeTyping(peerUid: String) = typingFlow
}
