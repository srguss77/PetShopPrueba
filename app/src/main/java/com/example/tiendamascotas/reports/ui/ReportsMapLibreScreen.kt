// FILE: app/src/main/java/com/example/tiendamascotas/reports/ui/ReportsMapLibreScreen.kt
package com.example.tiendamascotas.reports.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.tiendamascotas.BuildConfig
import com.example.tiendamascotas.places.data.MapTilerGeocodingClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

@SuppressLint("MissingPermission")
@Composable
fun ReportsMapLibreScreen(
    styleUrl: String =
        "https://api.maptiler.com/maps/basic-v2/style.json?key=${BuildConfig.MAPTILER_API_KEY}",
    defaultCenter: LatLng = LatLng(14.634915, -90.506882),
    defaultZoom: Double = 12.0
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var hasLocation by remember { mutableStateOf(false) }

    // Permisos de ubicación
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        hasLocation = (res["android.permission.ACCESS_FINE_LOCATION"] == true) ||
                (res["android.permission.ACCESS_COARSE_LOCATION"] == true)
        if (hasLocation) map?.getStyle()?.let { enableLocation(context, map!!, it, centerNow = true) }
    }

    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).apply {
                getMapAsync { m ->
                    map = m
                    m.setStyle(styleUrl) { st ->
                        m.cameraPosition = CameraPosition.Builder()
                            .target(defaultCenter).zoom(defaultZoom).build()

                        // Fuente + capa para “pin de búsqueda” (una vez)
                        if (st.getSource("search-source") == null) {
                            val src = GeoJsonSource("search-source")
                            st.addSource(src)
                            // inicializar vacía usando la sobrecarga String
                            src.setGeoJson("""{"type":"FeatureCollection","features":[]}""")

                            st.addLayer(
                                CircleLayer("search-layer", "search-source").withProperties(
                                    circleRadius(7.5f),
                                    circleColor("#E53935"),
                                    circleStrokeWidth(2.0f),
                                    circleStrokeColor("#FFFFFF")
                                )
                            )
                        }

                        if (hasLocation) enableLocation(ctx, m, st, centerNow = true)
                    }
                }
                mapView = this
            }
        }
    )

    // Ciclo de vida del MapView
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView?.apply { onPause(); onStop(); onDestroy() }
            mapView = null; map = null
        }
    }

    // Pedir permisos al entrar
    LaunchedEffect(Unit) {
        if (!hasLocation) {
            permissionLauncher.launch(arrayOf(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION"
            ))
        }
    }

    // UI: barra de búsqueda y FABs
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Buscar lugar (ej. veterinaria zona 10)") },
                leadingIcon = { Text("🔍") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (query.isBlank()) return@FloatingActionButton
                    scope.launch {
                        try {
                            val resp = MapTilerGeocodingClient.api.search(query)
                            val coords = resp.features?.firstOrNull()?.geometry?.coordinates // [lon, lat]
                            val lon = coords?.getOrNull(0)
                            val lat = coords?.getOrNull(1)
                            if (lat != null && lon != null) {
                                val target = LatLng(lat, lon)
                                map?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(target, 15.0)
                                )
                                // Actualiza la fuente con String GeoJSON (sin choques de tipos)
                                map?.getStyle()?.let { st ->
                                    val src = st.getSource("search-source") as? GeoJsonSource ?: return@let
                                    val geojson = """
                                      {"type":"FeatureCollection",
                                       "features":[{"type":"Feature",
                                                    "geometry":{"type":"Point","coordinates":[$lon,$lat]}}]}
                                    """.trimIndent()
                                    src.setGeoJson(geojson)
                                }
                            }
                        } catch (_: Throwable) { /* TODO: snackbar si quieres */ }
                    }
                }
            ) { Text("Ir") }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomIn()) }) {
                Text("+")
            }
            FloatingActionButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomOut()) }) {
                Text("–")
            }
            FloatingActionButton(onClick = { centerOnCurrentLocation(context, map) }) {
                Text("📍")
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun enableLocation(
    ctx: Context,
    map: MapLibreMap,
    style: Style,
    centerNow: Boolean
) {
    val options = LocationComponentOptions.builder(ctx)
        .pulseEnabled(true)
        .build()

    val activation = LocationComponentActivationOptions
        .builder(ctx, style)
        .locationComponentOptions(options)
        .useDefaultLocationEngine(true)
        .build()

    val lc: LocationComponent = map.locationComponent
    lc.activateLocationComponent(activation)
    lc.isLocationComponentEnabled = true
    lc.cameraMode = CameraMode.TRACKING

    if (centerNow) centerOnCurrentLocation(ctx, map)
}

@SuppressLint("MissingPermission")
private fun centerOnCurrentLocation(ctx: Context, map: MapLibreMap?) {
    map ?: return

    // Si el LocationComponent aún no está activado, actívalo "on-demand" de forma segura
    try {
        val style = map.style
        val lc = map.locationComponent
        if (style != null && !lc.isLocationComponentEnabled) {
            val options = LocationComponentOptions.builder(ctx).pulseEnabled(true).build()
            val activation = LocationComponentActivationOptions
                .builder(ctx, style)
                .locationComponentOptions(options)
                .useDefaultLocationEngine(true)
                .build()
            lc.activateLocationComponent(activation)
            lc.isLocationComponentEnabled = true
            lc.cameraMode = CameraMode.TRACKING
        }
    } catch (_: Throwable) {
        // no crashear si falla la activación
    }

    val fused = LocationServices.getFusedLocationProviderClient(ctx)
    val cts = CancellationTokenSource()
    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
        .addOnSuccessListener { loc: Location? ->
            loc?.let {
                try { map.locationComponent.forceLocationUpdate(it) } catch (_: Throwable) { }
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15.0)
                )
            }
        }
}
