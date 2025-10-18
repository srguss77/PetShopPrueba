package com.example.tiendamascotas.navigation

sealed class Screen(val route: String, val title: String) {
    object Login : Screen("auth/login", "Iniciar sesión")
    object Home : Screen("home", "Inicio")

    object ReportsFeed : Screen("reports", "Reportes")
    object CreateReport : Screen("report/create", "Crear reporte") {
        fun editRoute(id: String) = "report/create?editId=$id"
    }
    object ReportDetail : Screen("report/detail/{reportId}", "Detalle")
    object Map : Screen("reports/map", "Mapa")


    object CareAssistant : Screen("care/assistant", "Asistente")

    object ReviewsHome : Screen("reviews/home", "Reseñas")
    object ReviewCreate : Screen("reviews/create", "Nueva reseña")
    object ReviewDetail : Screen("reviews/detail/{reviewId}", "Detalle reseña") {
        fun route(reviewId: String) = "reviews/detail/$reviewId"
    }

    object ChatGeneral : Screen("chat/general", "Chat")

    object AdoptionsList : Screen("adoptions/list", "Adopciones")
    object AdoptionDetail : Screen("adoptions/detail/{adoptionId}", "Adopción") {
        fun route(adoptionId: String) = "adoptions/detail/$adoptionId"
    }

    object NotificationsSettings : Screen("settings/notifications", "Notificaciones")
    object Profile : Screen("profile", "Perfil") // ✅ única definición
}
