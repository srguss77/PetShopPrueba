package com.example.tiendamascotas.domain.model

data class ChatMessage(
    val id: String,
    val peerUid: String,   // con quién hablo
    val fromMe: Boolean,   // lo envié yo
    val text: String,
    val timestamp: Long
)
