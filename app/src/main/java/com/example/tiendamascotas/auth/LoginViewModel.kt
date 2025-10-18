package com.example.tiendamascotas.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiendamascotas.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

class LoginViewModel : ViewModel() {
    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui

    fun onEmail(v: String) { _ui.value = _ui.value.copy(email = v) }
    fun onPassword(v: String) { _ui.value = _ui.value.copy(password = v) }

    fun signIn(onSuccess: () -> Unit) = authAction(onSuccess) { email, pass ->
        ServiceLocator.auth.signIn(email, pass)
    }
    fun signUp(onSuccess: () -> Unit) = authAction(onSuccess) { email, pass ->
        ServiceLocator.auth.signUp(email, pass)
    }

    private fun authAction(onSuccess: () -> Unit, block: suspend (String, String) -> Result<Unit>) {
        val s = _ui.value
        if (s.email.isBlank() || s.password.length < 6) {
            _ui.value = s.copy(error = "Correo/contraseña inválidos (min 6)")
            return
        }
        _ui.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = block(s.email.trim(), s.password)
            _ui.value = _ui.value.copy(loading = false)
            r.onSuccess { onSuccess() }
                .onFailure { _ui.value = _ui.value.copy(error = it.message ?: "Error") }
        }
    }
}
