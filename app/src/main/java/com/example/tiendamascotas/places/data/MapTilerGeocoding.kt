package com.example.tiendamascotas.places.data

import com.example.tiendamascotas.BuildConfig
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ----- DTOs -----
data class GeocodingResponse(val features: List<GeocodingFeature>?)
data class GeocodingFeature(
    @SerializedName("place_name") val placeName: String?,
    val geometry: Geometry?
)
data class Geometry(
    val type: String?,
    val coordinates: List<Double>? // [lon, lat]
)

// ----- API -----
interface MapTilerGeocodingApi {
    // GET https://api.maptiler.com/geocoding/{query}.json?key=...&language=es&limit=5
    @GET("geocoding/{query}.json")
    suspend fun search(
        @Path("query") query: String,
        @Query("key") key: String = BuildConfig.MAPTILER_API_KEY,
        @Query("language") lang: String = "es",
        @Query("limit") limit: Int = 5
    ): GeocodingResponse
}

object MapTilerGeocodingClient {
    val api: MapTilerGeocodingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.maptiler.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MapTilerGeocodingApi::class.java)
    }
}
