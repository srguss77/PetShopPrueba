// FILE: app/src/main/java/com/example/tiendamascotas/chat/ui/ConversationScreen.kt
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.tiendamascotas.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ===========================================================
   API pública (mantener firma y defaults)
   =========================================================== */

@Composable
fun ConversationScreen(
    nav: NavHostController,
    peerUid: String,
    // Mantiene tu API por defecto. Ajusta el paquete si tu ServiceLocator vive en otro.
    repo: Any = com.example.tiendamascotas.ServiceLocator.chat,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val myUid = auth.currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()

    // === Observación de mensajes sin depender de tipos concretos ===
    var rawMessages by remember { mutableStateOf<List<Any>>(emptyList()) }

    LaunchedEffect(repo, peerUid) {
        // intenta observeConversation(peerUid) o variantes
        val flow = resolveObserveFlow(repo, peerUid)
        if (flow != null) {
            flow.collect { emitted ->
                rawMessages = when (emitted) {
                    is List<*> -> emitted.filterNotNull()
                    else -> emptyList()
                }
            }
        } else {
            // si no hay flujo, dejamos vacío (no rompemos UI)
            rawMessages = emptyList()
        }
    }

    // Título: usa peerUid ahora. TODO para resolver displayName real sin implementarlo.
    val title by remember(peerUid) {
        mutableStateOf(peerUid) // TODO(): resolver nombre del peer (lookup en /users o args)
    }

    // Mapeo UI (no tocamos backend). Asumimos campos id/fromUid/text/createdAt en los objetos.
    val uiMessages by remember(rawMessages, myUid) {
        derivedStateOf {
            rawMessages.mapNotNull { m -> toMessageUi(m, myUid) }
                .sortedByDescending { it.timestamp } // reverseLayout=true → más nuevo arriba (índice 0)
        }
    }

    // Headers por día (Hoy, Ayer, dd/MM/yyyy)
    val items by remember(uiMessages) { derivedStateOf { withDayHeaders(uiMessages) } }

    val listState = rememberLazyListState()

    // Estoy al fondo (con reverseLayout, fondo = índice 0 sin offset)
    val isAtBottom by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }

    // ¿El más nuevo es mío?
    val latestMine by remember(items) {
        derivedStateOf {
            items.firstOrNull { it is ConvListItem.MessageItem }?.let { (it as ConvListItem.MessageItem).msg.isMine } ?: false
        }
    }

    // Auto-scroll: si el más nuevo es mío o ya estás al fondo → baja.
    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && (latestMine || isAtBottom)) {
            listState.animateScrollToItem(0)
        }
    }

    // Estado de input
    var input by rememberSaveable { mutableStateOf("") }
    val canSend by remember(input) { derivedStateOf { input.trim().isNotEmpty() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { nav.popBackStack() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" }
                    ) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                },
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    ) { padd ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padd)
        ) {
            MessagesList(
                items = items,
                listState = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            InputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    if (!canSend) return@InputBar
                    val textToSend = input.trim()
                    scope.launch {
                        runCatching { trySendText(repo, peerUid, textToSend) }
                        input = ""
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

/* ===========================================================
   Resolución dinámica de repo/flujo/envío (reflexión segura)
   =========================================================== */

// Busca un método con nombre y un único parámetro String
private fun findStringMethod(target: Any, vararg names: String): Method? {
    val methods = target.javaClass.methods
    for (n in names) {
        methods.firstOrNull { it.name == n && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
            ?.let { return it }
    }
    return null
}

// Intenta obtener Flow<List<Any>> desde repo.observeConversation(peerUid) u otras variantes conocidas
@Suppress("UNCHECKED_CAST")
private fun resolveObserveFlow(repo: Any, peerUid: String): Flow<List<Any>>? {
    val m: Method? = findStringMethod(repo, "observeConversation", "observeMessages")
    val result = m?.invoke(repo, peerUid) ?: return null
    return when (result) {
        is Flow<*> -> result as Flow<List<Any>> // asumimos que emite List<*>
        else -> null
    }
}

// Invoca repo.sendText(peerUid, text) o variantes
private fun trySendText(repo: Any, peerUid: String, text: String) {
    // Prioridad: sendText, sendMessage
    val m: Method? = repo.javaClass.methods.firstOrNull {
        (it.name == "sendText" || it.name == "sendMessage") &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1] == String::class.java
    }
    m?.invoke(repo, peerUid, text)
}

/* ===========================================================
   Modelos/UI helpers
   =========================================================== */

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

// Convierte un objeto de tu capa de datos a MessageUi usando reflexión suave
private fun toMessageUi(m: Any, myUid: String): MessageUi? = runCatching {
    val cls = m.javaClass
    val id: String? =
        (cls.methods.firstOrNull { it.name == "getId" && it.parameterCount == 0 }?.invoke(m) as? String)
            ?: cls.declaredFields.firstOrNull { it.name == "id" }?.let { f ->
                f.isAccessible = true; f.get(m) as? String
            }

    val fromUid: String? =
        (cls.methods.firstOrNull { it.name == "getFromUid" && it.parameterCount == 0 }?.invoke(m) as? String)
            ?: cls.declaredFields.firstOrNull { it.name == "fromUid" }?.let { f ->
                f.isAccessible = true; f.get(m) as? String
            }

    val text: String? =
        (cls.methods.firstOrNull { it.name == "getText" && it.parameterCount == 0 }?.invoke(m) as? String)
            ?: cls.declaredFields.firstOrNull { it.name == "text" }?.let { f ->
                f.isAccessible = true; f.get(m) as? String
            }

    val createdAtMillis: Long? = (
            (cls.methods.firstOrNull { it.name == "getCreatedAt" && it.parameterCount == 0 }?.invoke(m) as? Number)?.toLong()
                ?: cls.declaredFields.firstOrNull { it.name == "createdAt" }?.let { f ->
                    f.isAccessible = true; (f.get(m) as? Number)?.toLong()
                }
            )

    if (fromUid == null || text == null || createdAtMillis == null) null
    else MessageUi(
        id = id ?: "${createdAtMillis}_${text.hashCode()}",
        text = text,
        isMine = fromUid == myUid,
        timestamp = createdAtMillis
    )
}.getOrNull()

private fun withDayHeaders(messagesDesc: List<MessageUi>): List<ConvListItem> {
    if (messagesDesc.isEmpty()) return emptyList()
    val out = mutableListOf<ConvListItem>()
    var currentKey: String? = null
    val now = System.currentTimeMillis()
    for (m in messagesDesc) {
        val (key, title) = dayKeyAndTitle(m.timestamp, now)
        if (key != currentKey) {
            currentKey = key
            out += ConvListItem.DayHeader(key = key, title = title)
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

/* ===========================================================
   Composables de lista e ítems
   =========================================================== */

@Composable
private fun MessagesList(
    items: List<ConvListItem>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        reverseLayout = true, // últimos abajo
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
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    isMine: Boolean,
    timestamp: Long,
) {
    val maxWidthFraction = 0.85f
    val bg = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = contentColorFor(bg)
    val hAlign: Alignment.Horizontal = if (isMine) Alignment.End else Alignment.Start

    // Esquinas diferenciadas para mine vs peer
    val shape = if (isMine) {
        RoundedCornerShape(
            topStart = 18.dp, topEnd = 4.dp,
            bottomEnd = 18.dp, bottomStart = 18.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp, topEnd = 18.dp,
            bottomEnd = 18.dp, bottomStart = 18.dp
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = hAlign
    ) {
        Surface(
            color = bg,
            contentColor = content,
            shape = shape,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(maxWidthFraction)
                .clip(shape)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                softWrap = true,
                overflow = TextOverflow.Clip // romper palabras largas
            )
        }
        val timeStr = remember(timestamp) { timeHHmm(timestamp) }
        Text(
            text = timeStr,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ===========================================================
   Barra de input
   =========================================================== */

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
            placeholder = { Text("Escribe un mensaje…") },
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

/* ===========================================================
   Helpers de formato
   =========================================================== */

private fun timeHHmm(ts: Long, locale: Locale = Locale.getDefault()): String {
    if (ts <= 0) return "—"
    return runCatching { SimpleDateFormat("HH:mm", locale).format(Date(ts)) }.getOrDefault("—")
}

/* ===========================================================
   Previews (mock) – no tocan tu repo
   =========================================================== */

@androidx.compose.ui.tooling.preview.Preview(name = "Conversation — Claro", showBackground = true)
@Composable
private fun PreviewConversationLight() {
    MaterialTheme { PreviewContent() }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Conversation — Oscuro", showBackground = true)
@Composable
private fun PreviewConversationDark() {
    MaterialTheme(colorScheme = darkColorScheme()) { PreviewContent() }
}

@Composable
private fun PreviewContent() {
    val now = System.currentTimeMillis()
    val msgs = listOf(
        MessageUi("1", "¡Hola! ¿Cómo va todo?", false, now - 5 * 60_000),
        MessageUi("2", "Todo bien, ¿y tú? 😊", true, now - 4 * 60_000),
        MessageUi("3", "Bastante ocupado, te escribo luego.", false, now - 3 * 60_000),
        MessageUi("4", "Perfecto, gracias.", true, now - 2 * 60_000),
        MessageUi("5", "Dale, allí estaré.", false, now - 60_000),
        MessageUi("6", "Texto sin espaciosssssssssssssssssssssssssssssssssssssss", true, now - 30_000),
    ).sortedByDescending { it.timestamp }

    val items = withDayHeaders(msgs)
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Peer Name") },
            navigationIcon = { IconButton(onClick = {}) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
        )
        MessagesList(items, listState, Modifier.weight(1f))
        InputBar(
            value = "", onValueChange = {}, onSend = {},
            enabled = true, canSend = false,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
