package com.example.tiendamascotas.domain.repository

import com.example.tiendamascotas.reports.model.PetReport
import kotlinx.coroutines.flow.Flow

interface ReportsRepository {
    suspend fun create(photoUrl: String, raza: String, edadAnios: Int, vacunas: String): Result<String>
    suspend fun update(id: String, fields: Map<String, Any?>): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
    suspend fun get(id: String): PetReport?
    fun feed(): Flow<List<PetReport>>
}
