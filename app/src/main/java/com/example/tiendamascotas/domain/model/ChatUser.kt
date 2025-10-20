package com.example.tiendamascotas.domain.model

data class ChatUser(
    val uid: String,
    val displayName: String? = null,
    val photoUrl: String? = null
)
