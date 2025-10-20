
package com.example.tiendamascotas.reports.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tiendamascotas.ServiceLocator
import com.example.tiendamascotas.data.repository.impl.FirestorePaths
import com.example.tiendamascotas.domain.repository.ReportsRepository
import com.example.tiendamascotas.nav.Routes
import com.example.tiendamascotas.reports.model.PetReport
import com.example.tiendamascotas.reports.util.uploadToCloudinary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReportScreen(
    nav: NavHostController,
    backStackEntry: NavBackStackEntry,
    repo: ReportsRepository = ServiceLocator.reports
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // --- usuario actual (para permisos de edición) ---
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUid by remember { mutableStateOf(auth.currentUser?.uid) }
    var role by remember { mutableStateOf("user") }

    LaunchedEffect(currentUid) {
        currentUid?.let { uid ->
            runCatching {
                val snap = Firebase.firestore.collection(FirestorePaths.USERS).document(uid).get().await()
                role = snap.getString("role") ?: "user"
            }
        }
    }
    fun canEdit(r: PetReport) = role.equals("admin", true) || r.ownerId == currentUid

    // --- feed (tiempo real) ---
    val feed by remember(repo) { repo.feed() }.collectAsState(initial = emptyList())

    // --- edición por deep-link (?editId=...) y por card ---
    val navEditParam = backStackEntry.arguments?.getString("editId")
        ?.takeIf { it.isNullOrBlank().not() && !it!!.startsWith("{") }
    var currentEditId by remember { mutableStateOf<String?>(navEditParam) }

    // --- hoja modal + formulario ---
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by remember { mutableStateOf("") }
    var raza by remember { mutableStateOf("") }
    var edad by remember { mutableStateOf("") }
    var vacunas by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var isEditExisting by remember { mutableStateOf(false) }

    fun resetForm() {
        url = ""; raza = ""; edad = ""; vacunas = ""
        isEditExisting = false
        currentEditId = null
    }

    // prefill si llega editId por navegación
    LaunchedEffect(currentEditId) {
        if (currentEditId != null) {
            saving = true
            val r = repo.get(currentEditId!!)
            saving = false
            if (r != null) {
                isEditExisting = true
                url = r.photoUrl
                raza = r.raza
                edad = r.edadAnios.toString()
                vacunas = r.vacunas
                showSheet = true
            } else {
                snackbar.showSnackbar("El reporte (ID=$currentEditId) no existe")
                resetForm()
            }
        }
    }

    // --- Photo picker + subir a Cloudinary ---
    var lastPicked by remember { mutableStateOf<Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            lastPicked = uri
            scope.launch {
                saving = true
                val res = uploadToCloudinary(context, uri)
                saving = false
                res.onSuccess {
                    url = it
                    snackbar.showSnackbar("Imagen subida")
                }.onFailure {
                    snackbar.showSnackbar("Error al subir: ${it.message}")
                }
            }
        }
    }

    fun submit() = scope.launch {
        saving = true
        val age = edad.toIntOrNull() ?: 0
        val res =
            if (isEditExisting && currentEditId != null) {
                repo.update(
                    id = currentEditId!!,
                    fields = mapOf(
                        "photoUrl" to url.trim(),
                        "raza" to raza.trim(),
                        "edadAnios" to age,
                        "vacunas" to vacunas.trim()
                    )
                )
            } else {
                repo.create(url.trim(), raza.trim(), age, vacunas.trim()).map {}
            }
        saving = false
        res.onSuccess {
            snackbar.showSnackbar(if (isEditExisting) "Cambios guardados" else "Publicado")
            resetForm()
            showSheet = false
        }.onFailure { snackbar.showSnackbar(it.message ?: "Error al guardar") }
    }

    // confirmar borrado
    var toDelete by remember { mutableStateOf<PetReport?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportes") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { resetForm(); showSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padd ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padd)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Publicaciones recientes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (feed.isEmpty()) {
                    Text(
                        "Aún no hay publicaciones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        items(feed, key = { it.id }) { r ->
                            ReportCard(
                                r = r,
                                canEdit = canEdit(r),
                                onEdit = {
                                    isEditExisting = true
                                    currentEditId = r.id
                                },
                                onDelete = { toDelete = r }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        // hoja modal (crear/editar)
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { if (!saving) showSheet = false },
                sheetState = sheetState
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        if (isEditExisting) "Editar reporte" else "Crear reporte",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(12.dp))

                    // Preview si ya hay URL
                    if (url.isNotBlank()) {
                        AsyncImage(
                            model = url,
                            contentDescription = "Foto",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pickImage.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        }) { Text("Seleccionar imagen") }

                        if (url.isNotBlank()) {
                            TextButton(onClick = { url = "" }) { Text("Quitar") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = raza, onValueChange = { raza = it },
                        label = { Text("Raza") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = edad, onValueChange = { edad = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Años") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = vacunas, onValueChange = { vacunas = it },
                        label = { Text("Vacunas (texto)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { submit() },
                        enabled = !saving && url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isEditExisting) "Guardar cambios" else "Publicar") }

                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // diálogo confirmar borrado
        toDelete?.let { r ->
            AlertDialog(
                onDismissRequest = { toDelete = null },
                title = { Text("Eliminar reporte") },
                text = { Text("¿Seguro que quieres eliminar este reporte?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val res = repo.delete(r.id)
                            res.onSuccess { snackbar.showSnackbar("Eliminado") }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Error al eliminar") }
                            toDelete = null
                        }
                    }) { Text("Eliminar") }
                },
                dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
private fun ReportCard(
    r: PetReport,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (r.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = r.photoUrl,
                    contentDescription = "Foto de mascota",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.raza.ifBlank { "Sin raza" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                if (canEdit) {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Borrar") }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Publicado ${timeAgo(r.createdAt?.toDate()?.time)} • por ${r.ownerName.ifBlank { "Usuario" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text("Años: ${r.edadAnios}", style = MaterialTheme.typography.bodyMedium)
            if (r.vacunas.isNotBlank()) {
                Text("Vacunas: ${r.vacunas}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun timeAgo(ms: Long?): String {
    if (ms == null || ms <= 0L) return "hace un momento"
    val diff = System.currentTimeMillis() - ms
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "hace un momento"
        minutes < 60 -> "hace ${minutes} min"
        hours < 24 -> "hace ${hours} h"
        days == 1L -> "hace 1 día"
        else -> "hace ${days} días"
    }
}
