@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.tiendamascotas.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

private const val AI_UID = "petshop_ai" // UID reservado para el bot

data class ChatThreadUi(
    val peerUid: String,
    val lastMessage: String,
    val unread: Int,
    val updatedAt: Long
)

data class UserUi(
    val uid: String,
    val displayName: String,
    val photoUrl: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGeneralScreen(nav: NavHostController) {
    val auth = remember { FirebaseAuth.getInstance() }
    val myUid = remember { auth.currentUser?.uid }
    val scope = rememberCoroutineScope()

    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<UserUi>()) }

    var threads by remember { mutableStateOf(listOf<ChatThreadUi>()) }
    var profiles by remember { mutableStateOf<Map<String, UserUi>>(emptyMap()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    if (myUid == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Chats") },
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                )
            }
        ) { padd ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padd),
                contentAlignment = Alignment.Center
            ) {
                Text("Inicia sesión para ver tus chats")
            }
        }
        return
    }

    // 👇 Utilidad local: lee millis sin crashear si no es Timestamp
    fun Any?.toMillis(): Long = when (this) {
        is com.google.firebase.Timestamp -> this.toDate().time
        is Number -> this.toLong()
        is Map<*, *> -> ((this["seconds"] as? Number)?.toLong() ?: 0L) * 1000
        else -> 0L
    }

    // Listener /users/{myUid}/chats (ordenado por updatedAt)
    DisposableEffect(myUid) {
        var reg: ListenerRegistration? = null
        reg = Firebase.firestore
            .collection("users")
            .document(myUid)
            .collection("chats")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    errorMsg = err.message
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener

                val mapped = snap.documents.map { d ->
                    val peer = (d.getString("peerUid") ?: d.id).orEmpty()
                    val last = d.getString("lastMessage").orEmpty()
                    val unread = (d.getLong("unreadCount") ?: 0L).toInt().coerceAtLeast(0)
                    val updated = d.get("updatedAt").toMillis() // 👈 seguro
                    ChatThreadUi(peer, last, unread, updated)
                }
                threads = mapped

                // Cargar perfiles faltantes
                scope.launch {
                    val current = profiles.toMutableMap()
                    mapped.forEach { t ->
                        if (!current.containsKey(t.peerUid)) {
                            runCatching {
                                val doc = Firebase.firestore.collection("users").document(t.peerUid).get().await()
                                if (doc.exists()) {
                                    current[t.peerUid] = UserUi(
                                        uid = t.peerUid,
                                        displayName = doc.getString("displayName") ?: "Usuario",
                                        photoUrl = doc.getString("photoUrl")
                                    )
                                } else {
                                    current[t.peerUid] = UserUi(t.peerUid, "Usuario", null)
                                }
                            }.onFailure {
                                current[t.peerUid] = UserUi(t.peerUid, "Usuario", null)
                            }
                        }
                    }
                    profiles = current
                }
            }
        onDispose { reg?.remove() }
    }

    // Búsqueda por prefijo sobre displayNameLower
    LaunchedEffect(showSearch, query, myUid) {
        if (!showSearch) { searchResults = emptyList(); return@LaunchedEffect }
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 2) { searchResults = emptyList(); return@LaunchedEffect }

        runCatching {
            val snap = Firebase.firestore.collection("users")
                .orderBy("displayNameLower")
                .startAt(q)
                .endAt(q + "\uf8ff")
                .limit(30)
                .get()
                .await()
            searchResults = snap.documents
                .filter { it.id != myUid && it.id != AI_UID }
                .map {
                    UserUi(
                        uid = it.id,
                        displayName = it.getString("displayName") ?: "Usuario",
                        photoUrl = it.getString("photoUrl")
                    )
                }
        }.onFailure { errorMsg = it.message }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar usuarios")
                    }
                }
            )
        }
    ) { padd ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padd)
        ) {
            if (errorMsg != null) {
                Text(
                    text = "Aviso: ${errorMsg}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (showSearch) {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClose = { showSearch = false; query = "" }
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(searchResults, key = { it.uid }) { user ->
                        UserRow(
                            user = user,
                            onClick = { nav.navigate("conversation/${user.uid}") }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chat fijo IA
                    item(key = "petshop-ia") {
                        ChatRowBase(
                            avatarUrl = null,
                            title = "PetShop IA 🤖",
                            subtitle = "Asistente virtual",
                            unread = 0,
                            updatedAt = null,
                            onClick = { nav.navigate("conversation/$AI_UID") }
                        )
                    }
                    // Solo hilos existentes
                    items(threads, key = { it.peerUid }) { t ->
                        val u = profiles[t.peerUid]
                        ChatRowBase(
                            avatarUrl = u?.photoUrl,
                            title = u?.displayName ?: "Usuario",
                            subtitle = t.lastMessage.ifBlank { "Empieza la conversación" },
                            unread = t.unread,
                            updatedAt = t.updatedAt.takeIf { it > 0L },
                            onClick = { nav.navigate("conversation/${t.peerUid}") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Buscar por nombre…") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClose) { Text("Cerrar") }
    }
}

@Composable
private fun UserRow(user: UserUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!user.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
            )
        } else {
            Icon(Icons.Default.Chat, contentDescription = "Avatar", modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text = user.displayName, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ChatRowBase(
    avatarUrl: String?,
    title: String,
    subtitle: String,
    unread: Int,
    updatedAt: Long?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            } else {
                Icon(Icons.Default.Chat, contentDescription = "Avatar", modifier = Modifier.size(46.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                updatedAt?.let { ts ->
                    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    Text(
                        text = runCatching { fmt.format(Date(ts)) }.getOrDefault(""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (unread > 0) {
                    BadgedBox(badge = { Badge { Text(unread.toString()) } }) {
                        Spacer(Modifier.size(1.dp)) // ancla invisible
                    }
                }
            }
        }
    }
}
