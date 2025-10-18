package com.example.tiendamascotas.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Inicio")

    object CreateReport : Screen("reports/create", "Reportar")
    object Map : Screen("reports/map", "Mapa")
    object ReportDetail : Screen("reports/detail/{reportId}", "Detalle Reporte") {
        fun build(reportId: String) = "reports/detail/$reportId"
    }

    object CareAssistant : Screen("care/assistant", "Asistente")
    object ReviewsHome : Screen("reviews/home", "Reseñas")
    object ReviewCreate : Screen("reviews/create", "Nueva Reseña")
    object ReviewDetail : Screen("reviews/detail/{reviewId}", "Detalle Reseña") {
        fun build(reviewId: String) = "reviews/detail/$reviewId"
    }

    object ChatGeneral : Screen("chat/general", "Chat")
    object AdoptionsList : Screen("adoptions/list", "Adopciones")
    object AdoptionDetail : Screen("adoptions/detail/{adoptionId}", "Detalle Adopción") {
        fun build(adoptionId: String) = "adoptions/detail/$adoptionId"
    }

    object NotificationsSettings : Screen("settings/notifications", "Notificaciones")
    object Profile : Screen("profile", "Perfil")
}
