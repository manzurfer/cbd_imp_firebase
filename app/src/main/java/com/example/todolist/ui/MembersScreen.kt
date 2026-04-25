package com.example.todolist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todolist.model.MemberRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    listName: String,
    members: Map<String, String>,       // uid -> role
    memberEmails: Map<String, String>,   // uid -> email
    currentUserUid: String,
    onInvite: (String, MemberRole) -> Unit,
    onChangeRole: (String, MemberRole) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteEmail by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf(MemberRole.EDITOR) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Miembros",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            listName,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showInviteDialog = true },
                containerColor = Color(0xFF667eea),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Invitar", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "${members.size} miembro${if (members.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(members.entries.toList(), key = { it.key }) { (uid, roleStr) ->
                    val email = memberEmails[uid] ?: uid
                    val role = MemberRole.fromString(roleStr)
                    val isCurrentUser = uid == currentUserUid
                    val isOwner = role == MemberRole.OWNER

                    MemberCard(
                        email = email,
                        role = role,
                        isCurrentUser = isCurrentUser,
                        isOwner = isOwner,
                        onChangeRole = { newRole -> onChangeRole(uid, newRole) },
                        onRemove = { showRemoveConfirm = uid }
                    )
                }
            }

            // Error snackbar
            errorMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = onClearError) {
                            Text("OK", color = Color.White)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Text(msg)
                }
            }
        }
    }

    // Diálogo de invitación
    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = {
                showInviteDialog = false
                inviteEmail = ""
                inviteRole = MemberRole.EDITOR
            },
            title = { Text("Invitar miembro", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text("Email del usuario") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Rol:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoleChip(
                            label = "✏️ Editor",
                            selected = inviteRole == MemberRole.EDITOR,
                            onClick = { inviteRole = MemberRole.EDITOR },
                            modifier = Modifier.weight(1f)
                        )
                        RoleChip(
                            label = "👁️ Visor",
                            selected = inviteRole == MemberRole.VIEWER,
                            onClick = { inviteRole = MemberRole.VIEWER },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Info sobre el rol
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3E5F5),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when (inviteRole) {
                                MemberRole.EDITOR -> "El editor puede crear, editar y eliminar tareas."
                                MemberRole.VIEWER -> "El visor solo puede ver las tareas, sin modificarlas."
                                MemberRole.OWNER -> ""
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF6A1B9A),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onInvite(inviteEmail, inviteRole)
                        showInviteDialog = false
                        inviteEmail = ""
                        inviteRole = MemberRole.EDITOR
                    },
                    enabled = inviteEmail.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667eea)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Invitar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showInviteDialog = false
                    inviteEmail = ""
                    inviteRole = MemberRole.EDITOR
                }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Confirmación de eliminación
    showRemoveConfirm?.let { uid ->
        val email = memberEmails[uid] ?: uid
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = null },
            title = { Text("Eliminar miembro") },
            text = { Text("¿Eliminar a $email de esta lista?") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove(uid)
                        showRemoveConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun MemberCard(
    email: String,
    role: MemberRole,
    isCurrentUser: Boolean,
    isOwner: Boolean,
    onChangeRole: (MemberRole) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val roleIcon = when (role) {
        MemberRole.OWNER -> "👑"
        MemberRole.EDITOR -> "✏️"
        MemberRole.VIEWER -> "👁️"
    }

    val roleLabel = when (role) {
        MemberRole.OWNER -> "Propietario"
        MemberRole.EDITOR -> "Editor"
        MemberRole.VIEWER -> "Visor"
    }

    val roleColor = when (role) {
        MemberRole.OWNER -> Color(0xFFFFD700)
        MemberRole.EDITOR -> Color(0xFF43e97b)
        MemberRole.VIEWER -> Color(0xFF90CAF9)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                shape = RoundedCornerShape(50),
                color = roleColor.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(roleIcon, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF667eea).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Tú",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF667eea),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Solo si no es owner ni es el usuario actual
            if (!isOwner && !isCurrentUser) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (role != MemberRole.EDITOR) {
                            DropdownMenuItem(
                                text = { Text("✏️ Hacer Editor") },
                                onClick = {
                                    onChangeRole(MemberRole.EDITOR)
                                    expanded = false
                                }
                            )
                        }
                        if (role != MemberRole.VIEWER) {
                            DropdownMenuItem(
                                text = { Text("👁️ Hacer Visor") },
                                onClick = {
                                    onChangeRole(MemberRole.VIEWER)
                                    expanded = false
                                }
                            )
                        }
                        Divider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "🗑️ Eliminar",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                onRemove()
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF667eea).copy(alpha = 0.15f) else Color(0xFFF5F5F5),
        onClick = onClick
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color(0xFF667eea) else Color.Gray
            )
        }
    }
}
