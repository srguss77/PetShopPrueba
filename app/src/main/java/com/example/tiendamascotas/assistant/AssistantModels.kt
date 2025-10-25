// FILE: app/src/main/java/com/example/tiendamascotas/assistant/AssistantModels.kt  (NUEVO)
package com.example.tiendamascotas.assistant

data class AssistantChatRequest(
    val userId: String,
    val message: String,
    val species: String? = null, // "dog" | "cat" | null
    val locale: String = "es-GT"
)

data class AssistantChatResponse(
    val reply: String,
    val sources: List<String>,
    val risk: String // "none" | "urgent"
)
