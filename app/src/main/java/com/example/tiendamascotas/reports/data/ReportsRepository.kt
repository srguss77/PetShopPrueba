package com.example.tiendamascotas.reports.data

import com.example.tiendamascotas.reports.model.PetReport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

import com.example.tiendamascotas.domain.repository.ReportsRepository as ReportsRepo

class ReportsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ReportsRepo {

    private val col get() = db.collection("reports")

    override fun feed(): Flow<List<PetReport>> = callbackFlow {
        val reg = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(PetReport::class.java)?.copy(id = d.id)
                }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun get(id: String): PetReport? = runCatching {
        val d = col.document(id).get().await()
        if (!d.exists()) null
        else d.toObject(PetReport::class.java)?.copy(id = d.id)
    }.getOrNull()

    override suspend fun create(
        photoUrl: String,
        raza: String,
        edadAnios: Int,
        vacunas: String
    ): Result<String> = runCatching {
        val u = auth.currentUser ?: error("No autenticado")
        val data = hashMapOf(
            "ownerId" to u.uid,
            "ownerName" to (u.displayName ?: ""),
            "photoUrl" to photoUrl,
            "raza" to raza,
            "edadAnios" to edadAnios,
            "vacunas" to vacunas,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val ref = col.add(data).await()
        ref.id
    }

    override suspend fun update(id: String, fields: Map<String, Any?>): Result<Unit> = runCatching {
        col.document(id).update(fields).await()
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        col.document(id).delete().await()
    }
}
