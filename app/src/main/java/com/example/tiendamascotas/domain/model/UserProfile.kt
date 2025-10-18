package com.example.tiendamascotas.domain.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "user",    // "admin" | "user"
    val photoUrl: String? = null
)
