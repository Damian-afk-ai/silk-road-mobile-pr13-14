package com.example.silkroad

import com.example.silkroad.data.local.ProductCache
import com.example.silkroad.data.remote.ProductSource
import com.example.silkroad.data.repository.OfflineProductRepository
import com.example.silkroad.domain.ShopRules
import com.example.silkroad.domain.model.CartItem
import com.example.silkroad.domain.model.Product
import com.example.silkroad.domain.model.Report
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

/** Tests exercise production methods; no copy of the business implementation. */
class WhiteBoxP11Test {
    private val product = Product(1, "Producto", 0.1, "Descripción", "beauty", "https://example.com/a.png")
    private class Cache(var list: List<Product> = emptyList(), var failSave: Boolean = false, var failRead: Boolean = false) : ProductCache {
        var writes = 0
        override fun products(): List<Product> { if (failRead) throw IOException("Cache unavailable"); return list }
        override fun saveProducts(products: List<Product>) { if (failSave) throw IOException("Disk full"); writes++; list=products }
        override fun lastUpdated() = 0L
    }
    @Test fun credentialsIndependentPathsAndBoundaries() {
        val cases=listOf(
            Triple("", "12345678", false), Triple("   ", "12345678", false),
            Triple("aa", "12345678", false), Triple("aaa", "12345678", true),
            Triple("a".repeat(40), "12345678", true), Triple("a".repeat(41), "12345678", false),
            Triple("demo", "", false), Triple("demo", "1".repeat(7), false),
            Triple("demo", "1".repeat(8), true), Triple("demo", "1".repeat(128), true),
            Triple("demo", "1".repeat(129), false), Triple("  demo  ", "12345678", true))
        cases.forEach { (user,password,valid) -> assertEquals("u=${user.length},p=${password.length}", valid, ShopRules.credentialsError(user,password)==null) }
    }
    @Test fun quantityIndependentPaths() {
        assertTrue(ShopRules.changeQuantity(emptyList(),product,0).isEmpty())
        assertEquals(1,ShopRules.changeQuantity(emptyList(),product,1).single().quantity)
        assertTrue(ShopRules.changeQuantity(emptyList(),product,-1).isEmpty())
        assertEquals(99,ShopRules.changeQuantity(emptyList(),product,100).single().quantity)
        val other=product.copy(id=2)
        val original=listOf(CartItem(other,3),CartItem(product,2))
        assertEquals(listOf(CartItem(other,3)),ShopRules.changeQuantity(original,product,-2))
        val changed=ShopRules.changeQuantity(original,product,1)
        assertEquals(3,changed.first{it.product.id==1}.quantity)
        assertEquals(3,changed.first{it.product.id==2}.quantity)
        assertEquals(2,original.last().quantity)
        assertEquals(99,ShopRules.changeQuantity(listOf(CartItem(product,99)),product,1).single().quantity)
    }
    @Test fun totalRoundingAndEmptyCollection() {
        assertEquals(BigDecimal("0.00"),ShopRules.total(emptyList()))
        assertEquals(BigDecimal("0.30"),ShopRules.total(listOf(CartItem(product,3))))
        assertEquals(BigDecimal("1.01"),ShopRules.total(listOf(CartItem(product.copy(price=1.005),1))))
    }
    @Test fun reportBoundaries() {
        for (n in listOf(2,3,80,81)) assertEquals(n in 3..80,Report.validationError("a".repeat(n),"PC","Texto válido")==null)
        for (n in listOf(1,2,80,81)) assertEquals(n in 2..80,Report.validationError("Título","a".repeat(n),"Texto válido")==null)
        for (n in listOf(4,5,500,501)) assertEquals(n in 5..500,Report.validationError("Título","PC","a".repeat(n))==null)
        assertNotNull(Report.validationError("   ","PC","Texto válido"))
    }
    @Test fun refreshSuccessAndCacheWrite() = runTest {
        val cache=Cache(); val result=OfflineProductRepository(ProductSource{listOf(product)},cache).refresh()
        assertFalse(result.cached); assertEquals(listOf(product),cache.list); assertEquals(1,cache.writes)
    }
    @Test fun refreshRejectsEachMalformedProductWithoutDestroyingCache() = runTest {
        val invalid=listOf(product.copy(id=0),product.copy(price=Double.NaN),product.copy(price=Double.POSITIVE_INFINITY),
            product.copy(price=-0.01),product.copy(title="  "),product.copy(category="  "))
        for(p in invalid) {
            val cache=Cache(listOf(product)); val result=OfflineProductRepository(ProductSource{listOf(p)},cache).refresh()
            assertTrue(result.cached);assertEquals(listOf(product),result.products);assertEquals(0,cache.writes)
        }
        val cache=Cache(listOf(product));assertTrue(OfflineProductRepository(ProductSource{emptyList()},cache).refresh().cached)
    }
    @Test fun refreshNetworkFailureWithAndWithoutCache() = runTest {
        val failing=ProductSource{throw IOException("Network interrupted")}
        assertEquals(listOf(product),OfflineProductRepository(failing,Cache(listOf(product))).refresh().products)
        val empty=OfflineProductRepository(failing,Cache()).refresh()
        assertTrue(empty.cached); assertTrue(empty.products.isEmpty()); assertTrue(empty.message!!.contains("primer catálogo"))
    }
    @Test fun refreshDiskFailureKeepsPreviousCatalog() = runTest {
        val cache=Cache(listOf(product),failSave=true)
        val result=OfflineProductRepository(ProductSource{listOf(product.copy(id=2))},cache).refresh()
        assertTrue(result.cached); assertEquals(listOf(product),result.products)
    }
    @Test fun refreshCancellationIsNeverReportedAsSuccess() = runTest {
        try { OfflineProductRepository(ProductSource{throw CancellationException("Cancelled")},Cache()).refresh(); fail("Cancellation swallowed") }
        catch(_:CancellationException) { }
    }
    @Test fun refreshRecoveryDoesNotDuplicateCatalog() = runTest {
        var offline=true; val cache=Cache(listOf(product))
        val repository=OfflineProductRepository(ProductSource{if(offline)throw IOException();listOf(product)},cache)
        assertTrue(repository.refresh().cached); offline=false
        repeat(3){ assertFalse(repository.refresh().cached) }
        assertEquals(1,cache.list.size)
    }
    @Test fun cacheReadFailureIsAnObservedUnhandledException() = runTest {
        try { OfflineProductRepository(ProductSource{throw IOException()},Cache(failRead=true)).refresh(); fail("Expected cache exception") }
        catch(e:IOException){assertEquals("Cache unavailable",e.message)}
    }
}
