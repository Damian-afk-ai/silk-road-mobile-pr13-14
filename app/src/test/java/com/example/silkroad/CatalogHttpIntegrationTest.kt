package com.example.silkroad

import android.content.SharedPreferences
import com.example.silkroad.data.local.LocalStorageService
import com.example.silkroad.data.remote.ProductService
import com.example.silkroad.data.repository.OfflineProductRepository
import com.example.silkroad.domain.model.Product
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class CatalogHttpIntegrationTest {
    private val product = Product(7, "Teclado", 29.50, "USB", "electronics", "keyboard.png")
    private val preferences = CatalogPreferences()
    private val storage = LocalStorageService(preferences.delegate)
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var server: ServerSocket
    private lateinit var service: ProductService

    @Before fun startServer() {
        server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 5_000
        service = ProductService("http://127.0.0.1:${server.localPort}/")
    }

    @After fun stopServer() {
        if (::server.isInitialized) server.close()
        executor.shutdownNow()
    }

    @Test fun mapsHttp200AndPersistsCatalogForAnotherStorageInstance() = runBlocking {
        val request = respond("200 OK", """{"products":[{"id":7,"title":"Teclado","price":29.50,
            "description":"USB","category":"electronics","thumbnail":"keyboard.png"}]}""")

        val result = OfflineProductRepository(service, storage).refresh()
        val reopenedStorage = LocalStorageService(preferences.delegate)

        assertRequest(request)
        assertFalse(result.cached)
        assertEquals(listOf(product), result.products)
        assertEquals(listOf(product), reopenedStorage.products())
        assertTrue(reopenedStorage.lastUpdated() > 0)
    }

    @Test fun usesSavedCatalogWhenHttpReturns503() = runBlocking {
        storage.saveProducts(listOf(product))
        val savedAt = storage.lastUpdated()
        val request = respond("503 Service Unavailable", """{"error":"unavailable"}""")

        val result = OfflineProductRepository(service, storage).refresh()

        assertRequest(request)
        assertTrue(result.cached)
        assertEquals(listOf(product), result.products)
        assertTrue(result.message.orEmpty().contains("Mostrando el catálogo guardado"))
        assertEquals(savedAt, storage.lastUpdated())
        assertEquals(1, preferences.commits)
    }

    @Test fun emptyResponseDoesNotReplaceSavedCatalog() = runBlocking {
        storage.saveProducts(listOf(product))
        val savedAt = storage.lastUpdated()
        val request = respond("200 OK", """{"products":[]}""")

        val result = OfflineProductRepository(service, storage).refresh()

        assertRequest(request)
        assertTrue(result.cached)
        assertEquals(listOf(product), result.products)
        assertEquals(listOf(product), storage.products())
        assertEquals(savedAt, storage.lastUpdated())
        assertEquals(1, preferences.commits)
    }

    private fun assertRequest(request: Future<String>) {
        assertEquals("GET /api/products.php HTTP/1.1", request.get(5, TimeUnit.SECONDS))
    }

    private fun respond(status: String, json: String): Future<String> = executor.submit<String> {
        server.accept().use { client ->
            client.soTimeout = 5_000
            val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
            val requestLine = checkNotNull(reader.readLine())
            while (!reader.readLine().isNullOrEmpty()) {
                // Drain the remaining request headers before writing the response.
            }
            val body = json.toByteArray(Charsets.UTF_8)
            val headers = "HTTP/1.1 $status\r\nContent-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            val output = client.getOutputStream()
            output.write(headers.toByteArray(Charsets.US_ASCII))
            output.write(body)
            output.flush()
            requestLine
        }
    }

    // JVM fixture for the Android boundary. LocalStorageService and its Gson codec are real.
    private class CatalogPreferences {
        private val values = mutableMapOf<String, Any>()
        var commits = 0
            private set

        val delegate = proxy(SharedPreferences::class.java) { method, args ->
            when (method) {
                "getString", "getLong" -> values[args[0] as String] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected SharedPreferences method: $method")
            }
        }

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)
            ) { proxy, method, args ->
                when (method.name) {
                    "putString", "putLong" -> {
                        pending[args[0] as String] = args[1]
                        proxy
                    }
                    "commit" -> {
                        values.putAll(pending)
                        commits++
                        true
                    }
                    else -> error("Unexpected Editor method: ${method.name}")
                }
            } as SharedPreferences.Editor
        }

        private fun <T> proxy(type: Class<T>, call: (String, Array<Any?>) -> Any?): T =
            type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                call(method.name, args ?: emptyArray())
            })
    }
}
