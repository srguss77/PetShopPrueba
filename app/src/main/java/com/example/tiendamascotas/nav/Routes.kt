// FILE: app/src/main/java/com/example/tiendamascotas/nav/Routes.kt
package com.example.tiendamascotas.nav

object Routes {
    const val REPORTS = "reports"
    const val CHAT = "chat"

    // ✨ Nueva ruta única de conversación (compat con nav.navigate("conversation/{peerUid}"))
    const val CONVERSATION = "conversation/{peerUid}"

    const val PROFILE = "profile/{uid}"
    object Args {
        const val PeerUid = "peerUid"
    }

    fun conversation(peerUid: String) = "conversation/$peerUid"
}
