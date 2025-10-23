// FILE: app/src/main/java/com/example/tiendamascotas/chat/ui/ChatGeneralScreen.kt
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.tiendamascotas.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

private const val AI_UID = "petshop_ai" // UID reservado para el bot

private enum class ChatSegment { Open, Closed }

data class ChatThreadUi(
    val peerUid: String,
    val lastMessage: String,
    val unread: Int,
    val updatedAt: Long,
    val closed: Boolean? // puede venir nulo si el campo no existe aún
)

data class UserUi(
    val uid: String,
    val displayName: String,
    val photoUrl: String?
)

/* ===================== Pantalla principal ===================== */

@Composable
fun ChatGeneralScreen(nav: NavHostController) {
    val myUid: String? = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()

    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchResults: List<UserUi> by remember { mutableStateOf(emptyList()) }

    var threads: List<ChatThreadUi> by remember { mutableStateOf(emptyList()) }
    var profiles: Map<String, UserUi> by remember { mutableStateOf(emptyMap()) }
    var errorMsg: String? by remember { mutableStateOf(null) }

    // ===== Estado local persistible para closed cuando el doc no lo tenga =====
    // Guardamos como String "uid:1|uid2:0" en rememberSaveable para máxima compatibilidad.
    var closedOverridesEncoded by rememberSaveable { mutableStateOf("") }
    val closedOverrides: Map<String, Boolean> by remember(closedOverridesEncoded) {
        mutableStateOf(decodeClosedMap(closedOverridesEncoded))
    }
    // TODO: Persistir el campo "closed" en Firestore cuando hoy no exista en el doc (estado local por ahora).

    // Segmento seleccionado (Open por defecto), sí saveable
    var segment by rememberSaveable { mutableStateOf(ChatSegment.Open) }

    // Estado de scroll recordado
    val listState: LazyListState = rememberLazyListState()

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

    fun Any?.toMillis(): Long = when (this) {
        is com.google.firebase.Timestamp -> this.toDate().time
        is Number -> this.toLong()
        is Map<*, *> -> ((this["seconds"] as? Number)?.toLong() ?: 0L) * 1000
        else -> 0L
    }

    // ==== Listener Firestore: /users/{myUid}/chats (ordenado por updatedAt) ====
    DisposableEffect(myUid) {
        var reg: ListenerRegistration? = null
        reg = Firebase.firestore
            .collection("users").document(myUid)
            .collection("chats")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    errorMsg = err.message
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener

                val mapped: List<ChatThreadUi> = snap.documents.map { d ->
                    val peer = (d.getString("peerUid") ?: d.id).orEmpty()
                    val last = d.getString("lastMessage").orEmpty()
                    val unread = (d.getLong("unreadCount") ?: 0L).toInt().coerceAtLeast(0)
                    val updated = d.get("updatedAt").toMillis()
                    val closedFromDoc = d.getBoolean("closed")
                    ChatThreadUi(peer, last, unread, updated, closedFromDoc)
                }
                threads = mapped

                // Cargar perfiles faltantes
                scope.launch {
                    val current = profiles.toMutableMap()
                    for (t in mapped) {
                        if (!current.containsKey(key = t.peerUid)) {
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
                    profiles = current.toMap()
                }
            }

        onDispose { reg?.remove() }
    }

    // ==== Búsqueda (se mantiene la existente) ====
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

            val list = buildList {
                for (d in snap.documents) {
                    val id = d.id
                    if (id != myUid && id != AI_UID) {
                        add(
                            UserUi(
                                uid = id,
                                displayName = d.getString("displayName") ?: "Usuario",
                                photoUrl = d.getString("photoUrl")
                            )
                        )
                    }
                }
            }
            searchResults = list
        }.onFailure { errorMsg = it.message }
    }

    // Helper: ¿está cerrado este hilo?
    fun isThreadClosed(t: ChatThreadUi): Boolean {
        return t.closed ?: closedOverrides[t.peerUid] ?: false
    }

    // Lista visible derivada (segmento Open/Closed; orden original se mantiene)
    val visibleThreads: List<ChatThreadUi> by remember(threads, segment, closedOverrides) {
        derivedStateOf {
            threads.filter { t ->
                val closed = isThreadClosed(t)
                when (segment) {
                    ChatSegment.Open -> !closed
                    ChatSegment.Closed -> closed
                }
            }
        }
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
            // Barra de segmentos Open / Closed
            SegmentedFilter(
                segment = segment,
                onSelect = { segment = it }
            )

            if (errorMsg != null) {
                Text(
                    text = "Aviso: $errorMsg",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                    contentPadding = PaddingValues(bottom = 16.dp),
                    state = listState
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
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    state = listState
                ) {
                    // Chat fijo IA
                    item(key = "petshop-ia") {
                        ChatRow(
                            avatarUrl = null,
                            title = "PetShop IA 🤖",
                            subtitle = "Asistente virtual",
                            unread = 0,
                            updatedAt = null,
                            onClick = { nav.navigate("conversation/$AI_UID") }
                        )
                    }

                    if (visibleThreads.isEmpty()) {
                        item(key = "empty-${segment.name}") {
                            EmptyState(
                                text = if (segment == ChatSegment.Open) "No chats Open" else "No chats Closed"
                            )
                        }
                    } else {
                        items(visibleThreads, key = { it.peerUid }) { t ->
                            val u: UserUi? = profiles[t.peerUid]
                            ChatRow(
                                avatarUrl = u?.photoUrl,
                                title = (u?.displayName ?: t.peerUid).ifBlank { t.peerUid },
                                subtitle = t.lastMessage.ifBlank { "—" },
                                unread = t.unread.coerceAtLeast(0),
                                updatedAt = t.updatedAt.takeIf { millis -> millis > 0L },
                                onClick = { nav.navigate("conversation/${t.peerUid}") }
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ===================== Componentes UI ===================== */

@Composable
private fun SegmentedFilter(
    segment: ChatSegment,
    onSelect: (ChatSegment) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        SegmentedButton(
            selected = segment == ChatSegment.Open,
            onClick = { onSelect(ChatSegment.Open) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("Open") }

        SegmentedButton(
            selected = segment == ChatSegment.Closed,
            onClick = { onSelect(ChatSegment.Closed) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("Closed") }
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
            label = { Text("Buscar usuarios…") },
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
private fun UserRow(
    user: UserUi,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp) // ≥48dp táctil
            .clickable(onClick = onClick),
        leadingContent = {
            Avatar(
                avatarUrl = user.photoUrl,
                fallbackIcon = Icons.Default.Chat,
                contentDesc = "Avatar de ${user.displayName}"
            )
        },
        headlineContent = {
            Text(
                text = user.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun ChatRow(
    avatarUrl: String?,
    title: String,
    subtitle: String,
    unread: Int,
    updatedAt: Long?,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp) // ≥48dp táctil
            .clickable(onClick = onClick),
        leadingContent = {
            Avatar(
                avatarUrl = avatarUrl,
                fallbackIcon = Icons.Default.Chat,
                contentDesc = "Avatar de $title"
            )
        },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = subtitle.ifBlank { "—" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val rel: String = updatedAt?.let { ts ->
                    // cálculo “puro UI” y barato
                    remember(ts) { relativeTimeShort(ts, System.currentTimeMillis()) }
                } ?: "—"
                Text(
                    text = rel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                if (unread > 0) {
                    BadgedBox(
                        badge = {
                            Badge {
                                Text(
                                    if (unread > 99) "99+" else unread.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    ) {
                        Spacer(Modifier.size(1.dp)) // ancla mínima para el BadgedBox
                    }
                }
            }
        }
    )
}

@Composable
private fun Avatar(
    avatarUrl: String?,
    fallbackIcon: ImageVector,
    contentDesc: String
) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = contentDesc,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .semantics { this.contentDescription = contentDesc }
        )
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDesc,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ===================== Helpers ===================== */

// Codificación simple para rememberSaveable
private fun decodeClosedMap(raw: String): Map<String, Boolean> {
    if (raw.isBlank()) return emptyMap()
    val pairs = raw.split("|")
    val map = HashMap<String, Boolean>(pairs.size)
    for (tok in pairs) {
        val idx = tok.indexOf(':')
        if (idx > 0 && idx < tok.lastIndex) {
            val key = tok.substring(0, idx)
            val v = tok.substring(idx + 1) == "1"
            map[key] = v
        }
    }
    return map
}

// Formato de hora relativa (corto), puramente UI.
private fun relativeTimeShort(tsMillis: Long, nowMillis: Long, locale: Locale = Locale.getDefault()): String {
    val diff = (nowMillis - tsMillis)
    val diffAbs = diff.absoluteValue

    val oneSecond = 1000L
    val oneMinute = 60 * oneSecond
    val oneHour = 60 * oneMinute
    val oneDay = 24 * oneHour

    if (diffAbs < oneMinute) return "Ahora"
    if (diffAbs < oneHour) {
        val m = (diffAbs / oneMinute).toInt()
        return "${m}m"
    }
    if (diffAbs < oneDay) {
        val h = (diffAbs / oneHour).toInt()
        return "${h}h"
    }

    val calNow = Calendar.getInstance(locale).apply { timeInMillis = nowMillis }
    val calTs = Calendar.getInstance(locale).apply { timeInMillis = tsMillis }
    val sameYear = calNow.get(Calendar.YEAR) == calTs.get(Calendar.YEAR)

    val isYesterday = sameYear &&
            calNow.get(Calendar.DAY_OF_YEAR) - calTs.get(Calendar.DAY_OF_YEAR) == 1
    if (isYesterday) return "Ayer"

    val pattern = if (sameYear) "dd/MM" else "dd/MM/yy"
    return runCatching { SimpleDateFormat(pattern, locale).format(Date(tsMillis)) }
        .getOrDefault("—")
}

/* ===================== Previews opcionales ===================== */

@androidx.compose.ui.tooling.preview.Preview(name = "Inbox Claro")
@Composable
private fun PreviewInboxLight() {
    MaterialTheme {
        Column {
            SegmentedFilter(ChatSegment.Open) {}
            ChatRow(
                avatarUrl = null,
                title = "María López",
                subtitle = "¿Listo para mañana?",
                unread = 3,
                updatedAt = System.currentTimeMillis() - 15 * 60 * 1000, // 15m
                onClick = {}
            )
            ChatRow(
                avatarUrl = null,
                title = "Carlos",
                subtitle = "—",
                unread = 120,
                updatedAt = System.currentTimeMillis() - 30 * 60 * 60 * 1000, // 30h
                onClick = {}
            )
            EmptyState("No chats Open")
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Inbox Oscuro")
@Composable
private fun PreviewInboxDark() {
    androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        PreviewInboxLight()
    }
}
