package com.example.silkroad.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.silkroad.data.firebase.FirebaseManager
import com.example.silkroad.data.firebase.ReportScope
import com.example.silkroad.data.firebase.ReportService
import com.example.silkroad.data.firebase.ReportSnapshot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ReportsState(
    val uid: String? = null, val email: String = "", val scope: ReportScope = ReportScope.PRIVATE,
    val snapshot: ReportSnapshot = ReportSnapshot(), val authenticating: Boolean = false,
    val busy: Boolean = false, val message: String? = null, val authError: String? = null
)

class ReportsViewModel : ViewModel() {
    private val auth = FirebaseManager.auth
    private val service = ReportService()
    private val _state = MutableStateFlow(ReportsState())
    val state = _state.asStateFlow()
    private var listenerJob: Job? = null
    private var writeJob: Job? = null
    private val authListener = FirebaseAuth.AuthStateListener { firebase ->
        listenerJob?.cancel()
        writeJob?.cancel()
        val user = firebase.currentUser
        _state.update { it.copy(uid = user?.uid, email = user?.email ?: "", snapshot = ReportSnapshot(),
            busy = false, message = null, authError = null) }
        listen()
    }
    init { auth.addAuthStateListener(authListener) }
    private fun listen() {
        listenerJob?.cancel()
        val uid = _state.value.uid ?: return
        val scope = _state.value.scope
        listenerJob = viewModelScope.launch {
            service.observe(scope, uid).collect { result ->
                _state.update { it.copy(snapshot = result) }
            }
        }
    }
    fun selectScope(scope: ReportScope) {
        if (_state.value.busy) return
        _state.update { it.copy(scope = scope, snapshot = ReportSnapshot(), message = null) }
        listen()
    }
    fun login(email: String, password: String, register: Boolean = false) {
        if (_state.value.authenticating) return
        val clean = email.trim()
        if (clean.length > 254 || !android.util.Patterns.EMAIL_ADDRESS.matcher(clean).matches()) {
            _state.update { it.copy(authError = "Ingresa un correo válido.") }; return
        }
        if (password.length !in 8..128) {
            _state.update { it.copy(authError = "La contraseña debe tener entre 8 y 128 caracteres.") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(authenticating = true, authError = null) }
            try {
                if (register) auth.createUserWithEmailAndPassword(clean, password).await()
                else auth.signInWithEmailAndPassword(clean, password).await()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _state.update { it.copy(authError = friendlyError(e)) }
            } finally { _state.update { it.copy(authenticating = false) } }
        }
    }
    fun logout() {
        listenerJob?.cancel()
        writeJob?.cancel()
        // Quitar la información visible antes de notificar el cambio de sesión.
        _state.update { ReportsState(scope = it.scope) }
        auth.signOut()
    }
    fun create(titulo: String, equipo: String, descripcion: String, prioridad: String) = write("Reporte guardado en el servidor.") {
        service.create(_state.value.scope, titulo, equipo, descripcion, prioridad)
    }
    fun update(id: String, field: String, value: String) = write("Cambio confirmado en el servidor.") {
        service.update(_state.value.scope, id, field, value)
    }
    fun delete(id: String) = write("Eliminación confirmada en el servidor.") {
        service.delete(_state.value.scope, id)
    }
    private fun write(success: String, action: suspend () -> Any?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = "Pendiente de confirmar en el servidor. Si no hay internet, se sincronizará al volver.") }
        writeJob = viewModelScope.launch {
            try {
                action()
                _state.update { it.copy(message = success) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _state.update { it.copy(message = friendlyError(e)) }
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
    companion object {
        fun friendlyError(e: Exception): String = when {
            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No tienes permiso para esta operación. El servidor rechazó la solicitud."
            e is FirebaseAuthException && e.errorCode == "ERROR_EMAIL_ALREADY_IN_USE" -> "El correo ya tiene una cuenta. Inicia sesión."
            e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" -> "Habilita correo y contraseña en Firebase Authentication."
            e is FirebaseAuthException -> "No se pudo iniciar sesión. Revisa el correo y la contraseña."
            e is com.google.firebase.FirebaseNetworkException -> "No hay conexión con Firebase. Revisa internet e inténtalo otra vez."
            e is IllegalArgumentException -> e.message ?: "Revisa los datos."
            else -> "No se pudo completar la operación. Revisa la conexión y vuelve a intentar."
        }
    }
}
