package com.example.silkroad.domain.model

import android.net.Uri

/**
 * [REGLA ESTRICTA DE ARQUITECTURA]:
 * El acceso NO se maneja por roles rígidos (ej. "admin"), sino por permisos granulares.
 * Cada usuario recibe dinámicamente sus capacidades al abrir la app a través de una URL / Deep Link.
 */
data class UserPermissions(
    val userId: String,
    val userName: String,
    val permissions: Set<String>
) {
    fun hasPermission(permission: String): Boolean = permissions.contains(permission)

    companion object {
        const val CAN_READ_REPORTS = "can_read_reports"
        const val CAN_CREATE_REPORTS = "can_create_reports"
        const val CAN_UPDATE_REPORTS = "can_update_reports"
        const val CAN_DELETE_REPORTS = "can_delete_reports"
        const val CAN_SEND_MESSAGES = "can_send_messages"
        const val CAN_READ_MESSAGES = "can_read_messages"
        const val CAN_CLEAR_CHAT = "can_clear_chat"

        /**
         * Parsea una URL / Deep Link y extrae el perfil dinámico de permisos granulares.
         * Formato soportado:
         * silkroad://access?user=Administrador&perms=can_read_reports,can_send_messages,can_clear_chat
         * o https://silkroad.app/access?user=Agente&perms=can_read_reports,can_send_messages
         */
        fun fromUri(uri: Uri): UserPermissions {
            val user = uri.getQueryParameter("user")?.takeIf { it.isNotBlank() } ?: "Invitado"
            val permsRaw = uri.getQueryParameter("perms") ?: uri.getQueryParameter("permissions") ?: ""
            val perms = if (permsRaw.isBlank()) {
                emptySet()
            } else {
                permsRaw.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
            return UserPermissions(
                userId = user.lowercase().replace(" ", "_"),
                userName = user,
                permissions = perms
            )
        }

        fun fromUrlString(url: String): UserPermissions {
            return try {
                fromUri(Uri.parse(url))
            } catch (e: Exception) {
                presetFor("Invitado")
            }
        }

        /**
         * Presets para simulación interactiva de dispositivos en la aplicación.
         */
        fun presetFor(device: String): UserPermissions {
            return when (device) {
                "Administrador" -> UserPermissions(
                    userId = "admin",
                    userName = "Administrador",
                    permissions = setOf(
                        CAN_READ_REPORTS,
                        CAN_CREATE_REPORTS,
                        CAN_UPDATE_REPORTS,
                        CAN_DELETE_REPORTS,
                        CAN_SEND_MESSAGES,
                        CAN_READ_MESSAGES,
                        CAN_CLEAR_CHAT
                    )
                )
                "Agente" -> UserPermissions(
                    userId = "agente",
                    userName = "Agente",
                    permissions = setOf(
                        CAN_READ_REPORTS,
                        CAN_UPDATE_REPORTS,
                        CAN_SEND_MESSAGES,
                        CAN_READ_MESSAGES
                    )
                )
                "Usuario 1" -> UserPermissions(
                    userId = "usuario_1",
                    userName = "Usuario 1",
                    permissions = setOf(
                        CAN_READ_REPORTS,
                        CAN_CREATE_REPORTS,
                        CAN_SEND_MESSAGES,
                        CAN_READ_MESSAGES
                    )
                )
                "Usuario 2" -> UserPermissions(
                    userId = "usuario_2",
                    userName = "Usuario 2",
                    permissions = setOf(
                        CAN_SEND_MESSAGES,
                        CAN_READ_MESSAGES
                    )
                )
                else -> UserPermissions(
                    userId = "invitado",
                    userName = device,
                    permissions = setOf(CAN_READ_MESSAGES)
                )
            }
        }
    }
}
