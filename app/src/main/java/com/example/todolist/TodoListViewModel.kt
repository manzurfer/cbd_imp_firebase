package com.example.todolist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolist.model.TodoList
import com.example.todolist.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodoListViewModel @Inject constructor(
    private val repository: TodoRepository
) : ViewModel() {

    var lists by mutableStateOf<List<TodoList>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadLists()
    }

    /**
     * Escucha en tiempo real todas las listas del usuario.
     */
    private fun loadLists() {
        viewModelScope.launch {
            repository.getMyLists()
                .catch { e ->
                    errorMessage = e.message
                    isLoading = false
                }
                .collect { result ->
                    lists = result
                    isLoading = false
                }
        }
    }

    /**
     * Crea una nueva lista.
     */
    fun createList(name: String) {
        if (name.isBlank()) {
            errorMessage = "El nombre no puede estar vacío"
            return
        }
        viewModelScope.launch {
            try {
                repository.createList(name)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /**
     * Elimina una lista (solo owner).
     */
    fun deleteList(listId: String) {
        viewModelScope.launch {
            try {
                repository.deleteList(listId)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }
}
