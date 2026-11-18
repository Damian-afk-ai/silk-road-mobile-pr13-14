package com.example.silkroad.data.remote

import com.example.silkroad.domain.model.Product
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Body

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String?)

interface FakeStoreApi : ProductSource {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    @GET("products")
    override suspend fun getProducts(): List<Product>

    @GET("products/categories")
    suspend fun getCategories(): List<String>

    @GET("products/category/{category}")
    suspend fun getProductsByCategory(@Path("category") category: String): List<Product>
}
