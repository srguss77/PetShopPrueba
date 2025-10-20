package com.example.tiendamascotas.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.tiendamascotas.ServiceLocator
import com.example.tiendamascotas.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    nav: NavHostController,
    peerUid: String,
    repo: ChatRepository = ServiceLocator.chat,
    auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    val scope = rememberCoroutineScope()
    val myUid = remember { auth.currentUser?.uid }

    // Mensajes del hilo (en tiempo real)
    val messages by repo.observeConversation(peerUid).collectAsState(initial = emptyList())

    var text by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversación") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(

                        onSend = {
                            val t = text.trim()
                            if (t.isNotEmpty()) {
                                scope.launch {
                                    repo.sendText(peerUid, t)
                                    text = ""
                                }
                            }
                        }
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val t = text.trim()
                        if (t.isNotEmpty()) {
                            scope.launch {
                                repo.sendText(peerUid, t)
                                text = ""
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar")
                }
            }
        }
    ) { paddings ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddings)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            items(messages, key = { it.id }) { m ->
                val isMine = m.fromUid == myUid

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isMine)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            m.text,
                            modifier = Modifier.padding(10.dp),
                            color = if (isMine)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
