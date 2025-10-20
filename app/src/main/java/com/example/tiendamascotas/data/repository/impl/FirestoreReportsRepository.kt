package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.repository.ReportsRepository
import com.example.tiendamascotas.reports.model.PetReport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.example.tiendamascotas.data.repository.impl.FirestorePaths


class FirestoreReportsRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ReportsRepository {

    private val col = Firebase.firestore.collection(FirestorePaths.REPORTS)

    override suspend fun create(photoUrl: String, raza: String, edadAnios: Int, vacunas: String)
            : Result<String> = runCatching {
        val user = auth.currentUser ?: error("Sesión inválida")
        val ref = col.add(
            mapOf(
                "ownerId" to user.uid,
                "ownerName" to (user.displayName ?: user.email ?: "Usuario"),
                "photoUrl" to photoUrl,
                "raza" to raza,
                "edadAnios" to edadAnios,
                "vacunas" to vacunas,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        ref.id
    }

    override suspend fun update(id: String, fields: Map<String, Any?>): Result<Unit> =
        runCatching { col.document(id).update(fields).await(); Unit }

    override suspend fun delete(id: String): Result<Unit> =
        runCatching { col.document(id).delete().await(); Unit }

    override suspend fun get(id: String): PetReport? = runCatching {
        val d = col.document(id).get().await()
        if (d.exists()) d.toPetReport() else null
    }.getOrNull()

    override fun feed(): Flow<List<PetReport>> = callbackFlow {
        val reg = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { close(e); return@addSnapshotListener }
                val items = snap?.documents?.map { it.toPetReport() } ?: emptyList()
                trySend(items)
            }
        awaitClose { reg.remove() }
    }
}

private fun DocumentSnapshot.toPetReport(): PetReport =
    PetReport(
        id = id,
        ownerId = getString("ownerId") ?: "",
        ownerName = getString("ownerName") ?: "",
        photoUrl = getString("photoUrl") ?: "",
        raza = getString("raza") ?: "",
        edadAnios = getLong("edadAnios")?.toInt() ?: 0,
        vacunas = getString("vacunas") ?: "",
        createdAt = getTimestamp("createdAt")
    )
