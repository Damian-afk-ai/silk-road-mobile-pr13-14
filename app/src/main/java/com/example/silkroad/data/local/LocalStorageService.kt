package com.example.silkroad.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.silkroad.domain.model.CartItem
import com.example.silkroad.domain.model.Product
import com.example.silkroad.domain.model.User
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

interface ProductCache {
    fun products(): List<Product>
    fun saveProducts(products: List<Product>)
    fun lastUpdated(): Long
}

class LocalStorageService internal constructor(private val preferences: SharedPreferences) : ProductCache {
    constructor(context: Context) : this(context.getSharedPreferences("silk_road_v1", Context.MODE_PRIVATE))

    private val gson = Gson()
    private inline fun <reified T> read(key: String, fallback: T): T = try {
        preferences.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) } ?: fallback
    } catch (_: RuntimeException) { fallback }

    override fun products(): List<Product> = read<List<Product>>("products", emptyList()).filter {
        it.id > 0 && it.price.isFinite() && it.price >= 0 && !it.title.isNullOrBlank() &&
            !it.category.isNullOrBlank() && it.description != null && it.image != null
    }
    override fun saveProducts(products: List<Product>) {
        // commit on IO: a successful refresh is only reported after the cache is durable.
        check(preferences.edit().putString("products", gson.toJson(products))
            .putLong("updated", System.currentTimeMillis()).commit()) { "No se pudo guardar el catálogo." }
    }
    override fun lastUpdated() = preferences.getLong("updated", 0)
    fun session(): User? = preferences.getString("session", null)?.takeIf { it.isNotBlank() }?.let { User(it) }
    fun saveSession(user: User) { preferences.edit().putString("session", user.username).apply() }
    fun clearSession() { preferences.edit().remove("session").apply() }
    fun searchServerUrl(): String? = preferences.getString("search_server_url", null)
    fun saveSearchServerUrl(url: String) { preferences.edit().putString("search_server_url", url).apply() }
    // The username is encoded so dots and separators cannot collide with other users' keys.
    private fun key(user: String, kind: String) = "$kind:" + android.util.Base64.encodeToString(
        user.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    fun favorites(user: String): Set<Int> = read<List<Int>>(key(user, "favorites"), emptyList()).toSet()
    fun saveFavorites(user: String, ids: Set<Int>) {
        preferences.edit().putString(key(user, "favorites"), gson.toJson(ids.toList())).apply()
    }
    fun cart(user: String): List<CartItem> = read<List<CartItem>>(key(user, "cart"), emptyList())
        .filter { it.product != null && it.product.id > 0 && it.quantity in 1..99 &&
            it.product.price.isFinite() && it.product.price >= 0 }.distinctBy { it.product.id }
    fun saveCart(user: String, items: List<CartItem>) {
        preferences.edit().putString(key(user, "cart"), gson.toJson(items)).apply()
    }
}
