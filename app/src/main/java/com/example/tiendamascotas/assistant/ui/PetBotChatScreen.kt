
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.tiendamascotas.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun PetBotChatScreen(
    viewModel: PetBotViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PetBot 🐾") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Atrás"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Escribe tu pregunta sobre tu mascota…") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val txt = input.trim()
                            if (txt.isNotEmpty() && !state.isSending) {
                                scope.launch { viewModel.send(txt) }
                                input = ""
                            }
                        }
                    )
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val txt = input.trim()
                        if (txt.isNotEmpty()) {
                            scope.launch { viewModel.send(txt) }
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank() && !state.isSending,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (state.isSending) "Enviando…" else "Enviar")
                }
            }
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(state.messages) { msg ->
                    ChatBubble(
                        text = msg.text,
                        fromUser = msg.fromUser
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.isSending) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Text("Pensando…")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(text: String, fromUser: Boolean) {
    val bg = if (fromUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bg,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
