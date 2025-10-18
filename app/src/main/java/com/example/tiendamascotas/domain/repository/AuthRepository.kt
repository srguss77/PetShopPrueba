package com.example.tiendamascotas.domain.repository

interface AuthRepository {
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(email: String, password: String): Result<Unit>
    fun signOut()
    val currentUid: String?
    val currentEmail: String?
}
