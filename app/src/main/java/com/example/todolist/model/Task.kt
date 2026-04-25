package com.example.todolist.model

import com.google.firebase.Timestamp

/**
 * Modelo de datos para una tarea dentro de una lista.
 */
data class Task(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val completed: Boolean = false,
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
