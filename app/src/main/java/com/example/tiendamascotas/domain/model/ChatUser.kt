package com.example.tiendamascotas.domain.model

data class ChatUser(
    val uid: String,
    val displayName: String,
    val photoUrl: String? = null,
    val email: String? = null
)
