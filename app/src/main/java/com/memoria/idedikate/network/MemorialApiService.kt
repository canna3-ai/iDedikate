package com.memoria.idedikate.network

import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class MemorialItem(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val message: String = ""
)

interface MemorialApiService {
    @GET("memorials")
    suspend fun getMemorials(
        @Query("lat") lat: Double, 
        @Query("lng") lng: Double
    ): List<MemorialItem>

    @POST("memorials")
    suspend fun dropPin(@Body item: MemorialItem): MemorialItem
}

object RetrofitClient {
    // Mock base URL for testing
    private const val BASE_URL = "https://example.com/api/"

    val apiService: MemorialApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(MemorialApiService::class.java)
    }
}
