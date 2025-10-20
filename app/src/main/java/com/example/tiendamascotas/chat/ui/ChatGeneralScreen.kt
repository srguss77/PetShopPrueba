@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.tiendamascotas.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tiendamascotas.data.repository.impl.FirestorePaths
import com.example.tiendamascotas.domain.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGeneralScreen(nav: NavHostController) {

    // --- estado UI ---
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }

    // --- auth para ocultar el propio usuario ---
    val meUid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    // --- traer usuarios de Firestore (colección "users") ---
    LaunchedEffect(Unit) {
        Firebase.firestore.collection(FirestorePaths.USERS)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map { d ->
                    UserProfile(
                        uid = d.getString("uid") ?: d.id,
                        displayName = d.getString("displayName") ?: (d.getString("email") ?: "Usuario"),
                        email = d.getString("email") ?: "",
                        photoUrl = d.getString("photoUrl")
                    )
                }.orEmpty()
                users = list.filter { it.uid != meUid } // no mostrarse a sí mismo
            }
    }

    // --- filtro local por nombre o email ---
    val filtered = remember(query, users) {
        val q = query.trim().lowercase()
        if (q.isBlank()) users
        else users.filter {
            it.displayName.lowercase().contains(q) || it.email.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chat general") }) }
    ) { paddings ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddings)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Buscar") },
                placeholder = { Text("Buscar usuarios…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // ---- Ítem PINNED: PetShop IA ----
            ChatRow(
                title = "PetShop IA",
                subtitle = "Asistente de la app",
                avatar = {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = "IA",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    // Lo dejamos separado: usa un peerUid especial que luego
                    // trataremos distinto al implementar la IA.
                    nav.navigate("conversation/__IA__")
                }
            )
            Divider(Modifier.padding(vertical = 8.dp))

            // ---- Lista de usuarios (dinámica) ----
            if (filtered.isEmpty()) {
                Text(
                    "No se encontraron usuarios.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    filtered.forEach { u ->
                        ChatRow(
                            title = u.displayName.ifBlank { u.email },
                            subtitle = u.email,
                            avatar = {
                                if (u.photoUrl.isNullOrBlank()) {
                                    Icon(Icons.Filled.Person, contentDescription = "Usuario")
                                } else {
                                    AsyncImage(
                                        model = u.photoUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                }
                            },
                            onClick = {
                                // abre la conversación 1:1 con el usuario
                                nav.navigate("conversation/${u.uid}")
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    title: String,
    subtitle: String?,
    avatar: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            avatar()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
