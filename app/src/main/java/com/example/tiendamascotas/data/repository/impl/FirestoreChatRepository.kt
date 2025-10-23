// FILE: app/src/main/java/com/example/tiendamascotas/data/repository/impl/FirestoreChatRepository.kt
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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.LinkedHashMap

/** Rutas de Firestore ya usadas en el proyecto. */
object FirestorePaths {
    const val USERS = "users"
    const val CHATS = "chats"
    const val REPORTS = "reports"
    const val MESSAGES = "messages"
    const val DISPLAY_NAME = "displayName"
    const val DISPLAY_NAME_LOWER = "displayNameLower"
    const val IA_PEER = "__IA__"
}

/** Rutas de RTDB para presencia/typing. */
private object RtdbPaths {
    const val PRESENCE = "presence"
    const val TYPING = "typing"
    const val INFO_CONNECTED = ".info/connected"
}

class FirestoreChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ChatRepository {

    private val db = Firebase.firestore
    private val rtdb = Firebase.database

    /* ======================== Users search (Firestore) ======================== */
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
            if (err != null) {
                trySend(emptyList()); return@addSnapshotListener
            }
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

    /* ==================== Conversation (messages) - Firestore ==================== */
    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> = callbackFlow {
        val me = auth.currentUser?.uid
        if (me == null) {
            trySend(emptyList()); close(); return@callbackFlow
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

    /* ========================= Users public (Firestore) ========================= */

    // Cache LRU (capacidad 200) para perfiles
    private val userCache = object : LinkedHashMap<String, UserPublic>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, UserPublic>?): Boolean {
            return size > 200
        }
    }
    private val userCacheLock = Any()

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

            synchronized(userCacheLock) {
                if (u != null) userCache[uid] = u else userCache.remove(uid)
            }
            trySend(u)
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    override fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>> = callbackFlow {
        if (uids.isEmpty()) { trySend(emptyMap()); close(); return@callbackFlow }

        // 1) Batch inicial con whereIn(documentId) en chunks de 10 (límite Firestore)
        val initialJob = launch {
            val initial = mutableMapOf<String, UserPublic>()
            val chunks = uids.toList().chunked(10)
            for (chunk in chunks) {
                val snap = db.collection(FirestorePaths.USERS)
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                for (d in snap.documents) {
                    if (d.exists()) {
                        val u = UserPublic(
                            uid = d.id,
                            displayName = d.getString(FirestorePaths.DISPLAY_NAME),
                            photoUrl = d.getString("photoUrl"),
                            email = d.getString("email")
                        )
                        synchronized(userCacheLock) { userCache[d.id] = u }
                        initial[d.id] = u
                    }
                }
            }
            trySend(initial)
        }

        // 2) Listeners individuales solo para el set (suscripción en vivo)
        val regs = mutableMapOf<String, ListenerRegistration>()
        uids.forEach { uid ->
            regs[uid] = db.collection(FirestorePaths.USERS).document(uid)
                .addSnapshotListener { snap, _ ->
                    val changed = if (snap != null && snap.exists()) {
                        UserPublic(
                            uid = snap.id,
                            displayName = snap.getString(FirestorePaths.DISPLAY_NAME),
                            photoUrl = snap.getString("photoUrl"),
                            email = snap.getString("email")
                        )
                    } else null

                    val out = synchronized(userCacheLock) {
                        if (changed != null) userCache[uid] = changed else userCache.remove(uid)
                        uids.mapNotNull { id -> userCache[id]?.let { id to it } }.toMap()
                    }
                    trySend(out)
                }
        }

        awaitClose {
            initialJob.cancel()
            regs.values.forEach { it.remove() }
        }
    }.distinctUntilChanged()

    /* ========================= Threads (enriquecidos) - Firestore ========================= */

    override fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>> = channelFlow {
        val chatsRef = db.collection(FirestorePaths.USERS)
            .document(currentUid)
            .collection(FirestorePaths.CHATS)
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        var latestBase: List<ChatThread> = emptyList()
        var latestProfiles: Map<String, UserPublic> = emptyMap()
        var usersJob: Job? = null

        val reg = chatsRef.addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                trySend(emptyList()); return@addSnapshotListener
            }

