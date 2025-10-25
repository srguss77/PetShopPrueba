@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.tiendamascotas.chat.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tiendamascotas.ServiceLocator
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.ChatThread
import com.example.tiendamascotas.domain.repository.UserPublic
import com.example.tiendamascotas.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val ChatListBlue = Color(0xFFF0F4FF)

private val HiddenUids = setOf("petshop_ai", "__IA__")

@Composable
fun ChatGeneralScreen(
    nav: NavHostController,
    chatRepo: ChatRepository = ServiceLocator.chat,
    userRepo: UserRepository = ServiceLocator.users,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val me = auth.currentUser?.uid
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var showSearch by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }

    val threads: List<ChatThread> by remember(me) {
        if (me == null) emptyFlow<List<ChatThread>>()
        else chatRepo.observeThreadsFor(me).distinctUntilChanged()
    }.collectAsState(initial = emptyList())

    val peerSet = remember(threads) { threads.map { it.peerUid }.toSet() }
    val profilesFlow: Flow<Map<String, UserPublic>> = remember(peerSet) {
        if (peerSet.isEmpty()) emptyFlow() else {
            combine(
                peerSet.map { uid -> userRepo.observeUserPublic(uid).map { up -> uid to up } }
            ) { pairs ->
                pairs.mapNotNull { (uid, up) -> up?.let { uid to it } }.toMap()
            }
        }
    }
    val profiles by profilesFlow.collectAsState(initial = emptyMap())

    val enrichedThreads = remember(threads, profiles) {
        threads
            .filter { it.peerUid !in HiddenUids }
            .map { t ->
                val up = profiles[t.peerUid]
                EnrichedThread(
                    peerUid = t.peerUid,
                    displayName = up?.displayName ?: t.displayName,
                    photoUrl = up?.photoUrl ?: t.photoUrl,
                    lastMessage = t.lastMessage,
                    updatedAt = t.updatedAt,
                    unreadCount = (t.unreadCount ?: 0).coerceAtLeast(0)
                )
            }
    }

    val userResultsRaw by remember(query) {
        val q = query.trim().lowercase()
        if (q.length >= 2) userRepo.searchUsersPrefix(q, limit = 50) else emptyFlow()
    }.collectAsState(initial = emptyList())
    val userResults = remember(userResultsRaw, me) {
        userResultsRaw.filter { it.uid !in HiddenUids && it.uid != me }
    }

    LaunchedEffect(me) {
        if (me == null) scope.launch { snackbar.showSnackbar("Debes iniciar sesión para ver tus chats") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { paddings ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ChatListBlue)
                .padding(paddings)
        ) {
            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Buscar usuarios…") }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Hilos
                if (enrichedThreads.isEmpty()) {
                    item(key = "empty-threads") { EmptyState("Aún no tienes conversaciones") }
                } else {
                    itemsIndexed(
                        items = enrichedThreads.distinctBy { it.peerUid },
                        key = { _, t -> "th-${t.peerUid}" }
                    ) { index, t ->
                        ChatRow(
                            title = (t.displayName ?: t.peerUid).ifBlank { t.peerUid },
                            subtitle = t.lastMessage ?: "Inicia una conversación",
                            avatarUrl = t.photoUrl,
                            updatedAt = t.updatedAt,
                            unread = t.unreadCount,
                            onClick = { nav.navigate("conversation/${t.peerUid}") }
                        )
                        if (index < enrichedThreads.lastIndex) DividerIndent()
                    }
                }

                item(key = "hdr-users") { SectionHeader("Usuarios") }

                if (query.trim().length >= 2) {
                    if (userResults.isEmpty()) {
                        item(key = "users-empty") {
                            Text(
                                "Sin resultados",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = userResults.distinctBy { it.uid },
                            key = { _, u -> "ud-${u.uid}" }
                        ) { index, u ->
                            UserRow(
                                title = u.displayName ?: u.uid,
                                avatarUrl = u.photoUrl,
                                onClick = {
                                    // 🔹 Limpia búsqueda y cierra teclado al abrir conversación
                                    focusManager.clearFocus()
                                    query = ""
                                    nav.navigate("conversation/${u.uid}")
                                }
                            )
                            if (index < userResults.lastIndex) DividerIndent()
                        }
                    }
                }
            }
        }
    }
}


private data class EnrichedThread(
    val peerUid: String,
    val displayName: String?,
    val photoUrl: String?,
    val lastMessage: String?,
    val updatedAt: Long?,
    val unreadCount: Int
)

@Composable
private fun ChatRow(
    title: String,
    subtitle: String,
    avatarUrl: String?,
    updatedAt: Long?,
    unread: Int,
    onClick: () -> Unit
) {
    val rel = remember(updatedAt) { formatRelativeTime(updatedAt) }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        leadingContent = { Avatar(avatarUrl = avatarUrl, contentDesc = "Avatar de $title") },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = rel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unread > 0) {
                    Spacer(Modifier.height(6.dp))
                    val badgeText = if (unread > 99) "99+" else unread.toString()
                    BadgedBox(badge = { Badge { Text(badgeText) } }) { Spacer(Modifier.size(1.dp)) }
                }
            }
        }
    )
}

@Composable
private fun UserRow(
    title: String,
    avatarUrl: String?,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        leadingContent = { Avatar(avatarUrl = avatarUrl, contentDesc = "Avatar de $title") },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun Avatar(avatarUrl: String?, contentDesc: String) {
    val context = LocalContext.current
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(avatarUrl)
                .crossfade(true)
                .size(64)
                .build(),
            contentDescription = contentDesc,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = contentDesc,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DividerIndent() {
    Divider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 80.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 1.dp
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EmptyState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatRelativeTime(ts: Long?, now: Long = System.currentTimeMillis()): String {
    if (ts == null || ts <= 0) return "—"
    return DateUtils.getRelativeTimeSpanString(
        ts, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
