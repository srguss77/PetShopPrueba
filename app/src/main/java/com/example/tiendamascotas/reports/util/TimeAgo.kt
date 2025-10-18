package com.example.tiendamascotas.reports.util

import com.google.firebase.Timestamp
import java.util.concurrent.TimeUnit
import kotlin.math.abs

fun timeAgo(ts: Timestamp?): String {
    val t = ts?.toDate()?.time ?: return "hace un momento"
    val now = System.currentTimeMillis()
    val diff = now - t
    val s = TimeUnit.MILLISECONDS.toSeconds(diff)
    val m = TimeUnit.MILLISECONDS.toMinutes(diff)
    val h = TimeUnit.MILLISECONDS.toHours(diff)
    val d = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        s < 60 -> "hace ${abs(s)} s"
        m < 60 -> "hace ${abs(m)} min"
        h < 24 -> "hace ${abs(h)} h"
        d < 7  -> "hace ${abs(d)} d"
        d < 30 -> "hace ${abs(d/7)} sem"
        d < 365 -> "hace ${abs(d/30)} mes"
        else -> "hace ${abs(d/365)} año"
    }
}
