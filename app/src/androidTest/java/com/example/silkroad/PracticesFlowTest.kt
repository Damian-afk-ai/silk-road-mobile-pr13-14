package com.example.silkroad

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.silkroad.data.firebase.FirebaseManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File

/** Caja negra: entradas y salidas de las pantallas reales; sin reemplazar repositorios. */
@RunWith(AndroidJUnit4::class)
class PracticesFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun finishActivity() {
        compose.activity.runOnUiThread { compose.activity.finish() }
    }
    private val args get() = InstrumentationRegistry.getArguments()
    private val account get() = args.getString("account", "a")!!
    private val email get() = "practica.$account@silkroad.example"
    private fun launchReports() {
        dismissSystemUiDialog()
        // Entrar por el botón real; no abrir una segunda Activity ajena a ActivityScenario.
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Calidad P8").fetchSemanticsNodes().isEmpty()) {
            if (compose.onAllNodesWithText("Rellenar cuenta demo").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText("Reportes").performScrollTo().performClick()
            } else {
                compose.onNodeWithText("Reportes").performClick()
            }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Calidad P8").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun waitText(text: String, timeout: Long = 20000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun waitReport(title: String, timeout: Long = 20000) {
        compose.waitUntil(timeout) { compose.onAllNodes(hasText(title) and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun stableSync() {
        var since=0L
        compose.waitUntil(20000) {
            val synced=compose.onAllNodesWithText("Sincronizado con el servidor").fetchSemanticsNodes().isNotEmpty()
            if (!synced) since=0L
            else if (since==0L) since=android.os.SystemClock.elapsedRealtime()
            synced && android.os.SystemClock.elapsedRealtime()-since>=800L
        }
    }
    private fun event(name: String, time: Long = System.currentTimeMillis()) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString(name, time.toString()) })
    }
    private fun login() {
        if (FirebaseManager.auth.currentUser?.email != email) {
            compose.runOnIdle { FirebaseManager.auth.signOut() }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("auth-login").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("auth-email").performTextInput(email)
            compose.onNodeWithTag("auth-password").performTextInput("Practicas810!")
            compose.onNodeWithTag("auth-login").performScrollTo().performClick()
        }
        compose.waitUntil(20000) { compose.onAllNodesWithTag("session-uid").fetchSemanticsNodes().isNotEmpty() }
        waitText("Sincronizado con el servidor")
    }
    private fun shot(name: String) {
        dismissSystemUiDialog()
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "practicas-8-10/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun dismissSystemUiDialog() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val root=automation.rootInActiveWindow ?: return
        // Se atiende solo el aviso del sistema del emulador; un ANR de Silk Road no se oculta.
        if (root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty()) {
            val wait=root.findAccessibilityNodeInfosByText("Wait").firstOrNull()
                ?: throw AssertionError("Aviso de System UI sin opción Wait")
            var target=wait
            while (!target.isClickable && target.parent != null) target=target.parent
            check(target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            event("system_ui_dialog_dismissed_ms")
            android.os.SystemClock.sleep(600)
            check(automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("System UI isn't responding")?.isEmpty() != false) {
                "System UI sigue bloqueando la captura"
            }
        }
    }
    private fun create(title: String) {
        compose.onNodeWithText("Nuevo").performClick()
        compose.onNodeWithTag("report-title").performTextInput(title)
        compose.onNodeWithTag("report-equipment").performTextInput("Android $account")
        compose.onNodeWithTag("report-description").performTextInput("Reporte de prueba desde dispositivo $account")
        if (title == args.getString("realtimeTitle")) event("creation_sent_ms")
        compose.onNodeWithTag("report-save").performClick()
        waitText("Reporte guardado en el servidor.")
        waitReport(title)
    }
    private fun search(title: String) {
        compose.onNodeWithTag("report-search").performTextReplacement(title)
        compose.onNodeWithTag("report-search").performImeAction()
    }
    private fun reportId(title: String): String {
        // Lee el ID visible, no el almacén interno, para accionar la tarjeta correcta.
        val node=compose.onNode(hasText("Creado por:", substring=true) and hasAnyAncestor(hasAnyDescendant(hasText(title)))).fetchSemanticsNode()
        val text=node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        return text.substringAfter("ID: ").substringBefore("\n")
    }
    @Test fun blackBoxFormsAndCrud() {
        launchReports()
        compose.runOnIdle { FirebaseManager.auth.signOut() }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("auth-login").fetchSemanticsNodes().isNotEmpty() }
        shot("P8-01-acceso-firebase")
        compose.onNodeWithTag("auth-email").performTextInput("correo-invalido")
        compose.onNodeWithTag("auth-password").performTextInput("Practicas810!")
        compose.onNodeWithTag("auth-login").performScrollTo().performClick()
        compose.onNodeWithText("Ingresa un correo válido.").assertExists()
        shot("P8-02-correo-invalido")
        compose.onNodeWithTag("auth-email").performTextReplacement(email)
        compose.onNodeWithTag("auth-password").performTextReplacement("Incorrecta810!")
        compose.onNodeWithTag("auth-login").performClick()
        waitText("No se pudo iniciar sesión.")
        shot("P8-03-contrasena-rechazada")
        compose.onNodeWithTag("auth-password").performTextReplacement("Practicas810!")
        compose.onNodeWithTag("auth-login").performClick()
        waitText("UID:")
        waitText("Sincronizado con el servidor")
        shot("P10-01-sesion-$account")
        compose.onNodeWithText("Nuevo").performClick()
        compose.onNodeWithTag("report-save").performClick()
        compose.onNodeWithTag("report-validation").assertTextEquals("El título debe tener entre 3 y 80 caracteres.")
        shot("P8-04-formulario-invalido")
        compose.onNodeWithTag("report-title").performTextInput("Título válido")
        compose.onNodeWithTag("report-save").performClick()
        compose.onNodeWithTag("report-validation").assertTextEquals("El equipo debe tener entre 2 y 80 caracteres.")
        compose.onNodeWithTag("report-equipment").performTextInput("Android")
        compose.onNodeWithTag("report-description").performTextInput("x")
        compose.onNodeWithTag("report-save").performClick()
        compose.onNodeWithTag("report-validation").assertTextEquals("La descripción debe tener entre 5 y 500 caracteres.")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Calidad P8").performClick()
        compose.onNodeWithText("RF-01 · Inicio de sesión").assertExists()
        shot("P8-05-requisitos")
        compose.onNodeWithText("Volver a reportes").performClick()
        val title="CAJA_NEGRA_"+System.currentTimeMillis()
        create(title)
        search(title)
        shot("P8-06-alta-confirmada")
        val id=reportId(title)
        compose.onNodeWithTag("status-$id").performClick()
        waitText("Cambio confirmado en el servidor.")
        compose.onNodeWithText("Estado: En Proceso · Prioridad: Media").assertExists()
        compose.onNodeWithTag("priority-$id").performClick()
        waitText("Estado: En Proceso · Prioridad: Alta")
        waitText("Cambio confirmado en el servidor.")
        stableSync()
        shot("P8-07-actualizacion")
        search("SIN_COINCIDENCIAS_123")
        compose.onNodeWithText("Sin coincidencias.").assertExists()
        shot("P8-08-busqueda-vacia")
        search(title)
        compose.onNodeWithTag("delete-$id").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNode(hasText(title) and !hasSetTextAction()).assertExists()
        compose.onNodeWithTag("delete-$id").performClick()
        shot("P8-09-confirmacion-eliminar")
        compose.onNodeWithTag("confirm-delete").performClick()
        waitText("Eliminación confirmada en el servidor.")
        compose.onNodeWithText("Sin coincidencias.").assertExists()
        shot("P8-10-eliminado")
        compose.onNodeWithTag("auth-logout").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("auth-login").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("session-uid").assertDoesNotExist()
        shot("P10-02-sesion-cerrada")
        // Regreso mediante botón Android a la pantalla de entrada de la tienda.
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Rellenar cuenta demo").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Catálogo").fetchSemanticsNodes().isNotEmpty()
        }
        shot("P8-11-regreso-a-tienda")
    }
    @Test fun seedDevice() {
        launchReports(); login()
        shot("P10-sesion-$account")
        val privateTitle = "PRIVADO_${account.uppercase()}_ANDROID"
        search(privateTitle)
        if (compose.onAllNodes(hasText(privateTitle) and !hasSetTextAction()).fetchSemanticsNodes().isEmpty()) create(privateTitle)
        shot("P10-propietario-$account")
        search(if(account=="a") "PRIVADO_B_ANDROID" else "PRIVADO_A_ANDROID")
        compose.onNodeWithText("Sin coincidencias.").assertExists()
        shot("P10-lista-privada-$account")
        compose.onNodeWithTag("scope-shared").performClick()
        waitText("Sincronizado con el servidor")
        val title="PRUEBA_${account.uppercase()}_ANDROID"
        search(title)
        if (compose.onAllNodes(hasText(title) and !hasSetTextAction()).fetchSemanticsNodes().isEmpty()) create(title)
        search("")
        shot("P9-compartidos-$account")
    }
    @Test fun verifyCrossDevice() {
        launchReports(); login()
        compose.onNodeWithTag("scope-shared").performClick()
        waitText("Sincronizado con el servidor")
        val other=if(account=="a") "B" else "A"
        val title="PRUEBA_${other}_ANDROID"
        search(title); waitReport(title)
        shot("P9-lectura-cruzada-$account")
        val id=reportId(title)
        compose.onNodeWithTag("status-$id").performClick()
        waitText("Cambio confirmado en el servidor.")
        shot("P9-actualizacion-cruzada-$account")
    }
    @Test fun persistedAfterRestart() {
        launchReports(); login()
        compose.onNodeWithTag("scope-shared").performClick()
        waitText("Sincronizado con el servidor")
        search("PRUEBA_A_ANDROID")
        waitReport("PRUEBA_A_ANDROID")
        shot("P9-persistencia-$account")
    }
    @Test fun offlineWriteAndReconnect() {
        launchReports(); login()
        compose.onNodeWithTag("scope-shared").performClick()
        waitText("Sincronizado con el servidor")
        val db=FirebaseFirestore.getInstance()
        runBlocking { db.disableNetwork().await() }
        try {
            val title="OFFLINE_ANDROID_"+System.currentTimeMillis()
            compose.onNodeWithText("Nuevo").performClick()
            compose.onNodeWithTag("report-title").performTextInput(title)
            compose.onNodeWithTag("report-equipment").performTextInput("Android sin conexión")
            compose.onNodeWithTag("report-description").performTextInput("Escritura pendiente mientras no hay conexión")
            compose.onNodeWithTag("report-save").performClick()
            waitText("Pendiente de confirmar en el servidor.")
            search(title)
            compose.onNodeWithText("Reporte guardado en el servidor.").assertDoesNotExist()
            shot("P9-09-pendiente-sin-red")
            runBlocking { db.enableNetwork().await() }
            waitText("Reporte guardado en el servidor.",30000)
            waitText("Sincronizado con el servidor")
            shot("P9-09-confirmado-al-reconectar")
        } finally { runBlocking { db.enableNetwork().await() } }
    }
    @Test fun observeRealtime() {
        launchReports(); login()
        compose.onNodeWithTag("scope-shared").performClick()
        waitText("Sincronizado con el servidor")
        val title=args.getString("realtimeTitle")!!
        search(title)
        compose.onNodeWithText("Sin coincidencias.").assertExists()
        event("listener_ready_ms")
        waitReport(title,90000)
        event("creation_received_ms")
        shot("P9-tiempo-real-alta-B")
        waitText("Estado: En Proceso · Prioridad: Media",60000)
        event("update_received_ms")
        waitText("Sincronizado con el servidor")
        shot("P9-tiempo-real-cambio-B")
    }
    @Test fun publishRealtime() {
        launchReports(); login()
        compose.onNodeWithTag("scope-shared").performClick()
        waitText("Sincronizado con el servidor")
        val title=args.getString("realtimeTitle")!!
        create(title)
        search(title)
        shot("P9-tiempo-real-alta-A")
        android.os.SystemClock.sleep(1500)
        event("update_sent_ms")
        compose.onNodeWithTag("status-${reportId(title)}").performClick()
        waitText("Cambio confirmado en el servidor.")
        waitText("Sincronizado con el servidor")
        shot("P9-tiempo-real-cambio-A")
    }
    @Test fun captureSyncedRealtime() {
        launchReports(); login()
        compose.onNodeWithTag("scope-shared").performClick()
        search(args.getString("realtimeTitle")!!)
        waitReport(args.getString("realtimeTitle")!!)
        stableSync()
        shot("P9-tiempo-real-cambio-${account.uppercase()}")
    }
}
