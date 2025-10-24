// FILE: app/src/main/java/com/example/tiendamascotas/AppNavHost.kt
package com.example.tiendamascotas

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth

import com.example.tiendamascotas.chat.ui.ChatGeneralScreen
import com.example.tiendamascotas.chat.ui.ConversationScreen
import com.example.tiendamascotas.home.HomeScreen
import com.example.tiendamascotas.navigation.Screen
import com.example.tiendamascotas.nav.Routes
import com.example.tiendamascotas.profile.ProfileScreen
import com.example.tiendamascotas.reports.ui.CreateReportScreen
import com.example.tiendamascotas.reports.ui.ReportsFeedScreen
import com.example.tiendamascotas.ui.auth.LoginScreen

// 👇 Pantalla del mapa (OSM)
import com.example.tiendamascotas.map.OsmMapScreen
// import com.example.tiendamascotas.map.MapScreen  // (si usas Google Maps)

@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var isLoggedIn by remember { mutableStateOf(firebaseAuth.currentUser != null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fb ->
            isLoggedIn = fb.currentUser != null
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    NavHost(navController = nav, startDestination = "auth/gate") {

        composable("auth/gate") {
            LaunchedEffect(isLoggedIn) {
                nav.navigate(if (isLoggedIn) Screen.Home.route else Screen.Login.route) {
                    popUpTo("auth/gate") { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        composable(Screen.Login.route) { LoginScreen(nav) }
        composable(Screen.Home.route) { HomeScreen(nav) }

        // Crear/editar reporte (con editId opcional)
        composable(
            route = "report/create?editId={editId}",
            arguments = listOf(
                navArgument("editId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStack -> CreateReportScreen(nav, backStack) }

        composable(Screen.ReportsFeed.route) { ReportsFeedScreen(nav) }

        // ✅ Mapa con la constante de Routes (opción B)
        composable(Routes.MAP) {
            // OSM gratis:
            OsmMapScreen(onBack = { nav.popBackStack() })

            // Si usas Google Maps:
            // val apiKey = stringResource(id = R.string.google_maps_key)
            // MapScreen(apiKey = apiKey, onBack = { nav.popBackStack() })
        }

        // Chat general
        composable(Screen.ChatGeneral.route) { ChatGeneralScreen(nav) }

        // Conversación 1–1
        composable(
            route = Routes.CONVERSATION, // "conversation/{peerUid}"
            arguments = listOf(navArgument(Routes.Args.PeerUid) { type = NavType.StringType })
        ) { backStackEntry ->
            val peerUid = backStackEntry.arguments?.getString(Routes.Args.PeerUid) ?: return@composable
            ConversationScreen(nav, peerUid)
        }

        composable(Screen.CareAssistant.route) { Text("Asistente de cuidados") }
        composable(Screen.ReviewsHome.route) { Text("Listado de reseñas") }
        composable(Screen.ReviewCreate.route) { Text("Crear reseña") }
        composable(
            Screen.ReviewDetail.route,
            arguments = listOf(navArgument("reviewId") { type = NavType.StringType })
        ) { bs ->
            Text("Detalle reseña: " + bs.arguments?.getString("reviewId").orEmpty())
        }

        composable(Screen.AdoptionsList.route) { Text("Listado de adopciones") }
        composable(
            Screen.AdoptionDetail.route,
            arguments = listOf(navArgument("adoptionId") { type = NavType.StringType })
        ) { bs ->
            Text("Detalle adopción: " + bs.arguments?.getString("adoptionId").orEmpty())
        }

        composable(Screen.NotificationsSettings.route) { Text("Ajustes de notificaciones") }
        composable(Screen.Profile.route) { ProfileScreen(nav) }
    }
}
