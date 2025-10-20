package com.example.tiendamascotas.chat.model

data class ChatMessage(
    val id: String = "",
    val fromUid: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)
