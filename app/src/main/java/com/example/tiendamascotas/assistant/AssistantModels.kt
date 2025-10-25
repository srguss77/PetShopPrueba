package com.example.tiendamascotas.assistant

data class AssistantChatRequest(
    val userId: String,
    val message: String,
    val species: String? = null,
    val locale: String = "es-GT"
)

data class AssistantChatResponse(
    val reply: String,
    val sources: List<String>,
    val risk: String
)
