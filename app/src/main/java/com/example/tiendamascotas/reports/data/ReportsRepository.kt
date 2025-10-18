package com.example.tiendamascotas.reports.data

import com.example.tiendamascotas.reports.model.PetReport
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ReportsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val col get() = db.collection("reports")

    fun feed(): Flow<List<PetReport>> = callbackFlow {
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

    suspend fun create(
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

    suspend fun update(reportId: String, fields: Map<String, Any?>): Result<Unit> = runCatching {
        col.document(reportId).update(fields).await()
    }

    suspend fun delete(reportId: String): Result<Unit> = runCatching {
        col.document(reportId).delete().await()
    }
}
