package com.example.tiendamascotas.data.repository.impl

import com.example.tiendamascotas.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await(); Unit
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        auth.createUserWithEmailAndPassword(email, password).await(); Unit
    }

    override fun signOut() { auth.signOut() }

    override val currentUid: String?
        get() = auth.currentUser?.uid

    override val currentEmail: String?
        get() = auth.currentUser?.email
}
