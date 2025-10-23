// FILE: app/src/main/java/com/example/tiendamascotas/chat/ui/ChatGeneralScreen.kt
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.tiendamascotas.chat.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tiendamascotas.ServiceLocator
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.ChatThread
import com.example.tiendamascotas.domain.repository.UserPresence
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private enum class ChatSegment { Open, Closed }
private const val AI_UID = "petshop_ai"

private data class ThreadUi(
    val peerUid: String,
    val displayName: String?,
    val photoUrl: String?,
    val lastMessage: String?,
    val updatedAt: Long?,
    val unreadCount: Int?
)

@Composable
fun ChatGeneralScreen(
    nav: NavHostController,
    repo: ChatRepository = ServiceLocator.chat,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUid = auth.currentUser?.uid

    var segment by rememberSaveable { mutableStateOf(ChatSegment.Open) }
    var showSearch by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    // Estado local para "closed" (sin persistir aún)
    var closedOverridesEncoded by rememberSaveable { mutableStateOf("") }
    val closedOverrides by remember(closedOverridesEncoded) { mutableStateOf(decodeClosedMap(closedOverridesEncoded)) }

    val listState: LazyListState = rememberLazyListState()

    var threads by remember { mutableStateOf(listOf<ThreadUi>()) }
    var reloadKey by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUid, repo, reloadKey) {
        if (currentUid == null) {
            threads = emptyList()
            return@LaunchedEffect
        }
        try {
            repo.observeThreadsFor(currentUid).collect { list ->
                threads = list.map { it.toUi() }
            }
        } catch (_: Throwable) {
            scope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = "Error al cargar chats",
                    actionLabel = "Reintentar",
                    withDismissAction = true
                )
                if (res.name == "ActionPerformed") reloadKey++
            }
        }
    }

    val visibleThreads by remember(threads, segment, closedOverrides, query, showSearch) {
        derivedStateOf {
            val base = threads.filter { t ->
                val isClosed = closedOverrides[t.peerUid] ?: false
                when (segment) {
                    ChatSegment.Open -> !isClosed
                    ChatSegment.Closed -> isClosed
                }
            }
            if (showSearch && query.isNotBlank()) {
                val q = query.trim().lowercase()
                base.filter { t ->
                    (t.displayName ?: t.peerUid).lowercase().contains(q) ||
                            t.peerUid.lowercase().contains(q) ||
                            (t.lastMessage ?: "").lowercase().contains(q)
                }
            } else base
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padd ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padd)
                .navigationBarsPadding()
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = segment == ChatSegment.Open,
                    onClick = { segment = ChatSegment.Open },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Open", style = MaterialTheme.typography.labelSmall) }
                SegmentedButton(
                    selected = segment == ChatSegment.Closed,
                    onClick = { segment = ChatSegment.Closed },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Closed", style = MaterialTheme.typography.labelSmall) }
            }

            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    label = { Text("Buscar en tus chats…", style = MaterialTheme.typography.labelSmall) }
                )
                Spacer(Modifier.height(6.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                state = listState
            ) {
                item(key = "petshop-ia") {
                    ChatRow(
                        peerUid = AI_UID,
                        repo = repo,
                        avatarUrl = null,
                        title = "PetShop IA 🤖",
                        subtitle = "Asistente virtual",
                        unread = 0,
                        updatedAt = null,
                        onClick = { nav.navigate("conversation/$AI_UID") }
                    )
                    DividerIndent()
                }

                if (visibleThreads.isEmpty()) {
                    item(key = "empty-${segment.name}") {
                        EmptyState(
                            text = if (segment == ChatSegment.Open) "Aún no tienes conversaciones"
                            else "Sin conversaciones cerradas"
                        )
                    }
                } else {
                    itemsIndexed(visibleThreads, key = { _, it -> it.peerUid }) { index, t ->
                        ChatRow(
                            peerUid = t.peerUid,
                            repo = repo,
                            avatarUrl = t.photoUrl,
                            title = (t.displayName ?: t.peerUid).ifBlank { t.peerUid },
                            subtitle = (t.lastMessage ?: "—").ifBlank { "—" },
                            unread = (t.unreadCount ?: 0).coerceAtLeast(0),
                            updatedAt = t.updatedAt,
                            onClick = { nav.navigate("conversation/${t.peerUid}") }
                        )
                        if (index < visibleThreads.lastIndex) {
                            DividerIndent()
                        }
                    }
                }
            }
        }
    }
}

/* --------------------- Mappers / Utils --------------------- */

private fun ChatThread.toUi() = ThreadUi(
    peerUid = peerUid,
    displayName = displayName,
    photoUrl = photoUrl,
    lastMessage = lastMessage,
    updatedAt = updatedAt,
    unreadCount = unreadCount
)

@Composable
private fun ChatRow(
    peerUid: String,
    repo: ChatRepository,
    avatarUrl: String?,
    title: String,
    subtitle: String,
    unread: Int,
    updatedAt: Long?,
    onClick: () -> Unit
) {
    val presence by repo.observePresence(peerUid).collectAsState(initial = UserPresence(false, null))
    val relTime: String = remember(updatedAt) { formatRelativeTime(updatedAt) }
    val lastSeenStr: String = remember(presence) {
        if (presence.isOnline) "En línea"
        else presence.lastSeen?.let { "Activo ${formatRelativeTime(it)}" } ?: "—"
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        leadingContent = {
            Box {
                Avatar(
                    avatarUrl = avatarUrl,
                    fallbackIcon = Icons.Default.Chat,
                    contentDesc = "Avatar de $title"
                )
                if (presence.isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2ECC71)) // verde legible
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.surface), CircleShape)
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = relTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = lastSeenStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                if (unread > 0) {
                    val badgeText = if (unread > 99) "99+" else unread.toString()
                    BadgedBox(
                        badge = {
                            Badge(
                                modifier = Modifier.semantics {
                                    contentDescription = "$badgeText mensajes sin leer"
                                }
                            ) { Text(badgeText, style = MaterialTheme.typography.labelSmall) }
                        }
                    ) { Spacer(Modifier.size(1.dp)) }
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
    val context = LocalContext.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant

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
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(BorderStroke(1.dp, borderColor), CircleShape)
                .semantics { this.contentDescription = contentDesc }
        )
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(BorderStroke(1.dp, borderColor), CircleShape),
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

@Composable private fun DividerIndent() {
    Divider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 80.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 1.dp
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
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun decodeClosedMap(raw: String): Map<String, Boolean> =
    if (raw.isBlank()) emptyMap()
    else raw.split("|").mapNotNull { tok ->
        val i = tok.indexOf(':'); if (i <= 0 || i >= tok.lastIndex) null else tok.substring(0, i) to (tok.substring(i + 1) == "1")
    }.toMap()

/** Hora relativa corta usando DateUtils. */
private fun formatRelativeTime(ts: Long?, now: Long = System.currentTimeMillis()): String {
    if (ts == null || ts <= 0) return "—"
    return DateUtils.getRelativeTimeSpanString(
        ts, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
