package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.repository.UserPublic
import com.example.tiendamascotas.domain.repository.UserRepository
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class FirestoreUserRepository : UserRepository {
    private val db = Firebase.firestore

    override fun observeUserDirectory(excludeUid: String): Flow<List<UserPublic>> = callbackFlow {
        val ref = db.collection(FirestorePaths.USERS)
            .orderBy(FirestorePaths.DISPLAY_NAME_LOWER, Query.Direction.ASCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                trySend(emptyList()); return@addSnapshotListener
            }
            val list = snap.documents.mapNotNull { d ->
                val uid = d.getString("uid") ?: d.id
                if (uid == excludeUid) return@mapNotNull null
                UserPublic(
                    uid = uid,
                    displayName = d.getString(FirestorePaths.DISPLAY_NAME),
                    photoUrl = d.getString("photoUrl"),
                    email = d.getString("email")
                )
            }
            trySend(list)
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    override fun observeUserPublic(uid: String): Flow<UserPublic?> = callbackFlow {
        val doc = db.collection(FirestorePaths.USERS).document(uid)
        val reg = doc.addSnapshotListener { d, _ ->
            val up = if (d != null && d.exists()) {
                UserPublic(
                    uid = d.id,
                    displayName = d.getString(FirestorePaths.DISPLAY_NAME),
                    photoUrl = d.getString("photoUrl"),
                    email = d.getString("email")
                )
            } else null
            trySend(up)
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    override fun searchUsersPrefix(queryLower: String, limit: Long): Flow<List<UserPublic>> =
        callbackFlow {
            val q = queryLower.trim().lowercase()
            if (q.length < 2) {
                trySend(emptyList()); close(); return@callbackFlow
            }

            val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val col = db.collection(FirestorePaths.USERS)

            // Vamos a mantener dos listas y fusionarlas:
            var listLower: List<UserPublic> = emptyList()
            var listNamePrefix: List<UserPublic> = emptyList()

            fun emit() {
                // Merge + dedupe + orden alfabético por nombre
                val merged = (listLower + listNamePrefix)
                    .distinctBy { it.uid }
                    .sortedBy { (it.displayName ?: it.uid).lowercase() }
                    .let { lst -> if (me == null) lst else lst.filter { it.uid != me } }
                    .take(limit.toInt())

                trySend(merged).isSuccess
            }

            // 1) Búsqueda rápida por displayNameLower (prefijo)
            val regLower = col
                .orderBy(FirestorePaths.DISPLAY_NAME_LOWER)
                .startAt(q)
                .endAt(q + "\uf8ff")
                .limit(limit)
                .addSnapshotListener { snap, _ ->
                    listLower = snap?.documents?.map { d ->
                        UserPublic(
                            uid = d.getString("uid") ?: d.id,
                            displayName = d.getString(FirestorePaths.DISPLAY_NAME),
                            photoUrl = d.getString("photoUrl"),
                            email = d.getString("email")
                        )
                    } ?: emptyList()
                    emit()
                }

            // 2) Fallback: si faltara displayNameLower en algunos docs, traemos un batch por displayName
            //    y filtramos en cliente por prefijo case-insensitive.
            val regName = col
                .orderBy(FirestorePaths.DISPLAY_NAME) // sensible a mayúsculas, por eso filtramos local
                .limit(100)
                .addSnapshotListener { snap, _ ->
                    val all = snap?.documents?.map { d ->
                        UserPublic(
                            uid = d.getString("uid") ?: d.id,
                            displayName = d.getString(FirestorePaths.DISPLAY_NAME),
                            photoUrl = d.getString("photoUrl"),
                            email = d.getString("email")
                        )
                    } ?: emptyList()

                    listNamePrefix = all.filter { up ->
                        (up.displayName ?: up.uid).lowercase().startsWith(q)
                    }
                    emit()
                }

            awaitClose {
                regLower.remove()
                regName.remove()
            }
        }.distinctUntilChanged()
}
