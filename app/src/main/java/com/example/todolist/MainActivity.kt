package com.example.todolist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthScreen()
                }
            }
        }
    }
}

@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    // Variables para guardar lo que el usuario escribe
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Observamos el estado desde el ViewModel
    val authState = viewModel.authState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "ToDo Collab", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mostrar mensajes de error o éxito
        when (authState) {
            is AuthState.Error -> {
                Text(text = authState.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }
            is AuthState.Success -> {
                Text(text = "¡Autenticación exitosa!", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }
            is AuthState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
            }
            else -> {}
        }

        Button(
            onClick = { viewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState != AuthState.Loading
        ) {
            Text("Iniciar Sesión")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { viewModel.register(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState != AuthState.Loading
        ) {
            Text("Registrarse")
        }
    }
}