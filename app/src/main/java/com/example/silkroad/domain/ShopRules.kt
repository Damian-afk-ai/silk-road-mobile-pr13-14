package com.example.silkroad.domain

import com.example.silkroad.domain.model.CartItem
import com.example.silkroad.domain.model.Product
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

object ShopRules {
    private val combiningMarks = "\\p{M}".toRegex()
    private val wordSeparators = "[^\\p{L}\\p{N}]+".toRegex()

    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(combiningMarks, "").lowercase(Locale.ROOT).trim()

    fun filter(products: List<Product>, query: String, category: String?): List<Product> {
        val needle = normalized(query)
        return products.filter {
            (category == null || it.category == category) &&
                (needle.isEmpty() || normalized(it.title).contains(needle) ||
                    normalized(it.category).contains(needle) || normalized(categoryLabel(it.category)).contains(needle))
        }
    }

    /** Respaldo local para errores cortos de escritura, conservando la categoría seleccionada. */
    fun filterApproximate(products: List<Product>, query: String, category: String?): List<Product> {
        val exact = filter(products, query, category)
        val terms = normalized(query).split(wordSeparators).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return exact
        val approximate = products.filter { product ->
            if (category != null && product.category != category) return@filter false
            val words = normalized("${product.title} ${product.category} ${categoryLabel(product.category)}")
                .split(wordSeparators).filter { it.isNotEmpty() }
            terms.all { term ->
                words.any { word -> word.contains(term) ||
                    (term.length >= 4 && distance(term, word) <= if (term.length >= 6) 2 else 1) }
            }
        }
        return (exact + approximate).distinctBy { it.id }
    }

    private fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(
                current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
            val completed = current
            current = previous
            previous = completed
        }
        return previous[b.length]
    }

    fun categoryLabel(category: String) = when (category) {
        "electronics" -> "Electrónica"
        "jewelery" -> "Joyería"
        "men's clothing" -> "Ropa de hombre"
        "women's clothing" -> "Ropa de mujer"
        "beauty" -> "Belleza"
        "fragrances" -> "Fragancias"
        "furniture" -> "Muebles"
        "groceries" -> "Alimentos"
        "home-decoration" -> "Decoración"
        "kitchen-accessories" -> "Cocina"
        "laptops" -> "Laptops"
        "mens-shirts" -> "Camisas de hombre"
        "mens-shoes" -> "Calzado de hombre"
        "mens-watches" -> "Relojes de hombre"
        "mobile-accessories" -> "Accesorios móviles"
        "motorcycle" -> "Motocicletas"
        "skin-care" -> "Cuidado de la piel"
        "smartphones" -> "Teléfonos"
        "sports-accessories" -> "Deportes"
        "sunglasses" -> "Lentes de sol"
        "tablets" -> "Tabletas"
        "tops" -> "Blusas"
        "vehicle" -> "Vehículos"
        "womens-bags" -> "Bolsos"
        "womens-dresses" -> "Vestidos"
        "womens-jewellery" -> "Joyería"
        "womens-shoes" -> "Calzado de mujer"
        "womens-watches" -> "Relojes de mujer"
        else -> category
    }

    fun credentialsError(username: String, password: String): String? = when {
        username.trim().isEmpty() -> "Escribe tu usuario."
        username.trim().length !in 3..40 -> "El usuario debe tener entre 3 y 40 caracteres."
        password.isEmpty() -> "Escribe tu contraseña."
        password.length !in 8..128 -> "La contraseña debe tener entre 8 y 128 caracteres."
        else -> null
    }

    fun changeQuantity(cart: List<CartItem>, product: Product, delta: Int): List<CartItem> {
        val current = cart.firstOrNull { it.product.id == product.id }
        val quantity = ((current?.quantity ?: 0).toLong() + delta).coerceIn(0L, 99L).toInt()
        val rest = cart.filterNot { it.product.id == product.id }
        return if (quantity == 0) rest else rest + CartItem(product, quantity)
    }

    fun total(cart: List<CartItem>): BigDecimal = cart.fold(BigDecimal.ZERO) { sum, item ->
        sum + BigDecimal.valueOf(item.product.price).multiply(BigDecimal.valueOf(item.quantity.toLong()))
    }.setScale(2, RoundingMode.HALF_UP)
}
