package com.example.silkroad.data.search

import com.example.silkroad.domain.ShopRules
import com.example.silkroad.domain.model.Product
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SearchEngine { ELASTICSEARCH, LOCAL }

data class SearchResult(val query: String, val category: String?, val products: List<Product>, val engine: SearchEngine,
    val notice: String? = null)

/**
 * Usa Elasticsearch cuando está configurado y responde; si no (sin red, servidor caído o
 * consulta vacía) recurre al filtro local sobre el catálogo guardado.
 */
class SearchRepository(private val remote: ProductSearchSource?) {
    suspend fun search(products: List<Product>, query: String, category: String?): SearchResult {
        val local = SearchResult(query, category, ShopRules.filterApproximate(products, query, category), SearchEngine.LOCAL)
        if (query.isBlank()) return local
        if (remote == null) return local.copy(notice = "Búsqueda local. No hay un servidor de Elasticsearch configurado.")
        return withContext(Dispatchers.IO) {
            try {
                val byId = products.associateBy { it.id }
                val ids = remote.search(query.trim(), category)
                val hits = ids.mapNotNull { byId[it] }
                if (ids.isNotEmpty() && hits.isEmpty() && local.products.isNotEmpty()) {
                    local.copy(notice = "Búsqueda local. El índice del servidor no coincide con el catálogo actual.")
                } else SearchResult(query, category, hits, SearchEngine.ELASTICSEARCH)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                local.copy(notice = "Búsqueda local. No se pudo conectar con Elasticsearch. Revisa el servidor y su dirección en Configurar buscador.")
            }
        }
    }
}
