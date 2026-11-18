package com.example.silkroad

import com.example.silkroad.data.AuthService
import com.example.silkroad.data.local.ProductCache
import com.example.silkroad.data.remote.*
import com.example.silkroad.data.repository.OfflineProductRepository
import com.example.silkroad.domain.model.Product
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RepositoryTest {
    private val product = Product(1, "Test", 3.25, "Description", "electronics", "")
    private class Cache(var saved: List<Product> = emptyList()) : ProductCache {
        override fun products() = saved
        override fun saveProducts(products: List<Product>) { saved = products }
        override fun lastUpdated() = 0L
    }
    private class Api(var response: List<Product>, var error: Exception? = null) : FakeStoreApi {
        var calls = 0
        override suspend fun getProducts(): List<Product> { calls++; error?.let { throw it }; return response }
        override suspend fun getCategories() = emptyList<String>()
        override suspend fun getProductsByCategory(category: String) = response.filter { it.category == category }
        override suspend fun login(request: LoginRequest): LoginResponse { calls++; error?.let { throw it }; return LoginResponse("test-token") }
    }
    @Test fun successfulRefreshPersistsCatalog() = runTest {
        val cache = Cache(); val result = OfflineProductRepository(Api(listOf(product)), cache).refresh()
        assertFalse(result.cached); assertEquals(listOf(product), cache.saved)
    }
    @Test fun networkFailureUsesSavedCatalog() = runTest {
        val result = OfflineProductRepository(Api(emptyList(), IOException()), Cache(listOf(product))).refresh()
        assertTrue(result.cached); assertEquals(listOf(product), result.products); assertNotNull(result.message)
    }
    @Test fun firstOfflineLaunchExplainsEmptyCache() = runTest {
        val result = OfflineProductRepository(Api(emptyList(), IOException()), Cache()).refresh()
        assertTrue(result.products.isEmpty()); assertTrue(result.message!!.contains("primer catálogo"))
    }
    @Test fun invalidResponseDoesNotOverwriteCache() = runTest {
        val cache = Cache(listOf(product))
        val result = OfflineProductRepository(Api(listOf(product.copy(price = -1.0))), cache).refresh()
        assertTrue(result.cached); assertEquals(listOf(product), cache.saved)
    }
    @Test fun cancellationIsPropagated() = runTest {
        try {
            OfflineProductRepository(Api(emptyList(), CancellationException()), Cache()).refresh()
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }
    @Test fun demoLoginDoesNotCallNetwork() = runTest {
        val api = Api(emptyList(), IOException())
        assertEquals("demo", AuthService(api).login("demo", "SilkRoad2026", true).getOrThrow().username)
        assertEquals(0, api.calls)
    }
    @Test fun wrongDemoPasswordFails() = runTest { assertTrue(AuthService(Api(emptyList())).login("demo", "incorrecta", true).isFailure) }
    @Test fun remoteLoginReceivesToken() = runTest {
        val api = Api(emptyList())
        assertEquals("test-token", AuthService(api).login("usuario", "12345678", false).getOrThrow().token)
        assertEquals(1, api.calls)
    }
}
