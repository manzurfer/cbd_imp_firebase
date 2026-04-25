package com.example.todolist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolist.repository.TodoRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
    private val auth: FirebaseAuth,
    private val repository: TodoRepository
) : ViewModel() {

    var authState by mutableStateOf<AuthState>(AuthState.Idle)
        private set

    /** Usuario actual de Firebase (null si no autenticado) */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** Indica si hay una sesión activa al iniciar la app */
    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    init {
        // Si ya existe sesión activa, marcar como Success
        if (auth.currentUser != null) {
            authState = AuthState.Success
        }
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            authState = AuthState.Error("Llena todos los campos")
            return
        }

        authState = AuthState.Loading
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Guardar perfil del usuario en Firestore
                    viewModelScope.launch {
                        try {
                            repository.saveUserProfile()
                        } catch (_: Exception) { }
                    }
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
                    // Guardar perfil del usuario en Firestore
                    viewModelScope.launch {
                        try {
                            repository.saveUserProfile()
                        } catch (_: Exception) { }
                    }
                    authState = AuthState.Success
                } else {
                    authState = AuthState.Error(task.exception?.message ?: "Error al registrar")
                }
            }
    }

    /**
     * Cierra la sesión del usuario actual.
     */
    fun logout() {
        auth.signOut()
        authState = AuthState.Idle
    }
}