package com.example.silkroad.data.search

import com.example.silkroad.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/** Motor de búsqueda remoto. Devuelve los ids de producto ordenados por relevancia. */
fun interface ProductSearchSource {
    suspend fun search(query: String, category: String?): List<Int>
}

data class EsHit(@SerializedName("_id") val id: String)
data class EsHits(val hits: List<EsHit>)
data class EsResponse(val hits: EsHits)

interface ElasticsearchApi {
    @POST("{index}/_search")
    suspend fun search(@Path("index") index: String, @Body body: Map<String, @JvmSuppressWildcards Any>): EsResponse
}

object ElasticQuery {
    const val MAX_RESULTS = 200

    /**
     * Búsqueda tolerante a errores de escritura (fuzziness) y por prefijo (search-as-you-type).
     * Los acentos y mayúsculas se ignoran gracias al analizador "folded" del índice.
     */
    fun build(query: String, category: String?): Map<String, Any> {
        val text = query.trim()
        val bool = mutableMapOf<String, Any>(
            "should" to listOf(
                mapOf("multi_match" to mapOf("query" to text, "fuzziness" to "AUTO",
                    "fields" to listOf("title^3", "categoryLabel^2", "category.text^2", "description"))),
                mapOf("multi_match" to mapOf("query" to text,
                    "fields" to listOf("title.prefix^2", "categoryLabel.prefix")))
            ),
            "minimum_should_match" to 1
        )
        if (category != null) bool["filter"] = listOf(mapOf("term" to mapOf("category" to category)))
        return mapOf("size" to MAX_RESULTS, "_source" to false, "query" to mapOf("bool" to bool))
    }
}

class ElasticsearchService(baseUrl: String, apiKey: String, private val index: String = DEFAULT_INDEX) : ProductSearchSource {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            // La API key solo tiene permiso de lectura sobre el índice de productos.
            if (apiKey.isNotBlank()) request.header("Authorization", "ApiKey $apiKey")
            chain.proceed(request.build())
        }
        // Tiempos cortos: si el servidor no responde se usa el filtro local.
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ElasticsearchApi::class.java)

    override suspend fun search(query: String, category: String?): List<Int> =
        api.search(index, ElasticQuery.build(query, category)).hits.hits.mapNotNull { it.id.toIntOrNull() }

    companion object {
        const val DEFAULT_INDEX = "silkroad-products"

        /** null si no se configuró `elasticsearch.url` en local.properties. */
        fun fromBuildConfig(serverUrl: String? = null): ProductSearchSource? =
            (serverUrl ?: BuildConfig.ES_URL).takeIf { it.isNotBlank() }?.let {
            ElasticsearchService(it, BuildConfig.ES_API_KEY, BuildConfig.ES_INDEX.ifBlank { DEFAULT_INDEX })
        }
    }
}
