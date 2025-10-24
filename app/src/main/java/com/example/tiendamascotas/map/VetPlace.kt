package com.example.tiendamascotas.map

data class VetPlace(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val rating: Double? = null
)
