package com.example.tiendamascotas.reports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiendamascotas.data.repository.impl.FirestorePaths
import com.example.tiendamascotas.reports.data.ReportsRepository
import com.example.tiendamascotas.reports.model.PetReport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ReportsUiState(
    val loading: Boolean = true,
    val uid: String? = null,
    val role: String = "user",
    val items: List<PetReport> = emptyList(),
    val message: String? = null
)

class ReportsFeedViewModel(
    private val repo: ReportsRepository = ReportsRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _ui = MutableStateFlow(ReportsUiState())
    val ui: StateFlow<ReportsUiState> = _ui

    init {
        // carga rol + uid
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            var role = "user"
            if (uid != null) {
                runCatching {
                    val snap = Firebase.firestore
                        .collection(FirestorePaths.USERS) // "user"
                        .document(uid)
                        .get()
                        .await()
                    role = snap.getString("role") ?: "user"
                }
            }
            _ui.value = _ui.value.copy(uid = uid, role = role, loading = true)

            // feed en tiempo real
            repo.feed().onEach { list ->
                _ui.value = _ui.value.copy(loading = false, items = list)
            }.catch { e ->
                _ui.value = _ui.value.copy(loading = false, message = e.message)
            }.launchIn(this)
        }
    }

    fun canEdit(r: PetReport): Boolean {
        val s = _ui.value
        return (s.role.equals("admin", true) || (s.uid != null && r.ownerId == s.uid))
    }

    fun delete(reportId: String) = viewModelScope.launch {
        repo.delete(reportId).onFailure {
            _ui.value = _ui.value.copy(message = it.message ?: "No se pudo borrar")
        }
    }
}
