package com.example.tiendamascotas.domain.model

data class Pet(
    val id: String,
    val type: String,   // "dog", "cat", etc.
    val name: String? = null
)