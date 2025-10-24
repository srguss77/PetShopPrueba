// FILE: app/src/main/java/com/example/tiendamascotas/home/HomeScreen.kt
package com.example.tiendamascotas.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.tiendamascotas.navigation.Screen
import com.example.tiendamascotas.nav.Routes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val features = listOf(
        Feature("Reportar", Icons.Filled.Pets, Screen.CreateReport.route),
        // ✅ Mapa usa la constante de Routes (opción B)
        Feature("Mapa", Icons.Filled.Map, Routes.MAP),
        Feature("Asistente", Icons.Filled.Lightbulb, Screen.CareAssistant.route),
        Feature("Reseñas", Icons.Filled.RateReview, Screen.ReviewsHome.route),
        Feature("Chat", Icons.Filled.Chat, Screen.ChatGeneral.route),
        Feature("Adopciones", Icons.Filled.Favorite, Screen.AdoptionsList.route),
    )

    val snackbarHost = remember { SnackbarHostState() }
    var unreadTotal by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mascotas") },
                actions = {
                    BadgedBox(
                        badge = {
                            if (unreadTotal > 0) {
                                Badge { Text(if (unreadTotal > 99) "99+" else unreadTotal.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = { nav.navigate(Screen.NotificationsSettings.route) }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notificaciones")
                        }
                    }
                    IconButton(onClick = { nav.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Perfil")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { innerPadding ->

        NewMessageWatcher(
            nav = nav,
            snackbar = snackbarHost,
            onUnreadTotalChange = { unreadTotal = it }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(features) { f ->
                    val badgeCount = if (f.route == Screen.ChatGeneral.route) unreadTotal else 0
                    FeatureCard(
                        label = f.label,
                        icon = f.icon,
                        badgeCount = badgeCount,
                        onClick = { nav.navigate(f.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(Modifier.fillMaxSize()) {
            if (badgeCount > 0) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = label)
                Spacer(Modifier.height(10.dp))
                Text(text = label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private data class Feature(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

@Composable
private fun NewMessageWatcher(
    nav: NavHostController,
    snackbar: SnackbarHostState,
    onUnreadTotalChange: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    fun Any?.toMillis(): Long = when (this) {
        is com.google.firebase.Timestamp -> this.toDate().time
        is Number -> this.toLong()
        is Map<*, *> -> ((this["seconds"] as? Number)?.toLong() ?: 0L) * 1000
        else -> 0L
    }

    var isInitial by remember { mutableStateOf(true) }
    var lastKeyNotified by remember { mutableStateOf<String?>(null) }

    DisposableEffect(myUid) {
        var reg: ListenerRegistration? = null
        if (myUid != null) {
            reg = Firebase.firestore
                .collection("users")
                .document(myUid)
                .collection("chats")
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null) return@addSnapshotListener

                    val total = snap.documents.sumOf { (it.getLong("unreadCount") ?: 0L).toInt() }
                    onUnreadTotalChange(total)

                    if (isInitial) {
                        isInitial = false
                        return@addSnapshotListener
                    }

                    snap.documentChanges.forEach { dc ->
                        val unread = (dc.document.getLong("unreadCount") ?: 0L).toInt()
                        if (unread <= 0) return@forEach

                        val peerUid = dc.document.getString("peerUid") ?: dc.document.id
                        val preview = dc.document.getString("lastMessage") ?: "Mensaje nuevo"
                        val updatedAtMs = dc.document.get("updatedAt").toMillis()
                        val key = "$peerUid-$updatedAtMs-$unread"
                        if (key == lastKeyNotified) return@forEach
                        lastKeyNotified = key

                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = "Nuevo mensaje: $preview",
                                actionLabel = "Abrir",
                                withDismissAction = true,
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                // ✅ usa helper consistente
                                nav.navigate(Routes.conversation(peerUid))
                            }
                        }
                    }
                }
        }
        onDispose { reg?.remove() }
    }
}
