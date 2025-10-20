package com.example.tiendamascotas.domain.model

data class ChatMessage(
    val id: String,
    val fromUid: String,
    val text: String,
    val createdAt: Long
)
