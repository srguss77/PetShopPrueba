package com.example.tiendamascotas.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.tiendamascotas.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(nav: NavHostController, vm: LoginViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Iniciar sesión") }) }) { padd ->
        Column(Modifier.fillMaxSize().padding(padd).padding(16.dp)) {
            OutlinedTextField(
                value = ui.email, onValueChange = vm::onEmail,
                label = { Text("Correo") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.password, onValueChange = vm::onPassword,
                label = { Text("Contraseña") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.signIn {
                        nav.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Entrar") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    vm.signUp {
                        nav.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Crear cuenta") }

            if (ui.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
