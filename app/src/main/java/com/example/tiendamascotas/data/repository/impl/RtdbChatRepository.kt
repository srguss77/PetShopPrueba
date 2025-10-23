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
 * Chat sobre RTDB. Firestore se mantiene para /users (perfiles) y /reports.
 *
 * RTDB:
 *  - /threads/{uid}/{peerUid}  -> { peerUid, lastMessage, updatedAt(Long), unreadCount(Int), closed? }
 *  - /messages/{convId}/{id}   -> { fromUid, toUid, text, createdAt(Long) }
 *  - /presence/{uid}           -> { online:Boolean, lastSeen:Long }
 *  - /typing/{toUid}/{fromUid} -> true
 */
class RtdbChatRepository(
    private val auth: FirebaseAuth,
    private val rtdb: FirebaseDatabase,
    private val firestore: FirebaseFirestore
) : ChatRepository {

    // -------------------- helpers --------------------

    private fun myUid(): String =
        auth.currentUser?.uid ?: error("Auth user is null")

    private fun convId(a: String, b: String): String =
        listOf(a, b).sorted().joinToString("_")

    private fun DataSnapshot.longChild(name: String): Long? {
        return child(name).getValue(Long::class.java)
            ?: child(name).getValue(Double::class.java)?.toLong()
    }

    // -------------------- Threads (lista de chats) --------------------

    override fun observeThreadsFor(currentUid: String): Flow<List<ChatThread>> {
        val query = rtdb.getReference("threads")
            .child(currentUid)
            .orderByChild("updatedAt") // RTDB entrega ascendente

        return callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val out = mutableListOf<ChatThread>()
                    snapshot.children.forEach { node ->
                        val peerUid = node.key ?: return@forEach
                        val lastMessage = node.child("lastMessage").getValue(String::class.java)
                        val updatedAt = node.longChild("updatedAt")
                        val unread = (node.child("unreadCount").getValue(Long::class.java) ?: 0L).toInt()
                        out.add(
                            ChatThread(
                                peerUid = peerUid,
                                lastMessage = lastMessage,
                                updatedAt = updatedAt,
                                unreadCount = unread,
                                displayName = null,
                                photoUrl = null
                            )
                        )
                    }
                    out.sortByDescending { it.updatedAt ?: 0L }
                    trySend(out).isSuccess
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyList()).isSuccess
                }
            }
            query.addValueEventListener(listener)
            awaitClose { query.removeEventListener(listener) }
        }.distinctUntilChanged()
    }

    // -------------------- Conversación (mensajes) --------------------

    override fun observeConversation(peerUid: String): Flow<List<ChatMessage>> {
        val cid = convId(myUid(), peerUid)
        val query = rtdb.getReference("messages").child(cid).orderByChild("createdAt")

        return callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<ChatMessage>()
                    snapshot.children.forEach { msg ->
                        val id = msg.key ?: return@forEach
                        val from = msg.child("fromUid").getValue(String::class.java) ?: return@forEach
                        val text = msg.child("text").getValue(String::class.java) ?: ""
                        val createdAt = msg.longChild("createdAt") ?: 0L

                        // ChatMessage(id, fromUid, text, createdAt)  <-- sin toUid
                        list.add(
                            ChatMessage(
                                id = id,
                                fromUid = from,
                                text = text,
                                createdAt = createdAt
                            )
                        )
                    }
                    trySend(list).isSuccess
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyList()).isSuccess
                }
            }
            query.addValueEventListener(listener)
            awaitClose { query.removeEventListener(listener) }
        }.distinctUntilChanged()
    }

    override suspend fun sendText(peerUid: String, text: String): Result<Unit> = runCatching {
        val me = myUid()
        val cid = convId(me, peerUid)

        val msgsRef = rtdb.getReference("messages").child(cid)
        val newKey = msgsRef.push().key ?: error("push() returned null key")

        // Multi-path: crea mensaje y actualiza ambos threads
        val multi = hashMapOf<String, Any?>(
            "/messages/$cid/$newKey/fromUid" to me,
            "/messages/$cid/$newKey/toUid" to peerUid,
            "/messages/$cid/$newKey/text" to text,
            "/messages/$cid/$newKey/createdAt" to ServerValue.TIMESTAMP,

            "/threads/$me/$peerUid/peerUid" to peerUid,
            "/threads/$me/$peerUid/lastMessage" to text,
            "/threads/$me/$peerUid/updatedAt" to ServerValue.TIMESTAMP,

            "/threads/$peerUid/$me/peerUid" to me,
            "/threads/$peerUid/$me/lastMessage" to text,
            "/threads/$peerUid/$me/updatedAt" to ServerValue.TIMESTAMP
        )
        rtdb.reference.updateChildren(multi).await()

        // unreadCount del peer +1 (transacción)
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

    // -------------------- Búsqueda (usuarios) --------------------

    override fun observeUsers(query: String): Flow<List<ChatUser>> {
        // Placeholder para no romper. Si quieres, luego lo hago con Firestore (displayNameLower).
        return callbackFlow {
            trySend(emptyList<ChatUser>()).isSuccess
            awaitClose { }
        }
    }

    // -------------------- Perfiles públicos (Firestore) --------------------

    override fun observeUserPublic(uid: String): Flow<UserPublic?> {
        val doc = firestore.collection("users").document(uid)
        return callbackFlow {
            val reg = doc.addSnapshotListener { snap, _ ->
                val up = if (snap != null && snap.exists()) {
                    UserPublic(
                        uid = uid,
                        displayName = snap.getString("displayName"),
                        photoUrl = snap.getString("photoUrl"),
                        email = snap.getString("email")
                    )
                } else null
                trySend(up).isSuccess
            }
            awaitClose { reg.remove() }
        }.distinctUntilChanged()
    }

    override fun observeUsersPublic(uids: Set<String>): Flow<Map<String, UserPublic>> {
        if (uids.isEmpty()) return callbackFlow<Map<String, UserPublic>> {
            trySend(emptyMap()).isSuccess; awaitClose { }
        }

        return callbackFlow {
            val regs = mutableMapOf<String, ListenerRegistration>()
            val cache = mutableMapOf<String, UserPublic>()

            fun emit() { trySend(cache.toMap()).isSuccess }

            uids.forEach { uid ->
                val r = firestore.collection("users").document(uid)
                    .addSnapshotListener { snap, _ ->
                        if (snap != null && snap.exists()) {
                            cache[uid] = UserPublic(
                                uid = uid,
                                displayName = snap.getString("displayName"),
                                photoUrl = snap.getString("photoUrl"),
                                email = snap.getString("email")
                            )
                        } else {
                            cache.remove(uid)
                        }
                        emit()
                    }
                regs[uid] = r
            }

            awaitClose { regs.values.forEach { it.remove() } }
        }.distinctUntilChanged()
    }

    // -------------------- Presencia / Typing (RTDB) --------------------

    override fun observePresence(uid: String): Flow<UserPresence> {
        val ref = rtdb.getReference("presence").child(uid)
        return callbackFlow {
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val online = snapshot.child("online").getValue(Boolean::class.java) ?: snapshot.exists()
                    val lastSeen = snapshot.longChild("lastSeen")
                    trySend(UserPresence(isOnline = online, lastSeen = lastSeen)).isSuccess
                }
                override fun onCancelled(error: DatabaseError) {
                    trySend(UserPresence(isOnline = false, lastSeen = null)).isSuccess
                }
            }
            ref.addValueEventListener(l)
            awaitClose { ref.removeEventListener(l) }
        }.distinctUntilChanged()
    }

    override suspend fun setTyping(peerUid: String, typing: Boolean) {
        val me = myUid()
        val outRef = rtdb.getReference("typing").child(peerUid).child(me)
        if (typing) {
            outRef.onDisconnect().removeValue()
            outRef.setValue(true).await()
        } else {
            outRef.removeValue().await()
        }
    }

    override fun observeTyping(peerUid: String): Flow<Boolean> {
        val me = myUid()
        val inRef = rtdb.getReference("typing").child(me).child(peerUid)
        return callbackFlow {
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    trySend(snapshot.exists()).isSuccess
                }
                override fun onCancelled(error: DatabaseError) {
                    trySend(false).isSuccess
                }
            }
            inRef.addValueEventListener(l)
            awaitClose { inRef.removeEventListener(l) }
        }.distinctUntilChanged()
    }

    // -------------------- Hooks de presencia global --------------------

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
}