            val base = snap.documents.map { d ->
                val peer = (d.getString("peerUid") ?: d.id).orEmpty()
                val last = d.getString("lastMessage")
                val updated = d.get("updatedAt").toMillisOrNull()
                val unread = d.getLong("unreadCount")?.toInt()
                ChatThread(
                    peerUid = peer,
                    lastMessage = last,
                    updatedAt = updated,
                    unreadCount = unread
                )
            }
            latestBase = base

            val peerSet = base.map { it.peerUid }.toSet()

            usersJob?.cancel()
            usersJob = launch {
                observeUsersPublic(peerSet).collectLatest { map ->
                    latestProfiles = map
                    val enriched = latestBase.map { t ->
                        val up = latestProfiles[t.peerUid]
                        t.copy(displayName = up?.displayName, photoUrl = up?.photoUrl)
                    }
                    trySend(enriched)
                }
            }

            // Emisión inicial con cache si hay
            val enriched = base.map { t ->
                val up = latestProfiles[t.peerUid]
                t.copy(displayName = up?.displayName, photoUrl = up?.photoUrl)
            }
            trySend(enriched)
        }

        awaitClose {
            reg.remove()
            usersJob?.cancel()
        }
    }.distinctUntilChanged()

    /* =================== Presence & Typing (RTDB) =================== */

    private fun presenceRef(uid: String): DatabaseReference =
        rtdb.getReference(RtdbPaths.PRESENCE).child(uid)

    private fun typingOutRef(peerUid: String, me: String): DatabaseReference =
        rtdb.getReference(RtdbPaths.TYPING).child(peerUid).child(me)

    private fun typingInRef(peerUid: String, me: String): DatabaseReference =
        rtdb.getReference(RtdbPaths.TYPING).child(me).child(peerUid)

    override fun observePresence(uid: String): Flow<UserPresence> = callbackFlow {
        if (uid.isBlank()) { trySend(UserPresence(false, null)); close(); return@callbackFlow }

        val ref = presenceRef(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java)
                    ?: snapshot.exists() // fallback: nodo presente => online
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java)
                trySend(UserPresence(online, lastSeen))
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(UserPresence(false, null))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }.distinctUntilChanged()

    override suspend fun setTyping(peerUid: String, typing: Boolean) {
        val me = auth.currentUser?.uid ?: return
        val ref = typingOutRef(peerUid, me)
        if (typing) {
            // Eliminar al desconectar y marcar escribiendo
            ref.onDisconnect().removeValue()
            ref.setValue(true)
        } else {
            ref.removeValue()
        }
    }

    override fun observeTyping(peerUid: String): Flow<Boolean> = callbackFlow {
        val me = auth.currentUser?.uid
        if (me == null || peerUid.isBlank()) { trySend(false); close(); return@callbackFlow }

        val ref = typingInRef(peerUid, me)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.exists())
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(false)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }.distinctUntilChanged()

    override suspend fun setPresenceOnline() {
        val me = auth.currentUser?.uid ?: return
        val ref = presenceRef(me)
        // Programa onDisconnect primero, luego marca online
        ref.onDisconnect().setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
        ref.setValue(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))
    }

    override suspend fun setPresenceOffline() {
        val me = auth.currentUser?.uid ?: return
        val ref = presenceRef(me)
        ref.setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
        // opcional: ref.onDisconnect().cancel()
    }
}

/* ======================= Helpers de conversión ======================= */

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
// FILE: app/src/main/java/com/example/tiendamascotas/data/repository/impl/FirestoreChatRepository.kt
package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.ChatThread
import com.example.tiendamascotas.domain.repository.UserPresence
import com.example.tiendamascotas.domain.repository.UserPublic
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.LinkedHashMap

class FirestoreChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ChatRepository {

    private val db = Firebase.firestore

    /* ======================== Users search ======================== */
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
            if (err != null) {
                trySend(emptyList()); return@addSnapshotListener
            }
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

    /* ==================== Conversation (messages) ==================== */
    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> = callbackFlow {
        val me = auth.currentUser?.uid
        if (me == null) {
            trySend(emptyList()); close(); return@callbackFlow
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
            // metadata (mi vista)
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
            // metadata (peer: +1 no leído)
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
            // espejo de mensaje
            b.set(myChat.collection(FirestorePaths.MESSAGES).document(newId), msgData)
            b.set(peerChat.collection(FirestorePaths.MESSAGES).document(newId), msgData)
        }.await()
        Unit
    }

    /* ========================= Users public ========================= */

