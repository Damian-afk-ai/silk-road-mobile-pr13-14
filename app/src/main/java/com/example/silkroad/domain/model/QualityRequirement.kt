package com.example.silkroad.domain.model

data class QualityRequirement(val id: String, val title: String, val acceptance: String)

val qualityRequirements = listOf(
    QualityRequirement("RF-01", "Inicio de sesión", "Correo y contraseña válidos muestran el correo y un UID no vacío; las credenciales incorrectas no abren el módulo."),
    QualityRequirement("RF-02", "Alta de reporte", "Un formulario válido crea un documento con el UID de sesión y aparece en la lista."),
    QualityRequirement("RF-03", "Consulta y búsqueda", "Buscar un título existente muestra ese reporte; una búsqueda sin coincidencias muestra lista vacía."),
    QualityRequirement("RF-04", "Actualización", "Cambiar estado o prioridad modifica solo ese campo y el servidor lo confirma."),
    QualityRequirement("RF-05", "Eliminación", "Cancelar conserva el reporte; confirmar lo elimina del servidor y de la lista."),
    QualityRequirement("VAL-01", "Correo inválido", "Un correo sin formato válido impide el envío y muestra Ingresa un correo válido."),
    QualityRequirement("VAL-02", "Campos del reporte", "Título de 3 a 80, equipo de 2 a 80 y descripción de 5 a 500 caracteres; fuera de rango se rechaza."),
    QualityRequirement("VAL-03", "Datos enviados al servidor", "Firestore rechaza prioridad inválida, tipo incorrecto, campos extra o cambio de propietario."),
    QualityRequirement("PER-01", "Persistencia", "Tras cerrar y abrir la app, un reporte confirmado permanece con los mismos campos."),
    QualityRequirement("PER-02", "Sincronización", "En Compartidos P9, una escritura de A se ve en B sin actualizar manualmente; medir el tiempo real."),
    QualityRequirement("NAV-01", "Regreso y cancelación", "Volver desde Reportes regresa al origen; cancelar un formulario no crea documentos."),
    QualityRequirement("SEG-01", "Propiedad privada", "En Mis reportes P10, A no lee, modifica ni elimina documentos de B, incluso por ID."),
    QualityRequirement("SEG-02", "Cierre de sesión", "Cerrar sesión quita lista y UID; Firestore rechaza nuevas lecturas y escrituras sin sesión."),
    QualityRequirement("ERR-01", "Pérdida de conexión", "Una escritura offline indica pendiente; no muestra confirmación remota hasta reconectar."),
    QualityRequirement("CON-01", "Conflicto de edición", "Ediciones de campos distintos se conservan. En el mismo campo, prevalece la última escritura aceptada.")
)
