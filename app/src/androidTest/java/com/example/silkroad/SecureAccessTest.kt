package com.example.silkroad

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.silkroad.data.local.LocalStorageService
import com.example.silkroad.data.remote.ProductSource
import com.example.silkroad.presentation.ui.SilkRoadApp
import com.example.silkroad.presentation.viewmodel.ShopViewModel
import com.example.silkroad.ui.theme.SilkRoadTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecureAccessTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @After fun finish() { compose.activity.runOnUiThread { compose.activity.finish() } }
    private fun waitText(text: String) {
        compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun shot(name: String) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        if (compose.onAllNodesWithText("Iniciar sesión").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Iniciar sesión").performScrollTo()
        }
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"seguridad-p5/$name.png")
        file.parentFile!!.mkdirs()
        val image=instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun secureLoginValidationAndNoCredentialsInLogcat() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        LocalStorageService(app).clearSession()
        val vm=ShopViewModel(app,ProductSource { emptyList() },null)
        compose.activity.runOnUiThread { compose.activity.setContent { SilkRoadTheme { SilkRoadApp(vm) } } }
        shot("01-acceso-seguro")
        compose.onNodeWithText("Usuario").performTextInput("    ")
        compose.onNodeWithText("Contraseña").performTextInput("12345678")
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        waitText("Escribe tu usuario.")
        shot("02-usuario-solo-espacios")
        compose.onNodeWithText("Usuario").performTextReplacement("demo")
        compose.onNodeWithText("Contraseña").performTextReplacement("1234567")
        compose.onNodeWithText("Iniciar sesión").performClick()
        waitText("La contraseña debe tener entre 8 y 128 caracteres.")
        shot("03-contrasena-corta")
        val secret="ClaveSensibleNoLog2026!"
        compose.onNodeWithText("Contraseña").performTextReplacement(secret)
        compose.onNodeWithText("Iniciar sesión").performClick()
        waitText("Usuario o contraseña incorrectos.")
        shot("04-error-generico")
        compose.onNodeWithText("Rellenar cuenta demo").performScrollTo().performClick()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        waitText("Hola, demo")
        shot("05-acceso-simulado-correcto")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val descriptor=instrumentation.uiAutomation.executeShellCommand("logcat -d --pid=${android.os.Process.myPid()}")
        val text=android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        check(!text.contains(secret) && !text.contains("SilkRoad2026")) { "Se detectó una credencial en Logcat" }
        File(instrumentation.targetContext.getExternalFilesDir(null),"seguridad-p5/logcat-verificado.txt").writeText(text)
    }
}
