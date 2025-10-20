package com.example.tiendamascotas

import com.example.tiendamascotas.domain.repository.AuthRepository
import com.example.tiendamascotas.domain.repository.ReportsRepository
import com.example.tiendamascotas.data.repository.impl.FirebaseAuthRepository
import com.example.tiendamascotas.data.repository.impl.FirestoreReportsRepository
import com.example.tiendamascotas.data.repository.impl.FirestoreChatRepository
import com.example.tiendamascotas.domain.repository.ChatRepository
object ServiceLocator {
    val auth: AuthRepository by lazy { FirebaseAuthRepository() }
    val reports: ReportsRepository by lazy { FirestoreReportsRepository() }
    val chat: ChatRepository by lazy { FirestoreChatRepository() }
    }