package com.example.tiendamascotas

import com.example.tiendamascotas.domain.repository.AuthRepository
import com.example.tiendamascotas.domain.repository.ReportsRepository
import com.example.tiendamascotas.data.repository.impl.FirebaseAuthRepository
import com.example.tiendamascotas.data.repository.impl.FirestoreReportsRepository
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.google.firebase.database.FirebaseDatabase
import com.example.tiendamascotas.data.repository.impl.RtdbChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
object ServiceLocator {
    val auth: AuthRepository by lazy { FirebaseAuthRepository() }
    val reports: ReportsRepository by lazy { FirestoreReportsRepository() }
    val chat: ChatRepository by lazy {
        RtdbChatRepository(
            auth = FirebaseAuth.getInstance(),
            rtdb = FirebaseDatabase.getInstance(),
            firestore = FirebaseFirestore.getInstance() // para perfiles/users si lo necesitas
        )
    }
}