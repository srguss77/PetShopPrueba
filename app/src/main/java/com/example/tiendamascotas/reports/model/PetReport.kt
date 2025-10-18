package com.example.tiendamascotas.reports.model

import com.google.firebase.Timestamp

data class PetReport(
    val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val photoUrl: String = "",  // URL de Cloudinary
    val raza: String = "",
    val edadAnios: Int = 0,
    val vacunas: String = "",   // texto libre o CSV
    val createdAt: Timestamp? = null
)
