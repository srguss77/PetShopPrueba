package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.ChatThread
import com.example.tiendamascotas.domain.repository.UserPresence
import com.example.tiendamascotas.domain.repository.UserPublic
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Repositorio Firestore legacy (sigue existiendo para compatibilidad).
 * En esta rama tu app usa RtdbChatRepository vía ServiceLocator.chat.
 */
class FirestoreChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ChatRepository {

    private val db = Firebase.firestore
    private val rtdb = FirebaseDatabase.getInstance()

    /* ===== Usuarios (búsqueda básica) ===== */
    override fun observeUsers(query: String): Flow<List<ChatUser>> = callbackFlow {
        val me = auth.currentUser?.uid
        val q = query.trim().lowercase()

        val base = if (q.isBlank()) {
            db.collection(FirestorePaths.USERS)
                .orderBy(FirestorePaths.DISPLAY_NAME_LOWER)
                .limit(50)
        } else {
            db.collection(FirestorePaths.USERS)
                .orderBy(FirestorePaths.DISPLAY_NAME_LOWER)
                .startAt(q)
                .endAt(q + "\uf8ff")
                .limit(50)
        }

        val reg = base.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val users = snap?.documents?.mapNotNull { d ->
                val uid = (d.getString("uid") ?: d.id)
                if (uid == me) return@mapNotNull null
                ChatUser(
                    uid = uid,
                    displayName = d.getString(FirestorePaths.DISPLAY_NAME) ?: "",
                    email = d.getString("email"),
                    photoUrl = d.getString("photoUrl")
                )
            } ?: emptyList()
            trySend(users)
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    /* ===== Conversación y envío (legacy sobre Firestore) ===== */
    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> = callbackFlow {
        val me = auth.currentUser?.uid
        if (me == null) { trySend(emptyList()); close(); return@callbackFlow }

        val ref = db.collection(FirestorePaths.USERS).document(me)
            .collection(FirestorePaths.CHATS).document(peerUid)
            .collection(FirestorePaths.MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents?.map { d ->
                ChatMessage(
                    id = d.id,
                    fromUid = d.getString("fromUid") ?: "",
                    text = d.getString("text") ?: "",
                    createdAt = d.get("createdAt").toMillisOrZero()
                )
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    override suspend fun sendText(peerUid: String, text: String): Result<Unit> = runCatching {
        val me = auth.currentUser?.uid ?: error("No authenticated user")
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Empty message" }

        val myChat = db.collection(FirestorePaths.USERS).document(me)
            .collection(FirestorePaths.CHATS).document(peerUid)
        val peerChat = db.collection(FirestorePaths.USERS).document(peerUid)
            .collection(FirestorePaths.CHATS).document(me)

        val newId = myChat.collection(FirestorePaths.MESSAGES).document().id
        val now = FieldValue.serverTimestamp()
        val msgData = mapOf(
            "fromUid" to me,
            "text" to trimmed,
            "createdAt" to now
        )

        db.runBatch { b ->
            b.set(
                myChat,
                mapOf(
                    "peerUid" to peerUid,
                    "lastMessage" to trimmed,
                    "updatedAt" to now,
                    "unreadCount" to 0
                ),
                SetOptions.merge()
            )
            b.set(
                peerChat,
                mapOf(
                    "peerUid" to me,
                    "lastMessage" to trimmed,
                    "updatedAt" to now,
                    "unreadCount" to FieldValue.increment(1)
                ),
                SetOptions.merge()
            )
            b.set(myChat.collection(FirestorePaths.MESSAGES).document(newId), msgData)
            b.set(peerChat.collection(FirestorePaths.MESSAGES).document(newId), msgData)
        }.await()
        Unit
    }

    // NUEVO: marcar leído
    override suspend fun markThreadRead(peerUid: String) {
        val me = auth.currentUser?.uid ?: return
        db.collection(FirestorePaths.USERS).document(me)
            .collection(FirestorePaths.CHATS).document(peerUid)
            .update("unreadCount", 0)
            .await()
    }

    /* ===== Perfiles públicos (Firestore) ===== */
    override fun observeUserPublic(uid: String): Flow<UserPublic?> = callbackFlow {
        if (uid.isBlank()) { trySend(null); close(); return@callbackFlow }
        val ref = db.collection(FirestorePaths.USERS).document(uid)
        val reg = ref.addSnapshotListener { snap, _ ->
            val u = if (snap != null && snap.exists()) {
                UserPublic(
                    uid = snap.id,
                    displayName = snap.getString(FirestorePaths.DISPLAY_NAME),
                    photoUrl = snap.getString("photoUrl"),
                    email = snap.getString("email")
                )
            } else null
            trySend(u)
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    override fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>> = callbackFlow {
        if (uids.isEmpty()) { trySend(emptyMap()); close(); return@callbackFlow }
        val regs = mutableMapOf<String, ListenerRegistration>()
        val cache = mutableMapOf<String, UserPublic>()
        uids.forEach { uid ->
            val r = db.collection(FirestorePaths.USERS).document(uid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) {
                        cache[uid] = UserPublic(
                            uid = snap.id,
                            displayName = snap.getString(FirestorePaths.DISPLAY_NAME),
                            photoUrl = snap.getString("photoUrl"),
                            email = snap.getString("email")
                        )
                    } else {
                        cache.remove(uid)
                    }
                    trySend(cache.toMap())
                }
            regs[uid] = r
        }
        awaitClose { regs.values.forEach { it.remove() } }
    }.distinctUntilChanged()

    /* ===== Threads enriquecidos (legacy Firestore) ===== */
    override fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>> = callbackFlow {
        val chatsRef = db.collection(FirestorePaths.USERS)
            .document(currentUid)
            .collection(FirestorePaths.CHATS)
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        val reg = chatsRef.addSnapshotListener { snap, err ->
            if (err != null || snap == null) { trySend(emptyList()); return@addSnapshotListener }
            val base = snap.documents.map { d ->
                val peer = (d.getString("peerUid") ?: d.id).orEmpty()
                val last = d.getString("lastMessage")
                val updated = d.get("updatedAt").toMillisOrNull()
                val unread = d.getLong("unreadCount")?.toInt()
                ChatThread(peerUid = peer, lastMessage = last, updatedAt = updated, unreadCount = unread)
            }
            trySend(base)
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    /* ===== Presence & Typing via RTDB (para compatibilidad) ===== */
    override fun observePresence(uid: String): Flow<UserPresence> {
        val ref = rtdb.getReference("presence").child(uid)
        return callbackFlow {
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val online = s.child("online").getValue(Boolean::class.java) ?: s.exists()
                    val lastSeen = s.child("lastSeen").getValue(Long::class.java)
                        ?: s.child("lastSeen").getValue(Double::class.java)?.toLong()
                    trySend(UserPresence(online, lastSeen))
                }
                override fun onCancelled(error: DatabaseError) { trySend(UserPresence(false, null)) }
            }
            ref.addValueEventListener(l)
            awaitClose { ref.removeEventListener(l) }
        }.distinctUntilChanged()
    }

    override suspend fun setTyping(peerUid: String, typing: Boolean) {
        val me = auth.currentUser?.uid ?: return
        val ref = rtdb.getReference("typing").child(peerUid).child(me)
        if (typing) {
            ref.onDisconnect().removeValue()
            ref.setValue(true)
        } else {
            ref.removeValue()
        }
    }

    override fun observeTyping(peerUid: String): Flow<Boolean> {
        val me = auth.currentUser?.uid
        if (me == null) return callbackFlow { trySend(false); awaitClose {} }
        val ref = rtdb.getReference("typing").child(me).child(peerUid)
        return callbackFlow {
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) { trySend(s.exists()).isSuccess }
                override fun onCancelled(error: DatabaseError) { trySend(false).isSuccess }
            }
            ref.addValueEventListener(l)
            awaitClose { ref.removeEventListener(l) }
        }.distinctUntilChanged()
    }

    override suspend fun setPresenceOnline() {
        val me = auth.currentUser?.uid ?: return
        val ref = rtdb.getReference("presence").child(me)
        ref.onDisconnect().setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
        ref.setValue(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))
    }

    override suspend fun setPresenceOffline() {
        val me = auth.currentUser?.uid ?: return
        val ref = rtdb.getReference("presence").child(me)
        ref.setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
    }

    // PRONT 10/11: no lo usamos aquí, devolver vacío para compatibilidad
    override fun observeChatRows(currentUid: String) =
        callbackFlow<List<ChatRepository.ChatRow>> { trySend(emptyList()); awaitClose { } }
}

/* ===== Helpers ===== */
private fun Any?.toMillisOrNull(): Long? = when (this) {
    is com.google.firebase.Timestamp -> this.toDate().time
    is Date -> this.time
    is Number -> this.toLong()
    is Map<*, *> -> {
        val sec = (this["seconds"] as? Number)?.toLong()
        val nano = (this["nanoseconds"] as? Number)?.toLong() ?: 0L
        sec?.let { it * 1000L + (nano / 1_000_000L) }
    }
    else -> null
}
private fun Any?.toMillisOrZero(): Long = toMillisOrNull() ?: 0L
