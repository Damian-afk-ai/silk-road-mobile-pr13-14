package com.example.silkroad.presentation.ui.pr7

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.silkroad.data.firebase.ChatService
import com.example.silkroad.domain.model.ChatMessage
import com.example.silkroad.domain.model.UserPermissions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * [TAREA 1: SISTEMA DE CHAT EN TIEMPO REAL]
 * Pantalla de chat conectada a Firestore ('messages') con soporte para:
 * 1. Identificación de emisor y receptor.
 * 2. Selección de dispositivo (Administrador, Agente, Usuario 1, Usuario 2).
 * 3. Interfaz de envío (TextField y botón).
 * 4. Historial en tiempo real con hora exacta de envío.
 * 5. Validación de mensajes vacíos.
 * 6. UX con SnackBar al enviar.
 * 7. Estadísticas de mensajes por dispositivo.
 * 8. Botón papelera para limpiar el historial en Firestore.
 * [REGLA DE ORO]: Control granular por permisos ('can_send_messages', 'can_clear_chat').
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatService: ChatService = remember { ChatService() },
    onDeepLinkUrlInput: (String) -> Unit = {}
) {
    val devices = listOf("Administrador", "Agente", "Usuario 1", "Usuario 2")
    var sender by rememberSaveable { mutableStateOf("Administrador") }
    var receiver by rememberSaveable { mutableStateOf("Agente") }

    // [REGLA DE ORO]: Permisos granulares asignados dinámicamente según emisor o URL
    var currentPermissions by remember(sender) {
        mutableStateOf(UserPermissions.presetFor(sender))
    }

    var textInput by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showUrlSimulatorDialog by remember { mutableStateOf(false) }

    // Observación en tiempo real de los mensajes de Firestore
    val messages by remember(sender, receiver) {
        chatService.observeMessages(sender, receiver)
    }.collectAsState(initial = emptyList())

    // Auto-scroll al final cuando llega un nuevo mensaje
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Silk Road · Simulación de chat PR7", style = MaterialTheme.typography.titleMedium)
                        Text("Los roles de esta demostración no autorizan el acceso a los reportes de Firebase.", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Emisor: $sender → Receptor: $receiver",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Botón para simular Deep Link por URL
                    IconButton(onClick = { showUrlSimulatorDialog = true }) {
                        Icon(Icons.Default.Link, "Simular Deep Link de Permisos")
                    }
                    // [Punto 8]: Botón de papelera para limpiar historial en Firestore
                    IconButton(onClick = {
                        if (!currentPermissions.hasPermission(UserPermissions.CAN_CLEAR_CHAT)) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Acceso Denegado: No tienes el permiso '${UserPermissions.CAN_CLEAR_CHAT}'.")
                            }
                        } else {
                            showClearConfirmDialog = true
                        }
                    }) {
                        Icon(Icons.Default.Delete, "Borrar historial del chat")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // [Puntos 1 y 2]: Selector de Emisor y Receptor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dropdown Emisor
                        Box(modifier = Modifier.weight(1f)) {
                            DeviceDropdown(
                                label = "Dispositivo Emisor",
                                selected = sender,
                                options = devices,
                                onSelected = { newSender ->
                                    sender = newSender
                                    if (receiver == newSender) {
                                        receiver = devices.first { it != newSender }
                                    }
                                }
                            )
                        }

                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)

                        // Dropdown Receptor
                        Box(modifier = Modifier.weight(1f)) {
                            DeviceDropdown(
                                label = "Dispositivo Receptor",
                                selected = receiver,
                                options = devices.filter { it != sender },
                                onSelected = { receiver = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Badge de Permisos Granulares Activos
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Permisos activos: ${currentPermissions.permissions.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // [Punto 7]: Contador de estadísticas de mensajes enviados
            val senderCount = messages.count { it.sender == sender }
            val receiverCount = messages.count { it.sender == receiver }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$sender: $senderCount enviados", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("$receiver: $receiverCount enviados", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("Total: ${messages.size}", style = MaterialTheme.typography.bodySmall)
                }
            }

            // [Punto 4]: Historial en tiempo real de la conversación
            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay mensajes aún entre $sender y $receiver.\n¡Escribe el primero!", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id.ifEmpty { "${it.sender}_${it.timestamp}" } }) { msg ->
                            val isMe = msg.sender == sender
                            MessageBubble(message = msg, isMe = isMe)
                        }
                    }
                }
            }

            // [Punto 3 y 5]: Campo de texto y botón para enviar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un mensaje...") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            // Validación de permiso granular
                            if (!currentPermissions.hasPermission(UserPermissions.CAN_SEND_MESSAGES)) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Acceso Denegado: No tienes el permiso '${UserPermissions.CAN_SEND_MESSAGES}'.")
                                }
                                return@IconButton
                            }

                            // [Punto 5]: Evitar el envío de mensajes vacíos
                            if (textInput.trim().isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("No se puede enviar un mensaje vacío.")
                                }
                                return@IconButton
                            }

                            val textToSend = textInput
                            textInput = ""

                            scope.launch {
                                try {
                                    chatService.sendMessage(sender, receiver, textToSend)
                                    // [Punto 6]: SnackBar de confirmación visual
                                    snackbarHostState.showSnackbar("Mensaje enviado a $receiver exitosamente.")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Error al enviar: ${e.message}")
                                }
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar mensaje")
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para borrar historial (Punto 8)
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Limpiar Conversación") },
            text = { Text("¿Deseas eliminar todo el historial en Firestore entre $sender y $receiver?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        scope.launch {
                            try {
                                chatService.clearConversation(sender, receiver)
                                snackbarHostState.showSnackbar("Historial eliminado en Firestore.")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Error al eliminar: ${e.message}")
                            }
                        }
                    }
                ) {
                    Text("Eliminar Todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo para simular asignación dinámica de roles por URL (Deep Link)
    if (showUrlSimulatorDialog) {
        var urlValue by remember {
            mutableStateOf("silkroad://access?user=$sender&perms=can_read_reports,can_send_messages,can_clear_chat")
        }
        AlertDialog(
            onDismissRequest = { showUrlSimulatorDialog = false },
            title = { Text("Simular Deep Link de Permisos") },
            text = {
                Column {
                    Text("Ingresa una URL con parámetros de permisos para evaluar la regla de oro:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlValue,
                        onValueChange = { urlValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL / Deep Link") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUrlSimulatorDialog = false
                        currentPermissions = UserPermissions.fromUrlString(urlValue)
                        onDeepLinkUrlInput(urlValue)
                        scope.launch {
                            snackbarHostState.showSnackbar("Permisos actualizados desde URL para '${currentPermissions.userName}'")
                        }
                    }
                ) {
                    Text("Aplicar URL")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlSimulatorDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isMe: Boolean) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()) }
    val timeString = timeFormat.format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Emisor
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Texto
                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                // [Punto 4]: Hora exacta de envío
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
