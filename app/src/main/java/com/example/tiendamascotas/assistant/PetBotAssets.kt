package com.example.tiendamascotas.assistant

import android.content.Context

object PetBotAssets {
    fun load(ctx: Context, name: String): String =
        ctx.assets.open("bot/$name").bufferedReader().use { it.readText() }

    fun systemPrompt(ctx: Context) = load(ctx, "bot/01_system_prompt_es.txt")
    fun responseTemplate(ctx: Context) = load(ctx, "bot/02_response_template.txt")
    fun intentClassifier(ctx: Context) = load(ctx, "bot/03_intent_classifier_prompt.txt")
    fun emergencyCheck(ctx: Context) = load(ctx, "bot/04_emergency_and_safety_prompt.txt")
    fun onboarding(ctx: Context) = load(ctx, "bot/06_onboarding_message.txt")
}
