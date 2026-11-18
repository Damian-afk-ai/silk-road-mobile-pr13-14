package com.example.silkroad

import com.example.silkroad.data.search.ElasticQuery
import com.example.silkroad.data.search.ProductSearchSource
import com.example.silkroad.data.search.SearchEngine
import com.example.silkroad.data.search.SearchRepository
import com.example.silkroad.domain.model.Product
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SearchRepositoryTest {
    private val shirt = Product(1, "Camisa de algodón", 19.99, "", "mens-shirts", "")
    private val phone = Product(2, "Teléfono", 99.0, "", "smartphones", "")
    private val catalog = listOf(shirt, phone)

    @Test fun usesElasticsearchOrderAndIgnoresUnknownIds() = runTest {
        val result = SearchRepository { _, _ -> listOf(2, 99, 1) }.search(catalog, "x", null)
        assertEquals(SearchEngine.ELASTICSEARCH, result.engine); assertEquals(listOf(phone, shirt), result.products)
    }
    @Test fun passesTrimmedQueryAndCategory() = runTest {
        var received: Pair<String, String?>? = null
        SearchRepository { q, c -> received = q to c; emptyList() }.search(catalog, "  camisa ", "mens-shirts")
        assertEquals("camisa" to "mens-shirts", received)
    }
    @Test fun networkFailureFallsBackToLocalFilter() = runTest {
        val result = SearchRepository { _, _ -> throw IOException("down") }.search(catalog, "ALGODON", null)
        assertEquals(SearchEngine.LOCAL, result.engine); assertEquals(listOf(shirt), result.products)
    }
    @Test fun blankQueryDoesNotCallElasticsearch() = runTest {
        var calls = 0
        val result = SearchRepository { _, _ -> calls++; emptyList() }.search(catalog, " ", null)
        assertEquals(0, calls); assertEquals(SearchEngine.LOCAL, result.engine); assertEquals(catalog, result.products)
    }
    @Test fun withoutConfigurationUsesLocalSearch() = runTest {
        assertEquals(SearchEngine.LOCAL, SearchRepository(null).search(catalog, "camisa", null).engine)
    }
    @Test fun cancellationIsPropagated() = runTest {
        val source = ProductSearchSource { _, _ -> throw CancellationException() }
        try { SearchRepository(source).search(catalog, "x", null); fail("Cancellation was swallowed") }
        catch (_: CancellationException) { }
    }
    @Test fun typoInSpanishCategoryWorksWithoutServer() = runTest {
        val fragrance = Product(3, "Calvin Klein CK One", 49.0, "", "fragrances", "")
        val result = SearchRepository { _, _ -> throw IOException("offline") }
            .search(catalog + fragrance, "fragansias", null)
        assertEquals(listOf(fragrance), result.products)
        assertEquals(SearchEngine.LOCAL, result.engine)
        assertNotNull(result.notice)
    }
    @Test fun approximateSearchStillRespectsSelectedCategory() = runTest {
        val fragrance = Product(3, "Calvin Klein CK One", 49.0, "", "fragrances", "")
        val result = SearchRepository(null).search(catalog + fragrance, "fragansias", "smartphones")
        assertTrue(result.products.isEmpty())
    }
    @Test fun shortUnrelatedQueryDoesNotReturnEverything() = runTest {
        assertTrue(SearchRepository(null).search(catalog, "zz", null).products.isEmpty())
    }
    @Test fun validRemoteEmptyResultIsNotPresentedAsLocal() = runTest {
        val result = SearchRepository { _, _ -> emptyList() }.search(catalog, "camisa", null)
        assertEquals(SearchEngine.ELASTICSEARCH, result.engine)
        assertTrue(result.products.isEmpty())
        assertNull(result.notice)
    }
    @Test fun staleRemoteIdsUseLocalResultAndExplainTheMismatch() = runTest {
        val result = SearchRepository { _, _ -> listOf(99999) }.search(catalog, "camisa", null)
        assertEquals(SearchEngine.LOCAL, result.engine)
        assertEquals(listOf(shirt), result.products)
        assertNotNull(result.notice)
    }
    @Test fun queryFiltersByCategoryOnlyWhenSelected() {
        @Suppress("UNCHECKED_CAST")
        fun bool(c: String?) = (ElasticQuery.build("camisa", c)["query"] as Map<String, Any>)["bool"] as Map<String, Any>
        assertFalse(bool(null).containsKey("filter"))
        assertEquals(listOf(mapOf("term" to mapOf("category" to "mens-shirts"))), bool("mens-shirts")["filter"])
    }
}
