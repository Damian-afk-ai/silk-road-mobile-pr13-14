package com.example.silkroad.data.remote

import com.example.silkroad.domain.model.Product
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

fun interface ProductSource {
    suspend fun getProducts(): List<Product>
}

data class DummyProduct(val id: Int, val title: String, val price: Double,
    val description: String, val category: String, val thumbnail: String)
data class DummyCatalog(val products: List<DummyProduct>)
interface DummyJsonApi {
    @GET("api/products.php")
    suspend fun products(): DummyCatalog
}

class ProductService(private val baseUrl: String = "https://silkroad.devnationqro.com/") : ProductSource {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).build()
    private val api = Retrofit.Builder().baseUrl(baseUrl).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(DummyJsonApi::class.java)
    override suspend fun getProducts() = api.products().products.map {
        Product(it.id, it.title, it.price, it.description, it.category, it.thumbnail)
    }
}
