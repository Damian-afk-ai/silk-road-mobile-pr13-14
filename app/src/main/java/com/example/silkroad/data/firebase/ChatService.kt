package com.example.silkroad.data.firebase

import com.example.silkroad.domain.model.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Servicio para el Sistema de Chat en Tiempo Real conectado a la colección 'messages'.
 * Diseñado con soporte Offline-First para no bloquear la interfaz ante retardos de red.
 */
class ChatService(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection = firestore.collection("messages")

    // Mensajes locales inmediatos para respuesta fluida sin esperas de red
    private val localMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage("init_1", "Administrador_Agente", "Administrador", "Agente", "Bienvenido a Silk Road. Sistema en línea.", System.currentTimeMillis() - 120000),
            ChatMessage("init_2", "Administrador_Agente", "Agente", "Administrador", "Recibido. Esperando asignación de situaciones.", System.currentTimeMillis() - 60000)
        )
    )

    companion object {
        fun getConversationId(devA: String, devB: String): String {
            val list = listOf(devA, devB).sorted()
            return "${list[0]}_${list[1]}"
        }
    }

    /**
     * [Tarea 1.3 & 1.5]: Envío de mensaje con validación de no vacío.
     */
    suspend fun sendMessage(sender: String, receiver: String, text: String) = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("No se puede enviar un mensaje vacío.")
        }

        val convId = getConversationId(sender, receiver)
        val message = ChatMessage(
            id = "msg_" + System.currentTimeMillis(),
            conversationId = convId,
            sender = sender,
            receiver = receiver,
            text = trimmed,
            timestamp = System.currentTimeMillis()
        )

        // Reflejar localmente de inmediato
        localMessages.value = localMessages.value + message

        // Persistir en Firestore de fondo con timeout para no congelar
        try {
            withTimeoutOrNull(400L) {
                collection.add(message.toMap())
            }
        } catch (_: Exception) {}
    }

    /**
     * [Tarea 1.4]: Historial en tiempo real de la conversación.
     */
    fun observeMessages(devA: String, devB: String): Flow<List<ChatMessage>> {
        val convId = getConversationId(devA, devB)

        val firestoreFlow: Flow<List<ChatMessage>> = callbackFlow {
            val subscription = collection
                .whereEqualTo("conversationId", convId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { ChatMessage.fromMap(doc.id, it) }
                    }
                    trySend(messages)
                }
            awaitClose { subscription.remove() }
        }

        return combine(localMessages, firestoreFlow) { local, remote ->
            val localForConv = local.filter { it.conversationId == convId }
            val map = mutableMapOf<String, ChatMessage>()
            for (m in localForConv) map[m.id.ifEmpty { "${m.sender}_${m.timestamp}" }] = m
            for (m in remote) map[m.id.ifEmpty { "${m.sender}_${m.timestamp}" }] = m
            map.values.sortedBy { it.timestamp }
        }
    }

    /**
     * [Tarea 1.8]: Limpieza total del historial de esa conversación en Firestore.
     */
    suspend fun clearConversation(devA: String, devB: String) = withContext(Dispatchers.IO) {
        val convId = getConversationId(devA, devB)
        // Limpiar local
        localMessages.value = localMessages.value.filterNot { it.conversationId == convId }

        // Limpiar en Firestore de forma segura
        try {
            withTimeoutOrNull(600L) {
                val snapshot = collection.whereEqualTo("conversationId", convId).get().result
                if (snapshot != null && !snapshot.isEmpty) {
                    val batch = firestore.batch()
                    for (doc in snapshot.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                }
            }
        } catch (_: Exception) {}
    }
}
