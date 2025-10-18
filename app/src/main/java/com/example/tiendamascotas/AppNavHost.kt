// FILE: app/src/main/java/com/example/tiendamascotas/AppNavHost.kt
package com.example.tiendamascotas

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tiendamascotas.home.HomeScreen
import com.example.tiendamascotas.navigation.Screen
import com.example.tiendamascotas.ui.auth.LoginScreen
import com.google.firebase.auth.FirebaseAuth
import com.example.tiendamascotas.profile.ProfileScreen
import com.example.tiendamascotas.reports.ui.ReportsFeedScreen
import com.example.tiendamascotas.reports.ui.CreateReportScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    // ✅ Usa firebaseAuth (nombre claro) y NO una variable 'auth' perdida
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var isLoggedIn by remember { mutableStateOf(firebaseAuth.currentUser != null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fb ->
            isLoggedIn = fb.currentUser != null
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    // 🔐 Gate de autenticación como startDestination
    NavHost(navController = nav, startDestination = "auth/gate") {

        // Decide a dónde ir y limpia el back stack del gate
        composable("auth/gate") {
            LaunchedEffect(isLoggedIn) {
                nav.navigate(if (isLoggedIn) Screen.Home.route else Screen.Login.route) {
                    popUpTo("auth/gate") { inclusive = true }
                }
            }
        }

        // Login real
        composable(Screen.Login.route) { LoginScreen(nav) }

        // Home
        composable(Screen.Home.route) { HomeScreen(nav) }

        // --- Rutas stub que ya tenías ---
        composable(Screen.CreateReport.route) { Text("Crear reporte") }
        composable(Screen.Map.route) { Text("Mapa de reportes") }
        composable(Screen.ReportsFeed.route) { ReportsFeedScreen(nav) }

        composable(
            route = "report/create?editId={editId}",
            arguments = listOf(navArgument("editId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { backStack -> CreateReportScreen(nav, backStack) }

        composable(Screen.CareAssistant.route) { Text("Asistente de cuidados") }
        composable(Screen.ReviewsHome.route) { Text("Listado de reseñas") }
        composable(Screen.ReviewCreate.route) { Text("Crear reseña") }
        composable(
            Screen.ReviewDetail.route,
            arguments = listOf(navArgument("reviewId") { type = NavType.StringType })
        ) { bs ->
            Text("Detalle reseña: " + bs.arguments?.getString("reviewId").orEmpty())
        }
        composable(Screen.ChatGeneral.route) { Text("Chat general") }
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
