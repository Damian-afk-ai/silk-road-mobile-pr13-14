package com.example.silkroad.domain.model

data class Report(
    val id: String = "", val equipo: String = "", val descripcion: String = "",
    val prioridad: String = "Media", val status: String = "Abierto",
    val fechaCreacion: Long = System.currentTimeMillis(),
    val titulo: String = "", val creadoPor: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "titulo" to titulo, "equipo" to equipo, "descripcion" to descripcion,
        "prioridad" to prioridad, "status" to status,
        "fechaCreacion" to fechaCreacion, "creadoPor" to creadoPor)
    companion object {
        val prioridades = listOf("Baja", "Media", "Alta", "Crítica")
        val estados = listOf("Abierto", "En Proceso", "Resuelto", "Cerrado")
        fun fromMap(id: String, map: Map<String, Any?>) = Report(
            id = id, equipo = map["equipo"] as? String ?: "",
            descripcion = map["descripcion"] as? String ?: "",
            prioridad = map["prioridad"] as? String ?: "Media",
            status = map["status"] as? String ?: "Abierto",
            fechaCreacion = (map["fechaCreacion"] as? Number)?.toLong() ?: 0L,
            titulo = map["titulo"] as? String ?: "", creadoPor = map["creadoPor"] as? String ?: "")
        fun validationError(titulo: String, equipo: String, descripcion: String): String? = when {
            titulo.trim().length !in 3..80 -> "El título debe tener entre 3 y 80 caracteres."
            equipo.trim().length !in 2..80 -> "El equipo debe tener entre 2 y 80 caracteres."
            descripcion.trim().length !in 5..500 -> "La descripción debe tener entre 5 y 500 caracteres."
            else -> null
        }
    }
}
