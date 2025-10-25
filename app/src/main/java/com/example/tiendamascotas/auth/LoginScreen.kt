package com.example.tiendamascotas.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.tiendamascotas.R
import com.example.tiendamascotas.navigation.Screen
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(nav: NavHostController) {
    val focus = LocalFocusManager.current
    val auth = remember { FirebaseAuth.getInstance() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {


        Image(
            painter = painterResource(id = R.drawable.login_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )


        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x99000000)),
                        startY = 400f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        Text(
            text = "",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
        )


        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "¡Bienvenido a Pet Garden!",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )


            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp))
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }

            Spacer(Modifier.height(12.dp))


            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp))
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }

            if (errorText != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    focus.clearFocus()
                    errorText = null
                    loading = true
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnCompleteListener { task ->
                            loading = false
                            if (task.isSuccessful) {
                                nav.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else {
                                errorText = task.exception?.localizedMessage
                                    ?: "No se pudo iniciar sesión."
                            }
                        }
                },
                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Iniciar sesión")
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = { /* nav.navigate("auth/register") */ },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Crear cuenta", color = Color.White) }
        }
    }
}