    // Cache LRU (capacidad 200) para perfiles
    private val userCache = object : LinkedHashMap<String, UserPublic>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, UserPublic>?): Boolean {
            return size > 200
        }
    }
    private val userCacheLock = Any()

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

            synchronized(userCacheLock) {
                if (u != null) userCache[uid] = u else userCache.remove(uid)
            }
            trySend(u)
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    override fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>> = callbackFlow {
        if (uids.isEmpty()) { trySend(emptyMap()); close(); return@callbackFlow }

        // 1) Batch inicial con whereIn(documentId) en chunks de 10 (límite Firestore)
        val initialJob = launch {
            val initial = mutableMapOf<String, UserPublic>()
            val chunks = uids.toList().chunked(10)
            for (chunk in chunks) {
                val snap = db.collection(FirestorePaths.USERS)
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                for (d in snap.documents) {
                    if (d.exists()) {
                        val u = UserPublic(
                            uid = d.id,
                            displayName = d.getString(FirestorePaths.DISPLAY_NAME),
                            photoUrl = d.getString("photoUrl"),
                            email = d.getString("email")
                        )
                        synchronized(userCacheLock) { userCache[d.id] = u }
                        initial[d.id] = u
                    }
                }
            }
            trySend(initial)
        }

        // 2) Listeners individuales solo para el set (suscripción en vivo)
        val regs = mutableMapOf<String, ListenerRegistration>()
        uids.forEach { uid ->
            regs[uid] = db.collection(FirestorePaths.USERS).document(uid)
                .addSnapshotListener { snap, _ ->
                    val changed = if (snap != null && snap.exists()) {
                        UserPublic(
                            uid = snap.id,
                            displayName = snap.getString(FirestorePaths.DISPLAY_NAME),
                            photoUrl = snap.getString("photoUrl"),
                            email = snap.getString("email")
                        )
                    } else null

                    val out = synchronized(userCacheLock) {
                        if (changed != null) userCache[uid] = changed else userCache.remove(uid)
                        uids.mapNotNull { id -> userCache[id]?.let { id to it } }.toMap()
                    }
                    trySend(out)
                }
        }

        awaitClose {
            initialJob.cancel()
            regs.values.forEach { it.remove() }
        }
    }.distinctUntilChanged()

    /* ========================= Threads (enriquecidos) ========================= */

    override fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>> = channelFlow {
        val chatsRef = db.collection(FirestorePaths.USERS)
            .document(currentUid)
            .collection(FirestorePaths.CHATS)
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        var latestBase: List<ChatThread> = emptyList()
        var latestProfiles: Map<String, UserPublic> = emptyMap()
        var usersJob: Job? = null

        val reg = chatsRef.addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                trySend(emptyList()); return@addSnapshotListener
            }

            val base = snap.documents.map { d ->
                val peer = (d.getString("peerUid") ?: d.id).orEmpty()
                val last = d.getString("lastMessage")
                val updated = d.get("updatedAt").toMillisOrNull()
                val unread = d.getLong("unreadCount")?.toInt()
                ChatThread(
                    peerUid = peer,
                    lastMessage = last,
                    updatedAt = updated,
                    unreadCount = unread
                )
            }
            latestBase = base

            val peerSet = base.map { it.peerUid }.toSet()

            usersJob?.cancel()
            usersJob = launch {
                observeUsersPublic(peerSet).collectLatest { map ->
                    latestProfiles = map
                    val enriched = latestBase.map { t ->
                        val up = latestProfiles[t.peerUid]
                        t.copy(displayName = up?.displayName, photoUrl = up?.photoUrl)
                    }
                    trySend(enriched)
                }
            }

            // Emisión inicial con cache si hay
            val enriched = base.map { t ->
                val up = latestProfiles[t.peerUid]
                t.copy(displayName = up?.displayName, photoUrl = up?.photoUrl)
            }
            trySend(enriched)
        }

        awaitClose {
            reg.remove()
            usersJob?.cancel()
        }
    }.distinctUntilChanged()

    /* =================== Presence & Typing (stubs) =================== */
    override fun observePresence(uid: String): Flow<UserPresence> =
        flowOf(UserPresence(isOnline = false, lastSeen = null)) // TODO real

    override suspend fun setTyping(peerUid: String, typing: Boolean) { /* no-op */ }

    override fun observeTyping(peerUid: String) = flowOf(false)
}

/* ======================= Helpers de conversión ======================= */

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
