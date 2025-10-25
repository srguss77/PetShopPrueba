// FILE: app/src/main/java/com/example/tiendamascotas/ServiceLocator.kt
package com.example.tiendamascotas

import com.example.tiendamascotas.assistant.AssistantService
import com.example.tiendamascotas.assistant.OkHttpAssistantService
import com.example.tiendamascotas.data.repository.impl.FirestoreUserRepository
import com.example.tiendamascotas.data.repository.impl.RtdbChatRepository
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Reports
import com.example.tiendamascotas.domain.repository.ReportsRepository as ReportsRepo
import com.example.tiendamascotas.reports.data.ReportsRepository as ReportsRepoImpl

// Adoptions
import com.example.tiendamascotas.domain.repository.AdoptionsRepository as AdoptionsRepo
import com.example.tiendamascotas.adoptions.data.AdoptionsRepositoryImpl as AdoptionsRepoImpl

object ServiceLocator {
    const val VET_BOT_UID = "__VET_BOT__"

    val users: UserRepository by lazy { FirestoreUserRepository() }

    val chat: ChatRepository by lazy {
        RtdbChatRepository(
            auth = FirebaseAuth.getInstance(),
            rtdb = FirebaseDatabase.getInstance(),
            firestore = FirebaseFirestore.getInstance(),
            userRepository = users
        )
    }

    val assistant: AssistantService by lazy {
        OkHttpAssistantService(BuildConfig.ASSISTANT_BASE_URL)
    }

    object auth {
        private val a = FirebaseAuth.getInstance()
        suspend fun signIn(email: String, pass: String): Result<Unit> = runCatching {
            a.signInWithEmailAndPassword(email, pass).await(); Unit
        }
        suspend fun signUp(email: String, pass: String): Result<Unit> = runCatching {
            a.createUserWithEmailAndPassword(email, pass).await(); Unit
        }
        fun signOut() = a.signOut()
    }

    // Reports
    val reports: ReportsRepo = ReportsRepoImpl()

    // Adoptions
    val adoptions: AdoptionsRepo = AdoptionsRepoImpl()
}
