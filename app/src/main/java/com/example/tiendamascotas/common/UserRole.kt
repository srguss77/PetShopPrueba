package com.example.tiendamascotas.common.auth

import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

@Composable
fun rememberCurrentUid(): String? = remember { FirebaseAuth.getInstance().currentUser?.uid }

@Composable
fun rememberIsAdmin(): Boolean {
    val uid = rememberCurrentUid()
    var isAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        isAdmin = false
        if (uid == null) return@LaunchedEffect
        val snap = Firebase.firestore.collection("users").document(uid).get().await()
        isAdmin = snap.getString("role") == "admin"
    }
    return isAdmin
}
