@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.tiendamascotas.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tiendamascotas.nav.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGeneralScreen(nav: NavHostController) {
    var query by remember { mutableStateOf("") }

    // Datos fake para UI (luego los reemplazamos por Firestore)
    val all = remember {
        listOf(
            ChatPreview("u1", "Ana Morales",
                "https://i.pravatar.cc/150?img=1", "¿A qué hora abrís?", "18:53", unread = 2),
            ChatPreview("u2", "Carlos Pérez",
                "https://i.pravatar.cc/150?img=2", "Listo, gracias!", "17:10", unread = 0),
            ChatPreview("u3", "María López",
                "https://i.pravatar.cc/150?img=3", "¿Sigue en adopción?", "Ayer", unread = 1),
            ChatPreview("u4", "Juan Rivera",
                "https://i.pravatar.cc/150?img=4", "Nos vemos!", "Lun", unread = 0),
        )
    }
    val filtered = remember(query, all) {
        if (query.isBlank()) all
        else all.filter { it.name.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat general") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { paddings ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddings)
        ) {
            // Barra de búsqueda (Material3 es experimental -> usamos @OptIn)
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Buscar usuarios") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {}

            // Lista tipo WhatsApp/Disa
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filtered, key = { it.uid }) { item ->
                    ChatRow(
                        item = item,
                        onOpenThread = { nav.navigate("${Routes.CONVERSATION.replace("{peerUid}", item.uid)}") },
                        onOpenProfile = { nav.navigate("profile/${item.uid}") }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private data class ChatPreview(
    val uid: String,
    val name: String,
    val avatarUrl: String?,
    val lastMessage: String,
    val timeLabel: String, // texto tipo "18:53", "Ayer", etc.
    val unread: Int
)

@Composable
private fun ChatRow(
    item: ChatPreview,
    onOpenThread: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenThread() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        if (!item.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Nombre + último mensaje
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Badge de no leídos
        if (item.unread > 0) {
            Spacer(Modifier.width(8.dp))
            Badge { Text(item.unread.toString()) }
        }

        // Acceso rápido a perfil (opcional)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onOpenProfile) {
            Icon(Icons.Default.Person, contentDescription = "Ver perfil")
        }
    }
}
