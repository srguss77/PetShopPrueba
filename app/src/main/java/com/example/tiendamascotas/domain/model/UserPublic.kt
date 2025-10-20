package com.example.tiendamascotas.domain.model

data class UserPublic(
    val uid: String,
    val name: String,
    val photoUrl: String? = null
)
