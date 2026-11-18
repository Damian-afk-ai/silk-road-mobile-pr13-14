package com.example.silkroad

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.silkroad.data.local.LocalStorageService
import com.example.silkroad.domain.model.CartItem
import com.example.silkroad.domain.model.Product
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Application
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import com.example.silkroad.data.remote.ProductSource
import com.example.silkroad.data.remote.ProductService
import com.example.silkroad.domain.model.User
import com.example.silkroad.presentation.viewmodel.ShopViewModel
import com.example.silkroad.presentation.ui.SilkRoadApp
import com.example.silkroad.ui.theme.SilkRoadTheme
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap

@RunWith(AndroidJUnit4::class)
class AndroidFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun reset() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("silk_road_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        // Recreating an Activity retains its ViewModel. Use a fresh model after clearing test storage.
        val fresh = ShopViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.runOnUiThread { compose.activity.setContent { SilkRoadTheme { SilkRoadApp(fresh) } } }
    }
    @Test fun demoLoginAndLogout() {
        compose.onNodeWithText("Rellenar cuenta demo").performScrollTo().performClick()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Hola, demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Cerrar sesión").performClick()
        compose.onNode(hasText("Cerrar sesión") and hasClickAction()).performClick()
        compose.onNodeWithText("Iniciar sesión").assertExists()
    }
    @Test fun storageSurvivesNewInstanceAndSeparatesUsers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val product = Product(1, "Producto", 2.0, "Detalle", "electronics", "")
        LocalStorageService(context).apply {
            saveProducts(listOf(product)); saveFavorites("demo", setOf(1)); saveCart("demo", listOf(CartItem(product, 2)))
        }
        LocalStorageService(context).apply {
            assertEquals(listOf(product), products()); assertEquals(setOf(1), favorites("demo"))
            assertEquals(2, cart("demo").single().quantity); assertTrue(favorites("otro").isEmpty()); assertTrue(cart("otro").isEmpty())
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // UIAutomator screenshots also include the system window's dialog animation.
        android.os.SystemClock.sleep(400)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.getExternalFilesDir(null), "$name.png")
        val bitmap = if (compose.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1)
            compose.onRoot().captureToImage().asAndroidBitmap()
        else InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        bitmap.let {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun offlineSearchFavoriteCartAndSessionFlow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val products = listOf(
            Product(1, "Camisa de algodón", 19.99, "Camisa cómoda para uso diario. Producto de prueba para validar el flujo offline.", "mens-shirts", ""),
            Product(2, "Teléfono portátil", 129.99, "Pantalla de alta resolución.", "smartphones", ""))
        val storage = LocalStorageService(app)
        storage.saveProducts(products); storage.saveSession(User("demo"))
        // Sin Elasticsearch: la búsqueda offline usa el filtro local.
        val vm = ShopViewModel(app, ProductSource { throw IOException("Offline test") }, null)
        compose.activity.runOnUiThread { compose.activity.setContent { SilkRoadTheme { SilkRoadApp(vm) } } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Camisa de algodón").fetchSemanticsNodes().isNotEmpty() }
        screenshot("02-catalogo-offline")
        compose.onNodeWithText("Buscar por nombre o categoría").performTextInput("ALGODON")
        compose.onNodeWithText("Buscar por nombre o categoría").performImeAction()
        compose.onNodeWithText("1 productos").assertExists()
        screenshot("03-busqueda")
        compose.onNodeWithText("Camisa de algodón").performClick()
        screenshot("04-detalle")
        compose.onNodeWithText("Guardar en favoritos").performScrollTo().performClick()
        compose.onNodeWithText("Agregar al carrito").performScrollTo().performClick()
        compose.onNodeWithText("Favoritos").performClick()
        compose.onNodeWithText("Camisa de algodón").assertExists()
        screenshot("05-favoritos")
        compose.onNodeWithText("Carrito (1)").performClick()
        compose.onNodeWithContentDescription("Sumar cantidad").performClick()
        compose.onNodeWithText("Total: $39.98 USD").assertExists()
        screenshot("06-carrito")
        assertEquals(2, LocalStorageService(app).cart("demo").single().quantity)
        compose.onNodeWithText("Finalizar compra simulada").performClick()
        compose.onNodeWithText("Confirmar simulación").performClick()
        screenshot("07-compra-simulada")
        compose.onNodeWithText("Entendido").performClick()
        assertTrue(LocalStorageService(app).cart("demo").isEmpty())
        compose.onNodeWithContentDescription("Cerrar sesión").performClick()
        compose.onNode(hasText("Cerrar sesión") and hasClickAction()).performClick()
        screenshot("01-login")
        assertNull(LocalStorageService(app).session())
        assertEquals(setOf(1), LocalStorageService(app).favorites("demo"))
    }
    @Test fun liveCatalogApiReturnsProducts() = runBlocking {
        val products = ProductService().getProducts()
        assertTrue(products.isNotEmpty()); assertTrue(products.all { it.id > 0 && it.price >= 0 })
        val app = ApplicationProvider.getApplicationContext<Application>()
        LocalStorageService(app).saveProducts(products)
        LocalStorageService(app).saveSession(User("demo"))
        val vm = ShopViewModel(app, ProductSource { products })
        compose.activity.runOnUiThread { compose.activity.setContent { SilkRoadTheme { SilkRoadApp(vm) } } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("${products.size} productos").fetchSemanticsNodes().isNotEmpty() }
        screenshot("08-catalogo-api-real")
    }
}
