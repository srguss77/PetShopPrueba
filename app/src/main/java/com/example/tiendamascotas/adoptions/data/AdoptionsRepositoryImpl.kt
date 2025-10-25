// FILE: app/src/main/java/com/example/tiendamascotas/adoptions/data/AdoptionsRepositoryImpl.kt
package com.example.tiendamascotas.adoptions.data

import com.example.tiendamascotas.adoptions.model.Adoption
import com.example.tiendamascotas.domain.repository.AdoptionsRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AdoptionsRepositoryImpl(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AdoptionsRepository {

    private val col get() = db.collection("adoption")

    override fun feed(): Flow<List<Adoption>> = callbackFlow {
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

    override suspend fun create(
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
        val ref = col.add(data).await()
        ref.id
    }

    override suspend fun delete(adoptionId: String): Result<Unit> = runCatching {
        col.document(adoptionId).delete().await()
    }
}
