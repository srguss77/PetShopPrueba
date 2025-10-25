package com.example.tiendamascotas.assistant

import com.google.ai.client.generativeai.GenerativeModel
import com.example.tiendamascotas.BuildConfig

object GeminiProvider {

    private const val FLASH = "gemini-2.0-flash"

    private const val PRO   = "gemini-2.0-flash"

    val model: GenerativeModel by lazy {
        GenerativeModel(
            modelName = FLASH,
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    fun fallback(): GenerativeModel = GenerativeModel(
        modelName = PRO,
        apiKey = BuildConfig.GEMINI_API_KEY
    )
}
