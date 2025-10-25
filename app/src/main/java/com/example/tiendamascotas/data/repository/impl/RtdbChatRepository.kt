// FILE: app/src/main/java/com/example/tiendamascotas/data/repository/impl/RtdbChatRepository.kt  (REEMPLAZA COMPLETO)
package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.model.ChatUser
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.ChatThread
import com.example.tiendamascotas.domain.repository.UserPresence
import com.example.tiendamascotas.domain.repository.UserPublic
import com.example.tiendamascotas.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * Chat sobre RTDB. Firestore se mantiene para /users (perfiles).
 *
 * RTDB:
 *  - /threads/{uid}/{peerUid}  -> { peerUid, lastMessage, updatedAt(Long), unreadCount(Int) }
 *  - /messages/{convId}/{id}   -> { fromUid, toUid, text, createdAt(Long) }
 *  - /presence/{uid}           -> { online:Boolean, lastSeen:Long }
 *  - /typing/{toUid}/{fromUid} -> true
 */
class RtdbChatRepository(
    private val auth: FirebaseAuth,
    private val rtdb: FirebaseDatabase,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) : ChatRepository {

    private fun myUid(): String =
        auth.currentUser?.uid ?: error("Auth user is null")

    private fun convId(a: String, b: String): String =
        listOf(a, b).sorted().joinToString("_")

    private fun DataSnapshot.longChild(name: String): Long? =
        child(name).getValue(Long::class.java) ?: child(name).getValue(Double::class.java)?.toLong()

    /* =================== Threads =================== */
    override fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>> {
        val query = rtdb.getReference("threads").child(currentUid).orderByChild("updatedAt")
        return callbackFlow {
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val out = mutableListOf<ChatThread>()
                    s.children.forEach { node ->
                        val peer = node.key ?: return@forEach
                        val last = node.child("lastMessage").getValue(String::class.java)
                        val updated = node.longChild("updatedAt")
                        val unread = (node.child("unreadCount").getValue(Long::class.java) ?: 0L).toInt()
                        out.add(ChatThread(peerUid = peer, lastMessage = last, updatedAt = updated, unreadCount = unread))
                    }
                    out.sortByDescending { it.updatedAt ?: 0L }
                    trySend(out).isSuccess
                }
                override fun onCancelled(error: DatabaseError) { trySend(emptyList()).isSuccess }
            }
            query.addValueEventListener(l)
            awaitClose { query.removeEventListener(l) }
        }.distinctUntilChanged()
    }

    /* =================== Conversation =================== */
    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> {
        val cid = convId(myUid(), peerUid)
        val query = rtdb.getReference("messages").child(cid).orderByChild("createdAt")
        return callbackFlow {
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val list = mutableListOf<ChatMessage>()
                    s.children.forEach { msg ->
                        val id = msg.key ?: return@forEach
                        val from = msg.child("fromUid").getValue(String::class.java) ?: return@forEach
                        val text = msg.child("text").getValue(String::class.java) ?: ""
                        val createdAt = msg.longChild("createdAt") ?: 0L
                        list.add(ChatMessage(id = id, fromUid = from, text = text, createdAt = createdAt))
                    }
                    trySend(list).isSuccess
                }
                override fun onCancelled(error: DatabaseError) { trySend(emptyList()).isSuccess }
            }
            query.addValueEventListener(l)
            awaitClose { query.removeEventListener(l) }
        }.distinctUntilChanged()
    }

    override suspend fun sendText(peerUid: String, text: String): Result<Unit> = runCatching {
        val me = myUid()
        val cid = convId(me, peerUid)
        val msgsRef = rtdb.getReference("messages").child(cid)
        the@ run {
            val newKey = msgsRef.push().key ?: error("push() returned null key")

            val trimmed = text.trim()

            val multi = hashMapOf<String, Any?>(
                "/messages/$cid/$newKey/fromUid" to me,
                "/messages/$cid/$newKey/toUid" to peerUid,
                "/messages/$cid/$newKey/text" to trimmed,
                "/messages/$cid/$newKey/createdAt" to ServerValue.TIMESTAMP,

                "/threads/$me/$peerUid/peerUid" to peerUid,
                "/threads/$me/$peerUid/lastMessage" to trimmed,
                "/threads/$me/$peerUid/updatedAt" to ServerValue.TIMESTAMP,

                "/threads/$peerUid/$me/peerUid" to me,
                "/threads/$peerUid/$me/lastMessage" to trimmed,
                "/threads/$peerUid/$me/updatedAt" to ServerValue.TIMESTAMP
            )
            rtdb.reference.updateChildren(multi).await()
        }

        // unreadCount++ del peer
        val unreadRef = rtdb.getReference("threads").child(peerUid).child(me).child("unreadCount")
        unreadRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = (currentData.getValue(Int::class.java) ?: 0)
                currentData.value = current + 1
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    // ✅ NUEVO: insertar mensaje entrante (para respuestas del bot u otros sistemas)
    suspend fun receiveText(fromUid: String, toUid: String, text: String) {
        val cid = convId(fromUid, toUid)
        val msgsRef = rtdb.getReference("messages").child(cid)
        val newKey = msgsRef.push().key ?: error("push() returned null key")

        val trimmed = text.trim()

        val multi = hashMapOf<String, Any?>(
            "/messages/$cid/$newKey/fromUid" to fromUid,
            "/messages/$cid/$newKey/toUid" to toUid,
            "/messages/$cid/$newKey/text" to trimmed,
            "/messages/$cid/$newKey/createdAt" to ServerValue.TIMESTAMP,

            "/threads/$toUid/$fromUid/peerUid" to fromUid,
            "/threads/$toUid/$fromUid/lastMessage" to trimmed,
            "/threads/$toUid/$fromUid/updatedAt" to ServerValue.TIMESTAMP
            // Si quisieras contarlo como no leído:
            // "/threads/$toUid/$fromUid/unreadCount" to ServerValue.increment(1)
        )

        rtdb.reference.updateChildren(multi).await()
    }

    override suspend fun markThreadRead(peerUid: String) {
        val me = myUid()
        rtdb.getReference("threads").child(me).child(peerUid).child("unreadCount")
            .setValue(0).await()
    }

    /* =================== Search users (no usado aquí) =================== */
    override fun observeUsers(query: String) = callbackFlow {
        trySend(emptyList<ChatUser>()).isSuccess
        awaitClose { }
    }

    /* =================== Users public (Firestore direct) =================== */
    override fun observeUserPublic(uid: String): Flow<UserPublic?> = callbackFlow {
        if (uid.isBlank()) { trySend(null); close(); return@callbackFlow }
        val doc = firestore.collection(FirestorePaths.USERS).document(uid)
        val reg = doc.addSnapshotListener { snap, _ ->
            val u = if (snap != null && snap.exists()) {
                UserPublic(
                    uid = snap.id,
                    displayName = snap.getString(FirestorePaths.DISPLAY_NAME),
                    photoUrl = snap.getString("photoUrl"),
                    email = snap.getString("email")
                )
            } else null
            trySend(u).isSuccess
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    override fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>> = callbackFlow {
        if (uids.isEmpty()) { trySend(emptyMap()); close(); return@callbackFlow }
        val regs = mutableMapOf<String, ListenerRegistration>()
        val cache = mutableMapOf<String, UserPublic>()
        uids.forEach { uid ->
            val r = firestore.collection(FirestorePaths.USERS).document(uid)
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
                    trySend(cache.toMap()).isSuccess
                }
            regs[uid] = r
        }
        awaitClose { regs.values.forEach { it.remove() } }
    }.distinctUntilChanged()

    /* =================== Presencia & Typing (RTDB) =================== */
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
        val me = myUid()
        val out = rtdb.getReference("typing").child(peerUid).child(me)
        if (typing) {
            out.onDisconnect().removeValue()
            out.setValue(true).await()
        } else {
            out.removeValue().await()
        }
    }

    override fun observeTyping(peerUid: String): Flow<Boolean> {
        val me = myUid()
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
        val me = myUid()
        val ref = rtdb.getReference("presence").child(me)
        ref.onDisconnect().setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
        ref.setValue(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP)).await()
    }

    override suspend fun setPresenceOffline() {
        val me = myUid()
        val ref = rtdb.getReference("presence").child(me)
        ref.setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)).await()
    }

    // (PRONT 10) Si alguien lo invoca, devolvemos vacío (no usado en esta rama)
    override fun observeChatRows(currentUid: String) =
        callbackFlow<List<ChatRepository.ChatRow>> { trySend(emptyList()); awaitClose { } }
}
