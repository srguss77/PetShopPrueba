package com.example.tiendamascotas.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.tiendamascotas.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: NavHostController, vm: ProfileViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = {
                    TextButton(onClick = {
                        vm.signOut {
                            nav.navigate(Screen.Login.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }) { Text("Cerrar sesión") }
                }
            )
        }
    ) { padd ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(220.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(84.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (ui.displayName.isBlank()) "Sin nombre" else ui.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = if (ui.role.equals("admin", true)) "Admin" else "User",
                                color = Color.White
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            labelColor = Color.White
                        )
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Email", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ui.email, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))

                    Text("Nombre para mostrar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = ui.displayName,
                        onValueChange = vm::onNameChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))
                    Text("Rol", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (ui.role.equals("admin", true)) "Admin" else "User", style = MaterialTheme.typography.titleMedium)

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { vm.saveName() },
                        enabled = !ui.loading,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar")
                    }

                    if (ui.message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(ui.message!!, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
