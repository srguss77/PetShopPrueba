package com.example.tiendamascotas

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

// Adopciones UI
import com.example.tiendamascotas.adoptions.ui.AdoptionsFeedScreen
import com.example.tiendamascotas.adoptions.ui.CreateAdoptionScreen

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

        // Reportar (creación directa) + edición con parámetro
        composable(Screen.CreateReport.route) { backStack ->
            CreateReportScreen(nav, backStack)
        }
        composable(
            route = "report/create?editId={editId}",
            arguments = listOf(
                navArgument("editId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStack ->
            CreateReportScreen(nav, backStack)
        }

        // Feed de reportes
        composable(Screen.ReportsFeed.route) { ReportsFeedScreen(nav) }

        // Mapa (soporta Screen.Map y Routes.MAP)
        composable(Screen.Map.route) { ReportsMapLibreScreen() }
        composable(Routes.MAP) { ReportsMapLibreScreen() }

        // Chat
        composable(Screen.ChatGeneral.route) { ChatGeneralScreen(nav) }
        composable(Routes.CHAT) { ChatGeneralScreen(nav) }

        // ------------------ ADOPCIONES ------------------
        // Listado
        composable(Screen.AdoptionsList.route) {
            AdoptionsFeedScreen(nav)
        }

        // ✅ ÚNICA ruta para crear o editar (editId opcional)
        composable(
            route = "adoptions/create?editId={editId}",
            arguments = listOf(
                navArgument("editId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStack ->
            val editId = backStack.arguments?.getString("editId")
            // CreateAdoptionScreen debe aceptar: editId: String? = null
            CreateAdoptionScreen(
                editId = editId,
                onClose = {
                    if (!nav.popBackStack()) {
                        nav.navigate(Screen.AdoptionsList.route)
                    }
                }
            )
        }
        // -------------------------------------------------

        // Perfil y notificaciones
        composable(Screen.NotificationsSettings.route) { /* Ajustes de notificaciones */ }
        composable(Screen.Profile.route) { ProfileScreen(nav) }

        // Conversación (único)
        composable(
            route = Routes.CONVERSATION,
            arguments = listOf(navArgument("peerUid") { type = NavType.StringType })
        ) { backStackEntry ->
            val peerUid = backStackEntry.arguments?.getString("peerUid") ?: return@composable
            ConversationScreen(nav, peerUid)
        }
    }
}
