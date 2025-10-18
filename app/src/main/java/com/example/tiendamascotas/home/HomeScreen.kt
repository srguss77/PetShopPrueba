package com.example.tiendamascotas.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.tiendamascotas.navigation.Screen


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val features = listOf(
        Feature("Reportar", Icons.Filled.Pets, Screen.CreateReport.route),
        Feature("Mapa", Icons.Filled.Map, Screen.Map.route),
        Feature("Asistente", Icons.Filled.Lightbulb, Screen.CareAssistant.route),
        Feature("Reseñas", Icons.Filled.RateReview, Screen.ReviewsHome.route),
        Feature("Chat", Icons.Filled.Chat, Screen.ChatGeneral.route),
        Feature("Adopciones", Icons.Filled.Favorite, Screen.AdoptionsList.route),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mascotas") },
                actions = {
                    IconButton(onClick = { nav.navigate(Screen.NotificationsSettings.route) }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Notificaciones")
                    }
                    IconButton(onClick = { nav.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Perfil")
                    }
                }
            )
        }
    ) { innerPadding ->

        // 👇 En lugar de innerPadding.plus(...), usamos un contenedor y aplicamos paddings por separado
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(features) { f ->
                    FeatureCard(
                        label = f.label,
                        icon = f.icon,
                        onClick = { nav.navigate(f.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = label)
                Spacer(Modifier.height(10.dp))
                Text(text = label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private data class Feature(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)
