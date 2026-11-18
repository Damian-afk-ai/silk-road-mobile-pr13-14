package com.example.silkroad.data.firebase

import com.example.silkroad.domain.model.Report
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

enum class ReportScope(val collection: String) { SHARED("shared_reports"), PRIVATE("reports") }
data class ReportSnapshot(val reports: List<Report> = emptyList(), val cached: Boolean = false,
    val pending: Boolean = false, val error: String? = null)

class ReportService(private val firestore: FirebaseFirestore = FirebaseManager.firestore,
    private val auth: FirebaseAuth = FirebaseManager.auth) {
    fun observe(scope: ReportScope, uid: String) = callbackFlow {
        val collection = firestore.collection(scope.collection)
        val query = if (scope == ReportScope.PRIVATE) collection.whereEqualTo("creadoPor", uid) else collection
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) trySend(ReportSnapshot(error = error.message ?: "No se pudo consultar el servidor."))
            else if (snapshot != null) trySend(ReportSnapshot(snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Report.fromMap(doc.id, it) }
            }.sortedByDescending { it.fechaCreacion }, snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites()))
        }
        awaitClose { registration.remove() }
    }
    suspend fun create(scope: ReportScope, titulo: String, equipo: String, descripcion: String, prioridad: String): String {
        val uid = requireNotNull(auth.currentUser?.uid) { "Inicia sesión para guardar." }
        Report.validationError(titulo, equipo, descripcion)?.let { throw IllegalArgumentException(it) }
        require(prioridad in Report.prioridades) { "Prioridad inválida." }
        val ref = firestore.collection(scope.collection).document()
        val report = Report(id = ref.id, titulo = titulo.trim(), equipo = equipo.trim(),
            descripcion = descripcion.trim(), prioridad = prioridad, creadoPor = uid)
        ref.set(report.toMap()).await()
        return ref.id
    }
    suspend fun update(scope: ReportScope, id: String, field: String, value: String) {
        require(auth.currentUser != null) { "Inicia sesión para editar." }
        require((field == "status" && value in Report.estados) ||
            (field == "prioridad" && value in Report.prioridades)) { "Cambio inválido." }
        firestore.collection(scope.collection).document(id).update(field, value).await()
    }
    suspend fun delete(scope: ReportScope, id: String) {
        require(auth.currentUser != null) { "Inicia sesión para eliminar." }
        firestore.collection(scope.collection).document(id).delete().await()
    }
    suspend fun getFromServer(scope: ReportScope, id: String): Report? {
        val doc = firestore.collection(scope.collection).document(id).get(Source.SERVER).await()
        return doc.data?.let { Report.fromMap(doc.id, it) }
    }
}
