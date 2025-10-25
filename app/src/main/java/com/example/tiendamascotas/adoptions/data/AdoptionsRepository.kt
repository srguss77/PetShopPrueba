package com.example.tiendamascotas.adoptions.data

import com.example.tiendamascotas.adoptions.model.Adoption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AdoptionsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val col get() = db.collection("adoptions")

    fun feed(): Flow<List<Adoption>> = callbackFlow {
        val reg = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(Adoption::class.java)?.copy(id = d.id)
                }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun create(
        photoUrl: String,
        nombre: String,
        especie: String,
        raza: String,
        sexo: String,
        edadAnios: Int,
        ubicacion: String,
        razon: String,
        salud: String,
        vacunado: Boolean,
        esterilizado: Boolean,
        desparasitado: Boolean,
        contacto: String
    ): Result<String> = runCatching {
        val u = auth.currentUser ?: error("No autenticado")
        val data = hashMapOf(
            "ownerId" to u.uid,
            "ownerName" to (u.displayName ?: ""),
            "photoUrl" to photoUrl,
            "nombre" to nombre,
            "especie" to especie,
            "raza" to raza,
            "sexo" to sexo,
            "edadAnios" to edadAnios,
            "ubicacion" to ubicacion,
            "razon" to razon,
            "salud" to salud,
            "vacunado" to vacunado,
            "esterilizado" to esterilizado,
            "desparasitado" to desparasitado,
            "contacto" to contacto,
            "createdAt" to FieldValue.serverTimestamp()
        )
        col.add(data).await().id
    }

    suspend fun update(adoptionId: String, fields: Map<String, Any?>): Result<Unit> = runCatching {
        col.document(adoptionId).update(fields).await()
    }

    suspend fun delete(adoptionId: String): Result<Unit> = runCatching {
        col.document(adoptionId).delete().await()
    }
}
