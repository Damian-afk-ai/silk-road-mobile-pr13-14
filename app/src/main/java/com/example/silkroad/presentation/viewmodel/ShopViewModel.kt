package com.example.silkroad.presentation.viewmodel

import android.app.Application
import com.example.silkroad.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.silkroad.data.AuthService
import com.example.silkroad.data.local.LocalStorageService
import com.example.silkroad.data.remote.RetrofitInstance
import com.example.silkroad.data.remote.ProductService
import com.example.silkroad.data.remote.ProductSource
import com.example.silkroad.data.repository.OfflineProductRepository
import com.example.silkroad.data.search.ElasticsearchService
import com.example.silkroad.data.search.ProductSearchSource
import com.example.silkroad.data.search.SearchRepository
import com.example.silkroad.data.search.SearchResult
import com.example.silkroad.domain.ShopRules
import com.example.silkroad.domain.model.CartItem
import com.example.silkroad.domain.model.Product
import com.example.silkroad.domain.model.User
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopState(
    val user: User? = null, val products: List<Product> = emptyList(),
    val favorites: Set<Int> = emptySet(), val cart: List<CartItem> = emptyList(),
    val loading: Boolean = false, val loggingIn: Boolean = false,
    val cached: Boolean = true, val updated: Long = 0,
    val message: String? = null, val loginError: String? = null,
    val search: SearchResult? = null,
    val searchServerUrl: String = ""
)

private data class SearchInput(val query: String = "", val category: String? = null)

class ShopViewModel @JvmOverloads constructor(
    application: Application,
    source: ProductSource = ProductService(),
    searchSource: ProductSearchSource? = ElasticsearchService.fromBuildConfig()
) : AndroidViewModel(application) {
    private val storage = LocalStorageService(application)
    private val repository = OfflineProductRepository(source, storage)
    private val searchRepository = MutableStateFlow(SearchRepository(
        if (searchSource is ElasticsearchService) ElasticsearchService.fromBuildConfig(storage.searchServerUrl()) else searchSource))
    private val searchInput = MutableStateFlow(SearchInput())
    private val auth = AuthService(RetrofitInstance.api)
    private val savedUser = storage.session()
    private val mutable = MutableStateFlow(ShopState(user = savedUser, products = storage.products(),
        favorites = savedUser?.let { storage.favorites(it.username) } ?: emptySet(),
        cart = savedUser?.let { storage.cart(it.username) } ?: emptyList(), updated = storage.lastUpdated(),
        searchServerUrl = storage.searchServerUrl() ?: BuildConfig.ES_URL))
    val state = mutable.asStateFlow()

    init {
        observeSearch()
        if (savedUser != null) refresh()
    }

    /** La UI informa el texto y la categoría; el resultado llega en [ShopState.search]. */
    fun search(query: String, category: String?) { searchInput.value = SearchInput(query, category) }

    @OptIn(FlowPreview::class)
    private fun observeSearch() = viewModelScope.launch {
        // Espera a que el usuario deje de escribir antes de consultar Elasticsearch.
        val input = searchInput.debounce { if (it.query.isBlank()) 0L else 300L }
        val products = mutable.map { it.products }.distinctUntilChanged()
        combine(input, products, searchRepository) { i, p, repo -> Triple(i, p, repo) }.collectLatest { (i, p, repo) ->
            val result = repo.search(p, i.query, i.category)
            mutable.update { it.copy(search = result) }
        }
    }

    fun configureSearchServer(value: String): String? {
        val clean = value.trim().trimEnd('/')
        val uri = try { java.net.URI(clean) } catch (_: Exception) { null }
        if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.query != null || uri.fragment != null ||
            !uri.path.isNullOrEmpty() || (uri.port != -1 && uri.port !in 1..65535)) {
            return "Escribe una dirección como http://192.168.1.10:9200"
        }
        storage.saveSearchServerUrl(clean)
        mutable.update { it.copy(searchServerUrl = clean, search = null) }
        searchRepository.value = SearchRepository(ElasticsearchService.fromBuildConfig(clean))
        return null
    }

    fun login(username: String, password: String, demo: Boolean) {
        if (mutable.value.loggingIn) return
        mutable.update { it.copy(loggingIn = true, loginError = null) }
        viewModelScope.launch {
            auth.login(username, password, demo).fold(onSuccess = { user ->
                storage.saveSession(user)
                mutable.update { it.copy(user = user, loggingIn = false,
                    favorites = storage.favorites(user.username), cart = storage.cart(user.username)) }
                refresh()
            }, onFailure = { error -> mutable.update { it.copy(loggingIn = false, loginError = error.message) } })
        }
    }
    fun refresh() {
        if (mutable.value.loading) return
        mutable.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = repository.refresh()
            mutable.update { it.copy(products = result.products, loading = false,
                cached = result.cached, message = result.message, updated = storage.lastUpdated()) }
        }
    }
    fun toggleFavorite(product: Product) {
        val user = mutable.value.user ?: return
        val ids = mutable.value.favorites.toMutableSet()
        if (!ids.add(product.id)) ids.remove(product.id)
        storage.saveFavorites(user.username, ids)
        mutable.update { it.copy(favorites = ids) }
    }
    fun changeQuantity(product: Product, delta: Int) {
        val user = mutable.value.user ?: return
        val cart = ShopRules.changeQuantity(mutable.value.cart, product, delta)
        storage.saveCart(user.username, cart)
        mutable.update { it.copy(cart = cart) }
    }
    fun remove(product: Product) = changeQuantity(product, -99)
    fun checkout() {
        val user = mutable.value.user ?: return
        storage.saveCart(user.username, emptyList())
        mutable.update { it.copy(cart = emptyList()) }
    }
    fun logout() {
        storage.clearSession()
        mutable.update { it.copy(user = null, favorites = emptySet(), cart = emptyList(), loginError = null) }
    }
}
