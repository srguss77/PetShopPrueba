package com.example.tiendamascotas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.tiendamascotas.ui.theme.MascotasTheme
import com.example.tiendamascotas.AppNavHost   // 👈 este import

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MascotasTheme {
                AppNavHost()
            }
        }
    }
}

