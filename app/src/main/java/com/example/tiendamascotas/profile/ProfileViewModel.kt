package com.example.tiendamascotas.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiendamascotas.ServiceLocator
import com.example.tiendamascotas.domain.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.tiendamascotas.profile.ProfileViewModel


data class ProfileUiState(
    val loading: Boolean = true,
    val email: String = "",
    val displayName: String = "",
    val role: String = "user",
    val photoUrl: String? = null,
    val message: String? = null
)

class ProfileViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui

    init { load() }

    private fun load() = viewModelScope.launch {
        val user = auth.currentUser
        if (user == null) {
            _ui.value = ProfileUiState(loading = false, message = "Sin sesión")
            return@launch
        }

        var state = ProfileUiState(
            loading = true,
            email = user.email.orEmpty(),
            displayName = user.displayName.orEmpty(),
            photoUrl = user.photoUrl?.toString()
        )
        _ui.value = state

        val docRef = db.collection("users").document(user.uid)
        val snap = docRef.get().await() // requiere kotlinx-coroutines-play-services
        if (snap.exists()) {
            val displayName = snap.getString("displayName") ?: state.displayName
            val role = snap.getString("role") ?: "user"
            state = state.copy(displayName = displayName, role = role, loading = false)
            _ui.value = state
        } else {
            val profile = UserProfile(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = state.displayName,
                role = "user",
                photoUrl = user.photoUrl?.toString()
            )
            docRef.set(profile).await()
            _ui.value = state.copy(loading = false)
        }
    }

    fun onNameChange(v: String) {
        _ui.value = _ui.value.copy(displayName = v.take(40))
    }

    fun saveName() = viewModelScope.launch {
        val user = auth.currentUser ?: return@launch
        val name = _ui.value.displayName.trim()

        // Guarda el nombre en Firestore
        db.collection("users").document(user.uid)
            .update(mapOf("displayName" to name))
            .await()

        // Actualiza displayName en Auth sin KTX (evita userProfileChangeRequest)
        val req = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()
        runCatching { user.updateProfile(req).await() }

        _ui.value = _ui.value.copy(message = "Nombre actualizado")
    }

    fun signOut(onSignedOut: () -> Unit) = viewModelScope.launch {
        ServiceLocator.auth.signOut()
        onSignedOut()
    }
}
