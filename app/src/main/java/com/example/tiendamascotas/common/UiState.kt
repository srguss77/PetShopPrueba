package com.example.tiendamascotas.ui.common

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val throwable: Throwable? = null, val message: String? = null) : UiState<Nothing>
}
