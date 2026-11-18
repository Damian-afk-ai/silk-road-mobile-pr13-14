package com.example.silkroad.data.repository

import com.example.silkroad.data.local.ProductCache
import com.example.silkroad.data.remote.ProductSource
import com.example.silkroad.domain.model.Product
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CatalogResult(val products: List<Product>, val cached: Boolean, val message: String? = null)

class OfflineProductRepository(private val api: ProductSource, private val cache: ProductCache) {
    suspend fun refresh(): CatalogResult = withContext(Dispatchers.IO) {
        try {
            val products = api.getProducts()
            require(products.isNotEmpty() && products.all {
                it.id > 0 && it.price.isFinite() && it.price >= 0 && !it.title.isNullOrBlank() &&
                    !it.category.isNullOrBlank() && it.description != null && it.image != null
            }) { "La API devolvió un catálogo inválido." }
            cache.saveProducts(products)
            CatalogResult(products, false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val saved = cache.products()
            CatalogResult(saved, true, if (saved.isEmpty())
                "No se pudo conectar. Conéctate y pulsa Actualizar para guardar tu primer catálogo."
            else "Sin actualización de red. Mostrando el catálogo guardado; las imágenes pueden no estar disponibles.")
        }
    }
}
