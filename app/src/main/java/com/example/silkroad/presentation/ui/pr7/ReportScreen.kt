package com.example.silkroad.presentation.ui.pr7

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.silkroad.BuildConfig
import com.example.silkroad.data.firebase.ReportScope
import com.example.silkroad.domain.model.Report
import com.example.silkroad.domain.model.qualityRequirements
import com.example.silkroad.presentation.viewmodel.ReportsViewModel

@Composable
fun ReportScreen(vm: ReportsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var quality by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = quality) { quality = false }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Reportes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { quality = !quality }) { Text(if (quality) "Volver a reportes" else "Calidad P8") }
        }
        if (quality) {
            Text("Casos de caja negra", style = MaterialTheme.typography.titleMedium)
            Text("Criterios de aceptación. Los resultados y evidencias se registran en el informe; esta lista no ejecuta pruebas.",
                style = MaterialTheme.typography.bodySmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(qualityRequirements) { requirement ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${requirement.id} · ${requirement.title}", fontWeight = FontWeight.Bold)
                            Text(requirement.acceptance, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else if (state.uid == null) {
            var email by rememberSaveable { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Acceso a reportes", style = MaterialTheme.typography.headlineSmall)
                Text("Entra con tu cuenta para registrar y consultar reportes.")
                Text(if (BuildConfig.FIREBASE_EMULATORS) "Entorno de pruebas local · Firebase Emulator Suite"
                    else "Firebase · ${BuildConfig.FIREBASE_PROJECT_ID}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("auth-email"), enabled = !state.authenticating)
                OutlinedTextField(password, { password = it }, label = { Text("Contraseña de reportes") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("auth-password"), enabled = !state.authenticating)
                state.authError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("auth-error")) }
                Button(onClick = { vm.login(email, password) }, enabled = !state.authenticating,
                    modifier = Modifier.fillMaxWidth().testTag("auth-login")) {
                    Text(if (state.authenticating) "Conectando..." else "Entrar a reportes")
                }
                OutlinedButton(onClick = { vm.login(email, password, true) }, enabled = !state.authenticating,
                    modifier = Modifier.fillMaxWidth().testTag("auth-register")) { Text("Crear cuenta de reportes") }
                Text("La cuenta demo de la tienda no da acceso a los reportes privados.", style = MaterialTheme.typography.bodySmall)
            }
        } else key(state.uid) {
            var query by rememberSaveable { mutableStateOf("") }
            var create by remember { mutableStateOf(false) }
            var deleting by remember { mutableStateOf<Report?>(null) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.email, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::logout, modifier = Modifier.testTag("auth-logout")) { Text("Salir de reportes") }
            }
            SelectionContainer { Text("UID: ${state.uid}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("session-uid")) }
            TabRow(selectedTabIndex = if (state.scope == ReportScope.PRIVATE) 0 else 1) {
                Tab(state.scope == ReportScope.PRIVATE, onClick = { query = ""; vm.selectScope(ReportScope.PRIVATE) },
                    enabled = !state.busy, text = { Text("Mis reportes P10") }, modifier = Modifier.testTag("scope-private"))
                Tab(state.scope == ReportScope.SHARED, onClick = { query = ""; vm.selectScope(ReportScope.SHARED) },
                    enabled = !state.busy, text = { Text("Compartidos P9") }, modifier = Modifier.testTag("scope-shared"))
            }
            Text(if (state.scope == ReportScope.PRIVATE) "Solo tú puedes leer, editar y eliminar estos reportes."
                else "Reportes de prueba visibles y editables por las cuentas de este proyecto.", style = MaterialTheme.typography.bodySmall)
            Text(when {
                state.snapshot.error != null -> "Error de consulta: ${state.snapshot.error}"
                state.snapshot.pending -> "Cambios pendientes de sincronizar"
                state.snapshot.cached -> "Copia local · esperando conexión con el servidor"
                else -> "Sincronizado con el servidor"
            }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("sync-state"))
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("operation-message")) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar reporte") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
                    modifier = Modifier.weight(1f).testTag("report-search"))
                FilledTonalButton(onClick = { create = true }, enabled = !state.busy, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Add, null); Text("Nuevo")
                }
            }
            val filtered = state.snapshot.reports.filter {
                query.isBlank() || it.titulo.contains(query, true) || it.equipo.contains(query, true)
            }
            if (filtered.isEmpty()) Text(if (query.isBlank()) "No hay reportes." else "Sin coincidencias.", modifier = Modifier.padding(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(filtered, key = { it.id }) { report ->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("report-${report.id}")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(report.titulo, style = MaterialTheme.typography.titleMedium)
                            Text(report.equipo, fontWeight = FontWeight.Bold)
                            Text(report.descripcion)
                            Text("Estado: ${report.status} · Prioridad: ${report.prioridad}", style = MaterialTheme.typography.bodySmall)
                            SelectionContainer { Text("ID: ${report.id}\nCreado por: ${report.creadoPor}", style = MaterialTheme.typography.labelSmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    vm.update(report.id, "status", Report.estados[(Report.estados.indexOf(report.status) + 1) % Report.estados.size])
                                }, enabled = !state.busy, modifier = Modifier.testTag("status-${report.id}")) { Text("Estado") }
                                TextButton(onClick = {
                                    vm.update(report.id, "prioridad", Report.prioridades[(Report.prioridades.indexOf(report.prioridad) + 1) % Report.prioridades.size])
                                }, enabled = !state.busy, modifier = Modifier.testTag("priority-${report.id}")) { Text("Prioridad") }
                                IconButton(onClick = { deleting = report }, enabled = !state.busy, modifier = Modifier.testTag("delete-${report.id}")) {
                                    Icon(Icons.Default.Delete, "Eliminar ${report.titulo}", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            if (create) NewReportDialog(onDismiss = { create = false }) { title, equipment, description, priority ->
                query = title.trim()
                vm.create(title, equipment, description, priority); create = false
            }
            deleting?.let { report ->
                AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Eliminar reporte") },
                    text = { Text("¿Eliminar ${report.titulo}? Esta acción quita el reporte del servidor.") },
                    confirmButton = { TextButton(onClick = { vm.delete(report.id); deleting = null }, modifier = Modifier.testTag("confirm-delete")) { Text("Eliminar") } },
                    dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
            }
        }
    }
}

@Composable
private fun NewReportDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Media") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuevo reporte") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth().testTag("report-title"), singleLine = true)
            OutlinedTextField(equipment, { equipment = it }, label = { Text("Equipo o producto") }, modifier = Modifier.fillMaxWidth().testTag("report-equipment"), singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth().testTag("report-description"), maxLines = 4)
            Text("Prioridad")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Report.prioridades.forEach { value ->
                    FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(value, style = MaterialTheme.typography.labelSmall) })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("report-validation")) }
        }
    }, confirmButton = { Button(onClick = {
        error = Report.validationError(title, equipment, description)
        if (error == null) onSave(title, equipment, description, priority)
    }, modifier = Modifier.testTag("report-save")) { Text("Guardar reporte") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
