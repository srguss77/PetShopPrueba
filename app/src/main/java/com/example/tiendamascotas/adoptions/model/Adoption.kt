package com.example.tiendamascotas.adoptions.model

import com.google.firebase.Timestamp

data class Adoption(
    val id: String = "",

    val ownerId: String = "",
    val ownerName: String = "",

    val photoUrl: String = "",
    val nombre: String = "",
    val especie: String = "",
    val raza: String = "",
    val sexo: String = "",
    val edadAnios: Int = 0,

    val ubicacion: String = "",
    val razon: String = "",
    val salud: String = "",
    val vacunado: Boolean = false,
    val esterilizado: Boolean = false,
    val desparasitado: Boolean = false,
    val contacto: String = "",

    val createdAt: Timestamp? = null
)
