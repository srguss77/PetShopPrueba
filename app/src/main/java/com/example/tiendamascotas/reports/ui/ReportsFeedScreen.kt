package com.example.tiendamascotas.reports.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tiendamascotas.navigation.Screen
import com.example.tiendamascotas.reports.model.PetReport
import com.example.tiendamascotas.reports.util.timeAgo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsFeedScreen(nav: NavHostController, vm: ReportsFeedViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reportes") }) }
    ) { padd ->
        Box(Modifier.fillMaxSize().padding(padd)) {

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(ui.items, key = { it.id }) { r ->
                    ReportCard(
                        report = r,
                        canEdit = vm.canEdit(r),
                        onEdit = { nav.navigate(Screen.CreateReport.editRoute(r.id))
                        },
                        onDelete = { vm.delete(r.id) },
                        onOpen = { nav.navigate(Screen.ReportDetail.route.replace("{reportId}", r.id)) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // FAB abajo-izquierda
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart
            ) {
                FloatingActionButton(
                    onClick = { nav.navigate(Screen.CreateReport.route) // "report/create"
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Crear reporte")
                }
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: PetReport,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    Card(onClick = onOpen) {
        Column(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = report.photoUrl,
                contentDescription = "Foto",
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        report.raza.ifBlank { "Sin raza" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    if (canEdit) {
                        var open by remember { mutableStateOf(false) }
                        IconButton(onClick = { open = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más")
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("Editar") }, onClick = { open = false; onEdit() })
                            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { open = false; onDelete() })
                        }
                    }
                }
                Text("Años: ${report.edadAnios}")
                Text("Vacunas: ${report.vacunas.ifBlank { "No indicado" }}")
                Text(timeAgo(report.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
