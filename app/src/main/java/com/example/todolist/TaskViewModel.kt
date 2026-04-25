package com.example.todolist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolist.model.MemberRole
import com.example.todolist.model.Task
import com.example.todolist.model.TodoList
import com.example.todolist.repository.TodoRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val listId: String = savedStateHandle.get<String>("listId") ?: ""

    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set

    var todoList by mutableStateOf<TodoList?>(null)
        private set

    var currentUserRole by mutableStateOf(MemberRole.VIEWER)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    init {
        if (listId.isNotEmpty()) {
            loadListInfo()
            loadTasks()
        }
    }

    /**
     * Carga la información de la lista y determina el rol del usuario.
     */
    private fun loadListInfo() {
        viewModelScope.launch {
            try {
                val list = repository.getList(listId)
                todoList = list
                currentUserRole = list?.getRoleForUser(currentUid) ?: MemberRole.VIEWER
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /**
     * Escucha las tareas en tiempo real.
     */
    private fun loadTasks() {
        viewModelScope.launch {
            repository.getTasks(listId)
                .catch { e ->
                    errorMessage = e.message
                    isLoading = false
                }
                .collect { result ->
                    tasks = result
                    isLoading = false
                }
        }
    }

    /** ¿Puede el usuario crear/editar tareas? */
    val canEdit: Boolean
        get() = currentUserRole == MemberRole.OWNER || currentUserRole == MemberRole.EDITOR

    /** ¿Es el owner de la lista? */
    val isOwner: Boolean
        get() = currentUserRole == MemberRole.OWNER

    /**
     * Añade una nueva tarea.
     */
    fun addTask(title: String, description: String = "") {
        if (!canEdit) {
            errorMessage = "No tienes permisos para añadir tareas"
            return
        }
        if (title.isBlank()) {
            errorMessage = "El título no puede estar vacío"
            return
        }
        viewModelScope.launch {
            try {
                repository.addTask(listId, title, description)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /**
     * Actualiza una tarea existente.
     */
    fun updateTask(taskId: String, title: String, description: String) {
        if (!canEdit) {
            errorMessage = "No tienes permisos para editar tareas"
            return
        }
        viewModelScope.launch {
            try {
                repository.updateTask(listId, taskId, title, description)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /**
     * Alterna el estado de completada de una tarea.
     */
    fun toggleTask(taskId: String, completed: Boolean) {
        if (!canEdit) {
            errorMessage = "No tienes permisos para modificar tareas"
            return
        }
        viewModelScope.launch {
            try {
                repository.toggleTask(listId, taskId, completed)
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /**
     * Elimina una tarea.
     */
    fun deleteTask(taskId: String) {
        if (!canEdit) {
            errorMessage = "No tienes permisos para eliminar tareas"
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteTask(listId, taskId)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /**
     * Invita a un miembro a la lista.
     */
    fun inviteMember(email: String, role: MemberRole) {
        if (!isOwner) {
            errorMessage = "Solo el propietario puede invitar miembros"
            return
        }
        viewModelScope.launch {
            val result = repository.inviteMember(listId, email, role)
            result.onFailure { e ->
                errorMessage = e.message
            }
            result.onSuccess {
                loadListInfo() // Recargar info de la lista
                errorMessage = null
            }
        }
    }

    /**
     * Cambia el rol de un miembro.
     */
    fun updateMemberRole(targetUid: String, newRole: MemberRole) {
        if (!isOwner) return
        viewModelScope.launch {
            try {
                repository.updateMemberRole(listId, targetUid, newRole)
                loadListInfo()
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    /**
     * Elimina un miembro de la lista.
     */
    fun removeMember(targetUid: String) {
        if (!isOwner) return
        viewModelScope.launch {
            try {
                repository.removeMember(listId, targetUid)
                loadListInfo()
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }
}
