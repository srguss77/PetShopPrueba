// FILE: app/src/main/java/com/example/tiendamascotas/domain/repository/AdoptionsRepository.kt
package com.example.tiendamascotas.domain.repository

import com.example.tiendamascotas.adoptions.model.Adoption
import kotlinx.coroutines.flow.Flow

interface AdoptionsRepository {
    fun feed(): Flow<List<Adoption>>

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
    ): Result<String>

    suspend fun delete(adoptionId: String): Result<Unit>
}
