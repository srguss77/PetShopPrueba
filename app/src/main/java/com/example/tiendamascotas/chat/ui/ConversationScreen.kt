package com.example.tiendamascotas.chat.ui

import androidx.compose.foundation.layout.padding   // <— IMPORT NECESARIO
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    nav: NavHostController,
    peerUid: String
) {
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
        }
    ) { paddings ->
        Text(
            text = "Placeholder: chat con $peerUid",
            modifier = Modifier
                .padding(paddings)
                .padding(16.dp)
        )
    }
}
