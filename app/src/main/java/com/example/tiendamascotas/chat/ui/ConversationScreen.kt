// FILE: app/src/main/java/com/example/tiendamascotas/chat/ui/ConversationScreen.kt
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.tiendamascotas.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tiendamascotas.ServiceLocator
import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.UserPresence
import com.example.tiendamascotas.domain.repository.UserPublic
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ConversationScreen(
    nav: NavHostController,
    peerUid: String,
    repo: ChatRepository = ServiceLocator.chat,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val myUid = auth.currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()

    // Mensajes del hilo
    var messages by remember(peerUid) { mutableStateOf(emptyList<ChatMessage>()) }
    LaunchedEffect(peerUid, repo) {
        repo.observeConversation(peerUid).collect { msgs -> messages = msgs }
    }

    // Perfil del peer (nombre/foto)
    var userPublic by remember(peerUid) { mutableStateOf<UserPublic?>(null) }
    LaunchedEffect(peerUid, repo) {
        repo.observeUserPublic(peerUid).collect { up -> userPublic = up }
    }

    // Presencia y typing
    val presence by repo.observePresence(peerUid).collectAsState(initial = UserPresence(false, null))
    val typing by repo.observeTyping(peerUid).collectAsState(initial = false)

    val title = userPublic?.displayName ?: peerUid
    val photo = userPublic?.photoUrl
    val subtitle = when {
        typing -> "Escribiendo…"
        presence.isOnline -> "En línea"
        else -> presence.lastSeen?.let { "Últ. vez ${relative(it)}" } ?: "—"
    }

    val uiMessages by remember(messages, myUid) {
        derivedStateOf {
            messages.map { m ->
                MessageUi(
                    id = m.id,
                    text = m.text,
                    isMine = m.fromUid == myUid,
                    timestamp = m.createdAt
                )
            }.sortedByDescending { it.timestamp }
        }
    }

    val items by remember(uiMessages) { derivedStateOf { withDayHeaders(uiMessages) } }
    val listState = rememberLazyListState()

    val isAtBottom by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    val latestMine by remember(items) {
        derivedStateOf {
            items.firstOrNull { it is ConvListItem.MessageItem }?.let { (it as ConvListItem.MessageItem).msg.isMine } ?: false
        }
    }
    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && (latestMine || isAtBottom)) listState.animateScrollToItem(0)
    }

    var input by rememberSaveable { mutableStateOf("") }
    val canSend by remember(input) { derivedStateOf { input.trim().isNotEmpty() } }

    // Debounce de typing (3s)
    var typingJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(peerUid) {
        onDispose {
            // Apaga typing al salir
            scope.launch { repo.setTyping(peerUid, false) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { nav.popBackStack() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" }
                    ) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!photo.isNullOrBlank()) {
                                val ctx = LocalContext.current
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx)
                                        .data(photo)
                                        .crossfade(true)
                                        .size(64)
                                        .build(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        }
    ) { padd ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padd)
                .navigationBarsPadding()
        ) {
            MessagesList(
                items = items,
                listState = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding()
            )

            InputBar(
                value = input,
                onValueChange = { new ->
                    input = new
                    val trimmed = new.trim()
                    scope.launch {
                        if (trimmed.isNotEmpty()) {
                            repo.setTyping(peerUid, true)
                            typingJob?.cancel()
                            typingJob = launch {
                                delay(3000)
                                repo.setTyping(peerUid, false)
                            }
                        } else {
                            typingJob?.cancel()
                            repo.setTyping(peerUid, false)
                        }
                    }
                },
                onSend = {
                    if (!canSend) return@InputBar
                    val textToSend = input.trim()
                    scope.launch {
                        runCatching { repo.sendText(peerUid, textToSend) }
                        input = ""
                        typingJob?.cancel()
                        repo.setTyping(peerUid, false)
                        listState.animateScrollToItem(0)
                    }
                },
                enabled = true,
                canSend = canSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/* ======= resto (burbujas/lista/helpers) ======= */

private data class MessageUi(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val timestamp: Long
)

private sealed class ConvListItem {
    data class DayHeader(val key: String, val title: String) : ConvListItem()
    data class MessageItem(val msg: MessageUi) : ConvListItem()
}

private fun withDayHeaders(messagesDesc: List<MessageUi>): List<ConvListItem> {
    if (messagesDesc.isEmpty()) return emptyList()
    val out = mutableListOf<ConvListItem>()
    var currentKey: String? = null
    val now = System.currentTimeMillis()
    for (m in messagesDesc) {
        val (key, title) = dayKeyAndTitle(m.timestamp, now)
        if (key != currentKey) {
            currentKey = key
            out += ConvListItem.DayHeader(key, title)
        }
        out += ConvListItem.MessageItem(m)
    }
    return out
}

private fun dayKeyAndTitle(ts: Long, now: Long, locale: Locale = Locale.getDefault()): Pair<String, String> {
    val calNow = Calendar.getInstance(locale).apply { timeInMillis = now }
    val calTs = Calendar.getInstance(locale).apply { timeInMillis = ts }
    val sameYear = calNow.get(Calendar.YEAR) == calTs.get(Calendar.YEAR)
    val dayDiff = calNow.get(Calendar.DAY_OF_YEAR) - calTs.get(Calendar.DAY_OF_YEAR)
    val key = "%04d-%03d".format(calTs.get(Calendar.YEAR), calTs.get(Calendar.DAY_OF_YEAR))
    val title = when {
        sameYear && dayDiff == 0 -> "Hoy"
        sameYear && dayDiff == 1 -> "Ayer"
        else -> SimpleDateFormat("dd/MM/yyyy", locale).format(Date(ts))
    }
    return key to title
}

@Composable
private fun MessagesList(
    items: List<ConvListItem>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (items.isEmpty()) {
            item(key = "empty") { EmptyConversation() }
        } else {
            items(
                items = items,
                key = {
                    when (it) {
                        is ConvListItem.DayHeader -> "hdr_${it.key}"
                        is ConvListItem.MessageItem -> "msg_${it.msg.id}"
                    }
                }
            ) { it ->
                when (it) {
                    is ConvListItem.DayHeader -> DayHeader(it.title)
                    is ConvListItem.MessageItem -> MessageBubble(
                        text = it.msg.text,
                        isMine = it.msg.isMine,
                        timestamp = it.msg.timestamp
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(title: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    isMine: Boolean,
    timestamp: Long,
) {
    val bg = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = contentColorFor(bg)
    val hAlign: Alignment.Horizontal = if (isMine) Alignment.End else Alignment.Start
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = hAlign
    ) {
        Surface(
            color = bg,
            contentColor = content,
            shape = shape,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(shape)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                softWrap = true,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip
            )
        }
        val timeStr = remember(timestamp) { timeHHmm(timestamp) }
        Text(
            text = timeStr,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .alpha(0.7f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyConversation() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Aún no hay mensajes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    canSend: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Escribe un mensaje…", style = MaterialTheme.typography.bodyMedium) },
            minLines = 1,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = { if (canSend) onSend() }
            )
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = enabled && canSend,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Enviar mensaje" }
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
        }
    }
}

private fun timeHHmm(ts: Long, locale: Locale = Locale.getDefault()): String {
    if (ts <= 0) return "—"
    return runCatching { SimpleDateFormat("HH:mm", locale).format(Date(ts)) }.getOrDefault("—")
}

private fun relative(ts: Long): String {
    val now = System.currentTimeMillis()
    return android.text.format.DateUtils.getRelativeTimeSpanString(
        ts, now, android.text.format.DateUtils.MINUTE_IN_MILLIS,
        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
