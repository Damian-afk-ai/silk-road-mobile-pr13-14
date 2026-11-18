package com.example.silkroad.domain.model

/**
 * Modelo de datos para la colección 'messages' de Firestore.
 * Cubre los requerimientos del Sistema de Chat en Tiempo Real (Tarea 1).
 */
data class ChatMessage(
    val id: String = "",
    val conversationId: String = "",
    val sender: String = "",
    val receiver: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "conversationId" to conversationId,
        "sender" to sender,
        "receiver" to receiver,
        "text" to text,
        "timestamp" to timestamp
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ChatMessage {
            return ChatMessage(
                id = id,
                conversationId = map["conversationId"] as? String ?: "",
                sender = map["sender"] as? String ?: "",
                receiver = map["receiver"] as? String ?: "",
                text = map["text"] as? String ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
