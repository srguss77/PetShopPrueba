// FILE: app/src/main/java/com/example/tiendamascotas/adoptions/ui/CreateAdoptionScreen.kt
package com.example.tiendamascotas.adoptions.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tiendamascotas.ServiceLocator
import com.example.tiendamascotas.adoptions.data.CloudinaryUploader
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAdoptionScreen(
    editId: String? = null,        // ✅ soporta edición
    onClose: () -> Unit
) {
    val repo = ServiceLocator.adoptions
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val isEdit = editId != null

    // --- Estados del formulario ---
    var pickedUri by remember { mutableStateOf<Uri?>(null) } // imagen local elegida
    var photoUrl by remember { mutableStateOf("") }          // URL ya subida (o actual)

    var nombre by remember { mutableStateOf("") }
    var especie by remember { mutableStateOf("") }
    var raza by remember { mutableStateOf("") }
    var sexo by remember { mutableStateOf("") }
    var edad by remember { mutableStateOf("") }
    var ubicacion by remember { mutableStateOf("") }
    var razon by remember { mutableStateOf("") }
    var salud by remember { mutableStateOf("") }
    var vacunado by remember { mutableStateOf(false) }
    var esterilizado by remember { mutableStateOf(false) }
    var desparasitado by remember { mutableStateOf(false) }
    var contacto by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(isEdit) }
    var error by remember { mutableStateOf<String?>(null) }

    // ✅ Cargar datos si venimos a editar
    LaunchedEffect(editId) {
        if (editId == null) return@LaunchedEffect
        try {
            val doc = Firebase.firestore.collection("adoption").document(editId).get().await()
            if (doc.exists()) {
                photoUrl = doc.getString("photoUrl").orEmpty()
                nombre = doc.getString("nombre").orEmpty()
                especie = doc.getString("especie").orEmpty()
                raza = doc.getString("raza").orEmpty()
                sexo = doc.getString("sexo").orEmpty()
                edad = (doc.getLong("edadAnios") ?: 0L).toString()
                ubicacion = doc.getString("ubicacion").orEmpty()
                razon = doc.getString("razon").orEmpty()
                salud = doc.getString("salud").orEmpty()
                vacunado = doc.getBoolean("vacunado") ?: false
                esterilizado = doc.getBoolean("esterilizado") ?: false
                desparasitado = doc.getBoolean("desparasitado") ?: false
                contacto = doc.getString("contacto").orEmpty()
            } else {
                error = "La publicación no existe"
            }
        } catch (t: Throwable) {
            error = t.message ?: "No se pudo cargar la publicación"
        } finally {
            loading = false
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> pickedUri = uri }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (isEdit) "Editar adopción" else "Nueva adopción") }) }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            // --- Imagen: solo botón para elegir; la subida será al publicar ---
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { pickImage.launch("image/*") }) { Text("Elegir foto") }
                }
            }

            item {
                if (pickedUri != null || photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = pickedUri ?: photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
                if (photoUrl.isNotBlank()) {
                    Text("URL imagen: $photoUrl", style = MaterialTheme.typography.bodySmall)
                }
            }

            // --- Campos ---
            item {
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it },
                    label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = especie, onValueChange = { especie = it },
                    label = { Text("Especie") }, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = raza, onValueChange = { raza = it },
                    label = { Text("Raza") }, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = sexo, onValueChange = { sexo = it },
                    label = { Text("Sexo (M/F)") }, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = edad, onValueChange = { edad = it },
                    label = { Text("Edad (años)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = ubicacion, onValueChange = { ubicacion = it },
                    label = { Text("Ubicación") }, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = razon, onValueChange = { razon = it },
                    label = { Text("Razón de adopción") }, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = salud, onValueChange = { salud = it },
                    label = { Text("Salud (resumen)") }, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(onClick = { vacunado = !vacunado }, label = { Text(if (vacunado) "Vacunado" else "Sin vacunas") })
                    AssistChip(onClick = { esterilizado = !esterilizado }, label = { Text(if (esterilizado) "Esterilizado" else "No esterilizado") })
                    AssistChip(onClick = { desparasitado = !desparasitado }, label = { Text(if (desparasitado) "Desparasitado" else "No desparasitado") })
                }
            }
            item {
                OutlinedTextField(
                    value = contacto, onValueChange = { contacto = it },
                    label = { Text("Contacto (teléfono o email)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- Botón Guardar / Publicar ---
            item {
                val hasImage = (pickedUri != null || photoUrl.isNotBlank())
                val isValid = nombre.isNotBlank() && especie.isNotBlank() && ubicacion.isNotBlank() && contacto.isNotBlank()

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && !loading && isValid && (hasImage || !isEdit), // en edición puedes reutilizar la existente
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                // 1) Sube imagen si aún no hay URL
                                val finalUrl = ensureImageUploaded(ctx, pickedUri, photoUrl)
                                photoUrl = finalUrl

                                val edadInt = edad.toIntOrNull() ?: 0

                                if (isEdit) {
                                    // 2a) EDITAR
                                    val fields = mapOf(
                                        "photoUrl" to finalUrl,
                                        "nombre" to nombre.trim(),
                                        "especie" to especie.trim(),
                                        "raza" to raza.trim(),
                                        "sexo" to sexo.trim().uppercase(),
                                        "edadAnios" to edadInt,
                                        "ubicacion" to ubicacion.trim(),
                                        "razon" to razon.trim(),
                                        "salud" to salud.trim(),
                                        "vacunado" to vacunado,
                                        "esterilizado" to esterilizado,
                                        "desparasitado" to desparasitado,
                                        "contacto" to contacto.trim()
                                    )
                                    Firebase.firestore.collection("adoption")
                                        .document(editId!!)
                                        .update(fields)
                                        .await()
                                    onClose()
                                } else {
                                    // 2b) CREAR
                                    val res = repo.create(
                                        photoUrl = finalUrl,
                                        nombre = nombre.trim(),
                                        especie = especie.trim(),
                                        raza = raza.trim(),
                                        sexo = sexo.trim().uppercase(),
                                        edadAnios = edadInt,
                                        ubicacion = ubicacion.trim(),
                                        razon = razon.trim(),
                                        salud = salud.trim(),
                                        vacunado = vacunado,
                                        esterilizado = esterilizado,
                                        desparasitado = desparasitado,
                                        contacto = contacto.trim()
                                    )
                                    res.fold(
                                        onSuccess = { onClose() },
                                        onFailure = { throw it }
                                    )
                                }
                            } catch (t: Throwable) {
                                error = t.message ?: "No se pudo guardar la publicación"
                            } finally {
                                saving = false
                            }
                        }
                    }
                ) {
                    Text(
                        when {
                            saving -> if (isEdit) "Guardando..." else "Publicando..."
                            isEdit  -> "Guardar cambios"
                            else    -> "Publicar"
                        }
                    )
                }
            }
        }
    }
}

/** Sube a Cloudinary si no hay URL aún. Lanza error si no hay imagen seleccionada en modo crear. */
private suspend fun ensureImageUploaded(
    ctx: Context,
    picked: Uri?,
    currentUrl: String
): String {
    if (currentUrl.isNotBlank()) return currentUrl
    requireNotNull(picked) { "Selecciona una foto antes de publicar" }
    val result = CloudinaryUploader.uploadUri(ctx, picked, folder = "adoptions")
    return result.getOrThrow()
}
