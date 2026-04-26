package com.example.todolist.repository

import com.example.todolist.model.MemberRole
import com.example.todolist.model.Task
import com.example.todolist.model.TodoList
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio para gestionar listas y tareas en Firestore
 * con listeners en tiempo real y control de permisos.
 */
@Singleton
class TodoRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val listsCollection = firestore.collection("todoLists")
    private val usersCollection = firestore.collection("users")

    private val currentUid: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")

    // ═══════════════════════════════════════════════════════════════════
    //  PERFIL DE USUARIO
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Guarda el perfil del usuario autenticado en la colección 'users'
     * para poder buscar por email al invitar.
     */
    suspend fun saveUserProfile() {
        val user = auth.currentUser ?: return
        val data = mapOf(
            "email" to (user.email ?: ""),
            "uid" to user.uid
        )
        usersCollection.document(user.uid).set(data).await()
    }

    /**
     * Busca un usuario por email en la colección 'users'.
     * Devuelve un par (uid, email) o null si no existe.
     */
    suspend fun findUserByEmail(email: String): Pair<String, String>? {
        val snapshot = usersCollection
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .await()

        if (snapshot.isEmpty) return null
        val doc = snapshot.documents[0]
        return Pair(doc.id, doc.getString("email") ?: email)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LISTAS — CRUD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Escucha en tiempo real todas las listas donde el usuario actual
     * es miembro. Usa snapshot listener de Firestore.
     */
    fun getMyLists(): Flow<List<TodoList>> = callbackFlow {
        val uid = currentUid

        val listener = listsCollection
            .whereGreaterThanOrEqualTo("members.$uid", "")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val lists = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val members = (data["members"] as? Map<*, *>)
                        ?.mapKeys { it.key.toString() }
                        ?.mapValues { it.value.toString() }
                        ?: emptyMap()

                    // Filtrar manualmente: asegurar que el uid está en members
                    if (!members.containsKey(uid)) return@mapNotNull null

                    val memberEmails = (data["memberEmails"] as? Map<*, *>)
                        ?.mapKeys { it.key.toString() }
                        ?.mapValues { it.value.toString() }
                        ?: emptyMap()

                    TodoList(
                        id = doc.id,
                        name = data["name"]?.toString() ?: "",
                        ownerId = data["ownerId"]?.toString() ?: "",
                        createdAt = data["createdAt"] as? Timestamp,
                        members = members,
                        memberEmails = memberEmails
                    )
                } ?: emptyList()

                trySend(lists)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Crea una nueva lista y establece al usuario actual como owner.
     */
    suspend fun createList(name: String): String {
        val uid = currentUid
        val email = auth.currentUser?.email ?: ""

        val data = mapOf(
            "name" to name,
            "ownerId" to uid,
            "createdAt" to Timestamp.now(),
            "members" to mapOf(uid to "owner"),
            "memberEmails" to mapOf(uid to email)
        )

        val docRef = listsCollection.add(data).await()
        return docRef.id
    }

    /**
     * Elimina una lista. Solo el owner puede hacerlo.
     */
    suspend fun deleteList(listId: String) {
        // Primero eliminar todas las tareas de la subcolección
        val tasks = listsCollection.document(listId)
            .collection("tasks")
            .get()
            .await()

        for (task in tasks.documents) {
            task.reference.delete().await()
        }

        listsCollection.document(listId).delete().await()
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MIEMBROS — Invitar, cambiar rol, eliminar
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Invita a un usuario a una lista con un rol determinado.
     * Busca al usuario por email y lo añade a 'members' y 'memberEmails'.
     */
    suspend fun inviteMember(listId: String, email: String, role: MemberRole): Result<Unit> {
        val found = findUserByEmail(email)
            ?: return Result.failure(Exception("No se encontró ningún usuario con ese email"))

        val (targetUid, targetEmail) = found

        if (targetUid == currentUid) {
            return Result.failure(Exception("No puedes invitarte a ti mismo"))
        }

        val docRef = listsCollection.document(listId)

        docRef.update(
            mapOf(
                "members.$targetUid" to role.name.lowercase(),
                "memberEmails.$targetUid" to targetEmail
            )
        ).await()

        return Result.success(Unit)
    }

    /**
     * Cambia el rol de un miembro existente.
     */
    suspend fun updateMemberRole(listId: String, targetUid: String, newRole: MemberRole) {
        listsCollection.document(listId)
            .update("members.$targetUid", newRole.name.lowercase())
            .await()
    }

    /**
     * Elimina un miembro de la lista.
     */
    suspend fun removeMember(listId: String, targetUid: String) {
        val docRef = listsCollection.document(listId)
        val snapshot = docRef.get().await()
        val members = (snapshot.data?.get("members") as? Map<*, *>)?.toMutableMap() ?: return
        val memberEmails = (snapshot.data?.get("memberEmails") as? Map<*, *>)?.toMutableMap() ?: return

        members.remove(targetUid)
        memberEmails.remove(targetUid)

        docRef.update(
            mapOf(
                "members" to members,
                "memberEmails" to memberEmails
            )
        ).await()
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TAREAS — CRUD en tiempo real
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Escucha en tiempo real las tareas de una lista.
     */
    fun getTasks(listId: String): Flow<List<Task>> = callbackFlow {
        val listener = listsCollection.document(listId)
            .collection("tasks")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val tasks = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Task(
                        id = doc.id,
                        title = data["title"]?.toString() ?: "",
                        description = data["description"]?.toString() ?: "",
                        completed = data["completed"] as? Boolean ?: false,
                        createdBy = data["createdBy"]?.toString() ?: "",
                        createdAt = data["createdAt"] as? Timestamp,
                        updatedAt = data["updatedAt"] as? Timestamp
                    )
                } ?: emptyList()

                trySend(tasks)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Añade una nueva tarea a una lista.
     */
    suspend fun addTask(listId: String, title: String, description: String = "") {
        val data = mapOf(
            "title" to title,
            "description" to description,
            "completed" to false,
            "createdBy" to currentUid,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        listsCollection.document(listId)
            .collection("tasks")
            .add(data)
            .await()
    }

    /**
     * Actualiza el título y la descripción de una tarea.
     */
    suspend fun updateTask(listId: String, taskId: String, title: String, description: String) {
        listsCollection.document(listId)
            .collection("tasks")
            .document(taskId)
            .update(
                mapOf(
                    "title" to title,
                    "description" to description,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
    }

    /**
     * Marca o desmarca una tarea como completada.
     */
    suspend fun toggleTask(listId: String, taskId: String, completed: Boolean) {
        listsCollection.document(listId)
            .collection("tasks")
            .document(taskId)
            .update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
    }

    /**
     * Elimina una tarea.
     */
    suspend fun deleteTask(listId: String, taskId: String) {
        listsCollection.document(listId)
            .collection("tasks")
            .document(taskId)
            .delete()
            .await()
    }

    /**
     * Obtiene una lista específica por ID (lectura única, no listener).
     */
    suspend fun getList(listId: String): TodoList? {
        val doc = listsCollection.document(listId).get().await()
        val data = doc.data ?: return null

        val members = (data["members"] as? Map<*, *>)
            ?.mapKeys { it.key.toString() }
            ?.mapValues { it.value.toString() }
            ?: emptyMap()

        val memberEmails = (data["memberEmails"] as? Map<*, *>)
            ?.mapKeys { it.key.toString() }
            ?.mapValues { it.value.toString() }
            ?: emptyMap()

        return TodoList(
            id = doc.id,
            name = data["name"]?.toString() ?: "",
            ownerId = data["ownerId"]?.toString() ?: "",
            createdAt = data["createdAt"] as? Timestamp,
            members = members,
            memberEmails = memberEmails
        )
    }

    /**
     * Observa una lista en tiempo real mediante snapshot listener.
     * Los cambios en miembros, roles o nombre se propagan automáticamente.
     */
    fun observeList(listId: String): Flow<TodoList?> = callbackFlow {
        val listener = listsCollection.document(listId)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (doc == null || !doc.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }

                val data = doc.data ?: run {
                    trySend(null)
                    return@addSnapshotListener
                }

                val members = (data["members"] as? Map<*, *>)
                    ?.mapKeys { it.key.toString() }
                    ?.mapValues { it.value.toString() }
                    ?: emptyMap()

                val memberEmails = (data["memberEmails"] as? Map<*, *>)
                    ?.mapKeys { it.key.toString() }
                    ?.mapValues { it.value.toString() }
                    ?: emptyMap()

                val todoList = TodoList(
                    id = doc.id,
                    name = data["name"]?.toString() ?: "",
                    ownerId = data["ownerId"]?.toString() ?: "",
                    createdAt = data["createdAt"] as? Timestamp,
                    members = members,
                    memberEmails = memberEmails
                )
                trySend(todoList)
            }

        awaitClose { listener.remove() }
    }
}
