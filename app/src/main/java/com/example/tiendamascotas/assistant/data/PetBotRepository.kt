package com.example.tiendamascotas.assistant.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GoogleGenerativeAIException

class PetBotRepository(private var model: GenerativeModel) {

    suspend fun ask(prompt: String, fallbackModelProvider: (() -> GenerativeModel)? = null): Result<String> {

        val first = runCatching {
            val res = model.generateContent(prompt)
            res.text ?: "No pude generar una respuesta."
        }
        if (first.isSuccess) return first


        val cause = first.exceptionOrNull()
        val notFound = (cause as? GoogleGenerativeAIException)
            ?.message?.contains("NOT_FOUND", ignoreCase = true) == true

        if (notFound && fallbackModelProvider != null) {
            model = fallbackModelProvider()
            return runCatching {
                val res = model.generateContent(prompt)
                res.text ?: "No pude generar una respuesta."
            }
        }

        return first
    }
}
