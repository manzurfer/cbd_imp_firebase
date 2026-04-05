package com.example.todolist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Estados posibles de la autenticación
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    var authState by mutableStateOf<AuthState>(AuthState.Idle)
        private set

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            authState = AuthState.Error("Llena todos los campos")
            return
        }

        authState = AuthState.Loading
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    authState = AuthState.Success
                } else {
                    authState = AuthState.Error(task.exception?.message ?: "Error al iniciar sesión")
                }
            }
    }

    fun register(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            authState = AuthState.Error("Llena todos los campos")
            return
        }

        authState = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    authState = AuthState.Success
                } else {
                    authState = AuthState.Error(task.exception?.message ?: "Error al registrar")
                }
            }
    }
}