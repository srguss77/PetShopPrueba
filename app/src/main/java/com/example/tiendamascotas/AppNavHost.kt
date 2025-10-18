package com.example.tiendamascotas

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tiendamascotas.navigation.Screen
import com.example.tiendamascotas.home.HomeScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    // 👇 startDestination correcto (no 'home' a secas)
    NavHost(navController = nav, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) { HomeScreen(nav) }

        composable(Screen.CreateReport.route) { Text("Crear reporte") }
        composable(Screen.Map.route) { Text("Mapa de reportes") }
        composable(
            Screen.ReportDetail.route,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("reportId").orEmpty()
            Text("Detalle reporte: $id")
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

        composable(Screen.ChatGeneral.route) { Text("Chat general") }

        composable(Screen.AdoptionsList.route) { Text("Listado de adopciones") }
        composable(
            Screen.AdoptionDetail.route,
            arguments = listOf(navArgument("adoptionId") { type = NavType.StringType })
        ) { bs ->
            Text("Detalle adopción: " + bs.arguments?.getString("adoptionId").orEmpty())
        }

        composable(Screen.NotificationsSettings.route) { Text("Ajustes de notificaciones") }
        composable(Screen.Profile.route) { Text("Perfil") }
    }
}
