package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.data.repository.impl.FirestorePaths
import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await   // <- IMPORT CLAVE

class FirestoreChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ChatRepository {

    private val db = Firebase.firestore

    /** Lista de usuarios con búsqueda por prefijo (displayNameLower). */
    override fun observeUsers(query: String): Flow<List<ChatUser>> = callbackFlow {
        val me = auth.currentUser?.uid
        val q = query.trim().lowercase()

        val base = if (q.isBlank()) {
            db.collection(FirestorePaths.USERS)
                .orderBy("displayNameLower")
                .limit(50)
        } else {
            db.collection(FirestorePaths.USERS)
                .orderBy("displayNameLower")
                .startAt(q)
                .endAt(q + "\uf8ff")
                .limit(50)
        }

        val reg = base.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val users = snap?.documents
                ?.mapNotNull { d ->
                    val uid = (d.getString("uid") ?: d.id)
                    if (uid == me) return@mapNotNull null
                    ChatUser(
                        uid = uid,
                        displayName = d.getString("displayName") ?: "",
                        email = d.getString("email"),
                        photoUrl = d.getString("photoUrl")
                    )
                } ?: emptyList()
            trySend(users)
        }
        awaitClose { reg.remove() }
    }

    /** Mensajes del hilo (MI vista) ordenados por fecha ascendente. */
    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> = callbackFlow {
        val me = auth.currentUser?.uid
        if (me == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = db.collection(FirestorePaths.USERS).document(me)
            .collection(FirestorePaths.CHATS).document(peerUid)
            .collection(FirestorePaths.MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList()); return@addSnapshotListener
            }
            val list = snap?.documents?.map { d ->
                ChatMessage(
                    id = d.id,
                    fromUid = d.getString("fromUid") ?: "",
                    text = d.getString("text") ?: "",
                    createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                )
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    /** Envío con espejo en ambos usuarios y actualización de lastMessage/updatedAt. */
    override suspend fun sendText(peerUid: String, text: String): Result<Unit> = runCatching {
        val me = auth.currentUser?.uid ?: error("No authenticated user")
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Empty message" }

        val myChat = db.collection(FirestorePaths.USERS).document(me)
            .collection(FirestorePaths.CHATS).document(peerUid)
        val peerChat = db.collection(FirestorePaths.USERS).document(peerUid)
            .collection(FirestorePaths.CHATS).document(me)

        // Usamos un mismo id para ambos
        val newId = myChat.collection(FirestorePaths.MESSAGES).document().id

        val now = FieldValue.serverTimestamp()
        val msgData = mapOf(
            "fromUid" to me,
            "text" to trimmed,
            "createdAt" to now
        )

        db.runBatch { b ->
            // metadata hilos
            b.set(myChat, mapOf("peerUid" to peerUid, "lastMessage" to trimmed, "updatedAt" to now), SetOptions.merge())
            b.set(peerChat, mapOf("peerUid" to me,   "lastMessage" to trimmed, "updatedAt" to now), SetOptions.merge())

            // mensajes espejo
            b.set(myChat.collection(FirestorePaths.MESSAGES).document(newId), msgData)
            b.set(peerChat.collection(FirestorePaths.MESSAGES).document(newId), msgData)
        }.await()

        Unit  // <- devolvemos Unit para que encaje con Result<Unit>
    }
}
