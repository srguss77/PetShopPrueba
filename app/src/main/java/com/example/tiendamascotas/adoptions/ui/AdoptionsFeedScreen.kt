package com.example.tiendamascotas.adoptions.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tiendamascotas.ServiceLocator
import com.example.tiendamascotas.adoptions.model.Adoption
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdoptionsFeedScreen(nav: NavHostController) {
    val repo = ServiceLocator.adoptions
    val scope = rememberCoroutineScope()
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    // ¿admin?
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(myUid) {
        if (myUid != null) {
            val doc = Firebase.firestore.collection("users").document(myUid).get().awaitOrNull()
            val role = doc?.getString("role") ?: ""
            isAdmin = role.equals("admin", ignoreCase = true)
        }
    }

    val items by repo.feed().collectAsState(initial = emptyList())

    Scaffold(
        topBar = { TopAppBar(title = { Text("Adopciones") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("adoptions/create") }) {
                Text("+")
            }
        }
    ) { inner ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Sin publicaciones todavía")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { a ->
                    AdoptionCard(
                        item = a,
                        canManage = (myUid != null && (myUid == a.ownerId || isAdmin)),
                        onDelete = {
                            scope.launch { repo.delete(a.id) }
                        },
                        onEdit = {
                            // ✅ Navega en modo edición con el id
                            nav.navigate("adoptions/create?editId=${a.id}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdoptionCard(
    item: Adoption,
    canManage: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(16.dp)) {
            if (item.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = item.photoUrl,
                    contentDescription = item.nombre,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                item.nombre.ifBlank { "Sin nombre" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            val meta = "Publicado ${timeAgo(item.createdAt)} · por ${item.ownerName.ifBlank { "Anónimo" }}"
            Text(meta, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))

            if (item.raza.isNotBlank()) Text("Raza: ${item.raza}")
            if (item.especie.isNotBlank()) Text("Especie: ${item.especie}")
            if (item.edadAnios > 0) Text("Años: ${item.edadAnios}")
            if (item.salud.isNotBlank()) Text("Salud: ${item.salud}")
            if (item.ubicacion.isNotBlank()) Text("Ubicación: ${item.ubicacion}")
            if (item.contacto.isNotBlank()) Text("Contacto: ${item.contacto}")

            val chips = listOfNotNull(
                if (item.vacunado) "Vacunado" else null,
                if (item.esterilizado) "Esterilizado" else null,
                if (item.desparasitado) "Desparasitado" else null
            ).joinToString(" · ")
            if (chips.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(chips, style = MaterialTheme.typography.bodySmall)
            }

            if (canManage) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                    }
                }
            }
        }
    }
}

private fun timeAgo(ts: Timestamp?): String {
    val now = System.currentTimeMillis()
    val t = ts?.toDate()?.time ?: now
    val diff = (now - t).coerceAtLeast(0)
    val min = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hrs = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        min < 1 -> "hace instantes"
        min < 60 -> "hace ${min} min"
        hrs < 24 -> "hace ${hrs} h"
        else -> "hace ${days} días"
    }
}

// Helper seguro para Tasks
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? =
    try { await() } catch (_: Throwable) { null }
