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
import com.example.silkroad.data.remote.ProductService
import com.example.silkroad.data.remote.ProductSource
import com.example.silkroad.domain.model.User
import com.example.silkroad.presentation.ui.SilkRoadApp
import com.example.silkroad.presentation.viewmodel.ShopViewModel
import com.example.silkroad.ui.theme.SilkRoadTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SearchCorrectionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @After fun finish() { compose.activity.runOnUiThread { compose.activity.finish() } }
    private fun waitText(text: String) {
        compose.waitUntil(25000) { compose.onAllNodesWithText(text, substring=true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun configure(url: String) {
        compose.onNodeWithContentDescription("Configurar buscador").performClick()
        compose.onNodeWithText("Dirección del servidor").performTextReplacement(url)
        compose.onNodeWithText("Guardar dirección").performClick()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(400)
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(target.getExternalFilesDir(null),"busqueda-nov9/$name.png")
        file.parentFile!!.mkdirs()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun fragansiasWorksWithElasticsearchAndOfflineFallback() = runBlocking {
        val products=ProductService().getProducts()
        check(products.any { it.category=="fragrances" })
        val app=ApplicationProvider.getApplicationContext<Application>()
        LocalStorageService(app).apply { saveProducts(products); saveSession(User("demo")) }
        val vm=ShopViewModel(app,ProductSource { products })
        compose.activity.runOnUiThread { compose.activity.setContent { SilkRoadTheme { SilkRoadApp(vm) } } }
        waitText("Hola, demo")
        compose.onNodeWithText("Buscar",substring=false).performClick()
        configure("http://10.0.2.2:9200")
        compose.onNodeWithText("Buscar por nombre o categoría").performTextInput("fragansias")
        compose.onNodeWithText("Buscar por nombre o categoría").performImeAction()
        waitText("5 productos · Elasticsearch")
        screenshot("01-fragansias-elasticsearch")
        configure("http://127.0.0.1:1")
        waitText("5 productos · Búsqueda local")
        waitText("No se pudo conectar con Elasticsearch")
        screenshot("02-fragansias-respaldo-local")
        compose.onNodeWithContentDescription("Configurar buscador").performClick()
        compose.onNodeWithText("Dirección del servidor").performTextReplacement("esto no es una direccion")
        compose.onNodeWithText("Guardar dirección").performClick()
        compose.onNodeWithText("Escribe una dirección como http://192.168.1.10:9200").assertExists()
        compose.onNodeWithText("Dirección del servidor").performTextReplacement("http://10.0.2.2:9200")
        compose.onNodeWithText("Guardar dirección").performClick()
        waitText("5 productos · Elasticsearch")
        screenshot("03-servidor-restablecido")
    }
}
