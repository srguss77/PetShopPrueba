package com.example.tiendamascotas.assistant.data

data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String
) {
    enum class Role { USER, BOT, SYSTEM }
}
