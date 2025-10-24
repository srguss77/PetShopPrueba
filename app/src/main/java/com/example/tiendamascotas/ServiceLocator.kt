package com.example.tiendamascotas

import com.example.tiendamascotas.domain.repository.AuthRepository
import com.example.tiendamascotas.domain.repository.ReportsRepository
import com.example.tiendamascotas.data.repository.impl.FirebaseAuthRepository
import com.example.tiendamascotas.data.repository.impl.FirestoreReportsRepository
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.UserRepository
import com.example.tiendamascotas.data.repository.impl.RtdbChatRepository
import com.example.tiendamascotas.data.repository.impl.FirestoreUserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

object ServiceLocator {
    val auth: AuthRepository by lazy { FirebaseAuthRepository() }
    val reports: ReportsRepository by lazy { FirestoreReportsRepository() }

    val users: UserRepository by lazy { FirestoreUserRepository() }

    val chat: ChatRepository by lazy {
        RtdbChatRepository(
            auth = FirebaseAuth.getInstance(),
            rtdb = FirebaseDatabase.getInstance(),
            firestore = FirebaseFirestore.getInstance(),
            userRepository = users
        )
    }
}
