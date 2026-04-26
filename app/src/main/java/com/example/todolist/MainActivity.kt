package com.example.todolist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.todolist.ui.MembersScreen
import com.example.todolist.ui.TaskScreen
import com.example.todolist.ui.TodoListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF667eea),
                    secondary = Color(0xFF764ba2),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    error = Color(0xFFEF5350)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()

    // Pantalla inicial según estado de sesión
    val startDestination = if (authViewModel.isLoggedIn) "lists" else "auth"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ── Pantalla de autenticación ────────────────────────────────
        composable("auth") {
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate("lists") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        // ── Pantalla de listas ───────────────────────────────────────
        composable("lists") {
            val listViewModel: TodoListViewModel = hiltViewModel()

            TodoListScreen(
                lists = listViewModel.lists,
                isLoading = listViewModel.isLoading,
                errorMessage = listViewModel.errorMessage,
                onCreateList = { name -> listViewModel.createList(name) },
                onDeleteList = { id -> listViewModel.deleteList(id) },
                onListClick = { id -> navController.navigate("tasks/$id") },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("auth") {
                        popUpTo("lists") { inclusive = true }
                    }
                },
                onClearError = { listViewModel.clearError() }
            )
        }

        // ── Pantalla de tareas ───────────────────────────────────────
        composable(
            route = "tasks/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.StringType })
        ) {
            val taskViewModel: TaskViewModel = hiltViewModel()
            val listName = taskViewModel.todoList?.name ?: "Tareas"

            // Diálogo cuando el usuario es expulsado de la lista
            if (taskViewModel.wasRemoved) {
                AlertDialog(
                    onDismissRequest = { },
                    title = {
                        Text(
                            "Acceso revocado",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text("Ya no tienes acceso a esta lista. Es posible que el propietario te haya eliminado como miembro.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                navController.navigate("lists") {
                                    popUpTo("lists") { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF667eea)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Volver a mis listas")
                        }
                    },
                    shape = RoundedCornerShape(20.dp)
                )
            }

            TaskScreen(
                listName = listName,
                tasks = taskViewModel.tasks,
                isLoading = taskViewModel.isLoading,
                canEdit = taskViewModel.canEdit,
                isOwner = taskViewModel.isOwner,
                errorMessage = taskViewModel.errorMessage,
                onAddTask = { title, desc -> taskViewModel.addTask(title, desc) },
                onToggleTask = { id, completed -> taskViewModel.toggleTask(id, completed) },
                onUpdateTask = { id, title, desc -> taskViewModel.updateTask(id, title, desc) },
                onDeleteTask = { id -> taskViewModel.deleteTask(id) },
                onNavigateToMembers = {
                    navController.navigate("members/${taskViewModel.listId}")
                },
                onBack = { navController.popBackStack() },
                onClearError = { taskViewModel.clearError() }
            )
        }

        // ── Pantalla de miembros ─────────────────────────────────────
        composable(
            route = "members/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.StringType })
        ) { backStackEntry ->
            // Compartir el mismo TaskViewModel de la pantalla de tareas
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry("tasks/{listId}")
            }
            val taskViewModel: TaskViewModel = hiltViewModel(parentEntry)

            MembersScreen(
                listName = taskViewModel.todoList?.name ?: "",
                members = taskViewModel.todoList?.members ?: emptyMap(),
                memberEmails = taskViewModel.todoList?.memberEmails ?: emptyMap(),
                currentUserUid = taskViewModel.currentUid,
                onInvite = { email, role -> taskViewModel.inviteMember(email, role) },
                onChangeRole = { uid, role -> taskViewModel.updateMemberRole(uid, role) },
                onRemove = { uid -> taskViewModel.removeMember(uid) },
                onBack = { navController.popBackStack() },
                errorMessage = taskViewModel.errorMessage,
                onClearError = { taskViewModel.clearError() }
            )
        }
    }
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onAuthSuccess: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authState = viewModel.authState

    // Navegar en caso de éxito
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onAuthSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo / Título
        Text(
            text = "📋",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ToDo Collab",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF667eea)
        )
        Text(
            text = "Listas colaborativas en tiempo real",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Mensajes de estado
        when (authState) {
            is AuthState.Error -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            is AuthState.Loading -> {
                CircularProgressIndicator(
                    color = Color(0xFF667eea),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            else -> {}
        }

        Button(
            onClick = { viewModel.login(email, password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = authState != AuthState.Loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF667eea)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Iniciar Sesión",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { viewModel.register(email, password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = authState != AuthState.Loading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF667eea)
            )
        ) {
            Text(
                "Registrarse",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}