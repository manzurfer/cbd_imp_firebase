package com.example.todolist.model

import com.google.firebase.Timestamp

/**
 * Roles posibles de un miembro en una lista.
 * - OWNER: control total (CRUD tareas, invitar, cambiar roles, eliminar lista)
 * - EDITOR: crear, editar y eliminar sus propias tareas
 * - VIEWER: solo lectura
 */
enum class MemberRole {
    OWNER, EDITOR, VIEWER;

    companion object {
        fun fromString(value: String): MemberRole {
            return try {
                valueOf(value.uppercase())
            } catch (_: Exception) {
                VIEWER
            }
        }
    }
}

/**
 * Modelo de datos para una lista de tareas colaborativa.
 */
data class TodoList(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val createdAt: Timestamp? = null,
    val members: Map<String, String> = emptyMap(),       // uid -> role ("owner", "editor", "viewer")
    val memberEmails: Map<String, String> = emptyMap()    // uid -> email
) {
    /**
     * Obtiene el rol de un usuario en esta lista.
     */
    fun getRoleForUser(uid: String): MemberRole {
        val roleStr = members[uid] ?: return MemberRole.VIEWER
        return MemberRole.fromString(roleStr)
    }

    /**
     * Comprueba si un usuario es miembro de esta lista.
     */
    fun isMember(uid: String): Boolean = members.containsKey(uid)

    /**
     * Comprueba si un usuario es el owner.
     */
    fun isOwner(uid: String): Boolean = members[uid] == "owner"
}
