// FILE: app/src/main/java/com/example/tiendamascotas/AppNavHost.kt
package com.example.tiendamascotas

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tiendamascotas.chat.ui.ChatGeneralScreen
import com.example.tiendamascotas.home.HomeScreen
import com.example.tiendamascotas.navigation.Screen
import com.example.tiendamascotas.nav.Routes
import com.example.tiendamascotas.ui.auth.LoginScreen
import com.google.firebase.auth.FirebaseAuth
import com.example.tiendamascotas.profile.ProfileScreen
import com.example.tiendamascotas.reports.ui.ReportsFeedScreen
import com.example.tiendamascotas.reports.ui.CreateReportScreen
import com.example.tiendamascotas.chat.ui.ConversationScreen
import com.example.tiendamascotas.reports.ui.ReportsMapLibreScreen

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
                }
            }
        }

        composable(Screen.Login.route) { LoginScreen(nav) }
        composable(Screen.Home.route) { HomeScreen(nav) }

        // Stubs y otras rutas existentes
        composable(Screen.CreateReport.route) { Text("Crear reporte") }
        composable(Screen.Map.route) { ReportsMapLibreScreen() }
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

        composable(Screen.ChatGeneral.route) { ChatGeneralScreen(nav) }
        // ✅ Solo un alias extra si lo usabas:
        composable(Routes.CHAT) { ChatGeneralScreen(nav) }

        composable(Screen.AdoptionsList.route) { Text("Listado de adopciones") }
        composable(
            Screen.AdoptionDetail.route,
            arguments = listOf(navArgument("adoptionId") { type = NavType.StringType })
        ) { bs ->
            Text("Detalle adopción: " + bs.arguments?.getString("adoptionId").orEmpty())
        }
        composable(Screen.NotificationsSettings.route) { Text("Ajustes de notificaciones") }

        // ✅ ÚNICO bloque para conversación (sin duplicados)
        composable(
            route = Routes.CONVERSATION,
            arguments = listOf(navArgument("peerUid") { type = NavType.StringType })
        ) { backStackEntry ->
            val peerUid = backStackEntry.arguments?.getString("peerUid") ?: return@composable
            ConversationScreen(nav, peerUid)
        }

        composable(Screen.Profile.route) { ProfileScreen(nav) }
    }
}
