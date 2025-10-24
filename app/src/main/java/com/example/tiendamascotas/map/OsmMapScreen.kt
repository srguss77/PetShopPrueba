// FILE: app/src/main/java/com/example/tiendamascotas/map/OsmMapScreen.kt
package com.example.tiendamascotas.map

import android.Manifest
import android.annotation.SuppressLint
import android.preference.PreferenceManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsmMapScreen(
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = ctx.packageName
    }

    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    val scope = rememberCoroutineScope() // <-- para lanzar corutinas desde onClick

    var hasLocationPerm by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var myPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var places by remember { mutableStateOf<List<VetPlace>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPerm = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    var mapView: MapView? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDetach()
            mapView = null
        }
    }
    val vetMarkers = remember { mutableStateListOf<Marker>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Veterinarias cercanas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val me = myPoint ?: return@IconButton
                        isLoading = true
                        error = null
                        // IMPORTANTE: usar corutina, NO LaunchedEffect aquí
                        scope.launch {
                            try {
                                val data = withContext(Dispatchers.IO) {
                                    OverpassService.nearbyVeterinaries(
                                        lat = me.latitude,
                                        lng = me.longitude,
                                        radius = 3000
                                    )
                                }
                                places = data
                            } catch (t: Throwable) {
                                error = t.message
                            } finally {
                                isLoading = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (error != null) {
                Text(text = "Error: $error", color = MaterialTheme.colorScheme.error)
            }
            if (isLoading) {
                LinearProgressIndicator()
            }

            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = {
                        MapView(it).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(it), this)
                            myLocationOverlay.enableMyLocation()
                            overlays.add(myLocationOverlay)
                            mapView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view ->
                        (view as? MapView)?.onDetach()
                        mapView = null
                    }
                )
            }
        }
    }

    // Cuando hay permisos: obtener ubicación y cargar veterinarias (esto SÍ es un contexto composable válido)
    LaunchedEffect(hasLocationPerm) {
        if (!hasLocationPerm) return@LaunchedEffect
        isLoading = true
        error = null
        try {
            val loc = getCurrentLocation(fused)
            val pt = GeoPoint(loc.latitude, loc.longitude)
            myPoint = pt
            mapView?.controller?.setCenter(pt)

            val data = withContext(Dispatchers.IO) {
                OverpassService.nearbyVeterinaries(
                    lat = pt.latitude,
                    lng = pt.longitude,
                    radius = 3000
                )
            }
            places = data
        } catch (t: Throwable) {
            error = t.message ?: "No se pudo obtener ubicación"
        } finally {
            isLoading = false
        }
    }

    // Redibujar marcadores cuando cambian los lugares (contexto composable)
    LaunchedEffect(places) {
        val map = mapView ?: return@LaunchedEffect
        map.overlays.removeAll(vetMarkers)
        vetMarkers.clear()

        places.forEach { p ->
            val m = Marker(map).apply {
                position = GeoPoint(p.lat, p.lng)
                title = p.name
                subDescription = p.address ?: ""
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            vetMarkers.add(m)
            map.overlays.add(m)
        }
        map.invalidate()
    }
}

@SuppressLint("MissingPermission")
private suspend fun getCurrentLocation(
    fused: FusedLocationProviderClient
): android.location.Location = withContext(Dispatchers.Main) {
    val cts = CancellationTokenSource()
    try {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val task = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            task.addOnSuccessListener { loc ->
                if (!cont.isCompleted) {
                    if (loc != null) cont.resume(loc, onCancellation = null)
                    else cont.resumeWith(Result.failure(IllegalStateException("Ubicación nula")))
                }
            }
            task.addOnFailureListener { e ->
                if (!cont.isCompleted) cont.resumeWith(Result.failure(e))
            }
            // Si la corutina se cancela (navegas atrás, etc.), cancelamos el token
            cont.invokeOnCancellation { cts.cancel() }
        }
    } finally {
        // Asegura que no quede vivo el canal interno
        cts.cancel()
    }
}
