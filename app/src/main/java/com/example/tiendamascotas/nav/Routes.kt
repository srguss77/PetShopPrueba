package com.example.tiendamascotas.nav

object Routes {
    const val REPORTS = "reports"
    const val CHAT = "chat"

    // ✅ Ruta del mapa (opción B)
    const val MAP = "reports/map"

    // Conversación 1–1
    const val CONVERSATION = "conversation/{peerUid}"

    // (Si la usas) perfil con argumento
    const val PROFILE = "profile/{uid}"

    const val ASSISTANT_CHAT = "assistant/chat"

    object Args {
        const val PeerUid = "peerUid"
    }

    fun conversation(peerUid: String) = "conversation/$peerUid"
}
