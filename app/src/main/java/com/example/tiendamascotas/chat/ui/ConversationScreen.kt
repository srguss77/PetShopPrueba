// FILE: app/src/main/java/com/example/tiendamascotas/chat/ui/ConversationScreen.kt  (REEMPLAZA COMPLETO)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.tiendamascotas.chat.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.tiendamascotas.ServiceLocator
import com.example.tiendamascotas.ServiceLocator.VET_BOT_UID
import com.example.tiendamascotas.assistant.AssistantChatRequest
import com.example.tiendamascotas.data.repository.impl.RtdbChatRepository   // 👈 para el cast seguro
import com.example.tiendamascotas.domain.model.ChatMessage
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.example.tiendamascotas.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ConversationScreen(
    nav: NavHostController,
    peerUid: String,
    chatRepo: ChatRepository = ServiceLocator.chat,
    userRepo: UserRepository = ServiceLocator.users,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val me = auth.currentUser?.uid ?: return
    val msgs by chatRepo.observeConversation(peerUid).collectAsState(initial = emptyList())
    val isBot = peerUid == VET_BOT_UID
    val peer by if (!isBot) userRepo.observeUserPublic(peerUid).collectAsState(initial = null) else remember { mutableStateOf(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var text by remember { mutableStateOf("") }
    var urgentBanner by remember { mutableStateOf(false) }

    // Disclaimer 1 sola vez al abrir el Asistente
    var showDisclaimer by remember {
        mutableStateOf(isBot && !ctx.getSharedPreferences("assistant", 0).getBoolean("disclaimer_shown", false))
    }
    fun markDisclaimerShown() {
        ctx.getSharedPreferences("assistant", 0).edit().putBoolean("disclaimer_shown", true).apply()
    }

    LaunchedEffect(peerUid) {
        chatRepo.markThreadRead(peerUid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                title = {
                    Column {
                        Text(
                            if (isBot) "Asistente" else peer?.displayName ?: peerUid,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isBot) {
                            val presence by chatRepo.observePresence(peerUid).collectAsState(
                                initial = com.example.tiendamascotas.domain.repository.UserPresence(false, null)
                            )
                            val sub = if (presence.isOnline) "En línea"
                            else presence.lastSeen?.let {
                                "Últ. vez " + DateUtils.getRelativeTimeSpanString(
                                    it, System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                    DateUtils.FORMAT_ABBREV_RELATIVE
                                )
                            } ?: "—"
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    ) { padd ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padd)
                .imePadding()
        ) {
            if (urgentBanner) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Podría requerir atención veterinaria.", style = MaterialTheme.typography.bodyMedium)
                        Text("Te sugerimos contactarte con un profesional a la brevedad.", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
            ) {
                val itemsRev = msgs.asReversed()
                items(itemsRev, key = { it.id }) { m ->
                    val isMine = m.fromUid == me
                    MessageBubble(
                        text = m.text,
                        isMine = isMine,
                        timestamp = m.createdAt
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        if (!isBot) {
                            scope.launch { chatRepo.setTyping(peerUid, it.isNotBlank()) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje…") },
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                val canSend = text.trim().isNotEmpty()
                Button(
                    onClick = {
                        val toSend = text.trim()
                        text = ""
                        scope.launch {
                            // 1) Guardar saliente SIEMPRE (mismo flujo 1–a–1)
                            chatRepo.sendText(peerUid, toSend)
                            if (!isBot) chatRepo.setTyping(peerUid, false)
                            chatRepo.markThreadRead(peerUid)
                        }
                        scope.launch { listState.animateScrollToItem(0) }

                        if (isBot) {
                            // 2) Llamar backend /chat y guardar respuesta del bot
                            scope.launch {
                                urgentBanner = false
                                val result = ServiceLocator.assistant.chat(
                                    AssistantChatRequest(
                                        userId = me,
                                        message = toSend,
                                        species = null,
                                        locale = "es-GT"
                                    )
                                )
                                result.fold(
                                    onSuccess = { r ->
                                        val pie = if (r.sources.isNotEmpty()) "\n\nFuentes: " + r.sources.joinToString("; ") else ""
                                        val finalText = r.reply + pie
                                        urgentBanner = r.risk.equals("urgent", ignoreCase = true)

                                        // 👇 Cast seguro al repo concreto para insertar el entrante
                                        (chatRepo as? RtdbChatRepository)?.receiveText(
                                            fromUid = VET_BOT_UID,
                                            toUid = me,
                                            text = finalText
                                        )
                                    },
                                    onFailure = {
                                        (chatRepo as? RtdbChatRepository)?.receiveText(
                                            fromUid = VET_BOT_UID,
                                            toUid = me,
                                            text = "No pude responder ahora. Revisa tu conexión e intenta de nuevo."
                                        )
                                    }
                                )
                            }
                        }
                    },
                    enabled = canSend
                ) { Text("Enviar") }
            }
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false; markDisclaimerShown() },
            confirmButton = {
                TextButton(onClick = { showDisclaimer = false; markDisclaimerShown() }) { Text("Entendido") }
            },
            title = { Text("Aviso") },
            text = {
                Text(
                    "Este asistente ofrece orientación general con base en guías veterinarias (WSAVA, AAHA, Merck). No reemplaza la evaluación clínica. En urgencias, acude a un veterinario."
                )
            }
        )
    }
}

@Composable
private fun MessageBubble(
    text: String,
    isMine: Boolean,
    timestamp: Long
) {
    val shapeMine = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    val shapePeer = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    val bg = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = contentColorFor(bg)

    val timeText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.85f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(if (isMine) shapeMine else shapePeer)
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = text, color = fg, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(text = timeText, color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
