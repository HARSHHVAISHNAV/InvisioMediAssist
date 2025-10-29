package com.muggles.invisioassist.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class ImageRequest(val image: String)

data class MedicineResponse(
    val description: String,
    val match_type: String,
    val medicine_name: String,
    val side_effects: String
)

interface ApiService {
    @Headers("Content-Type: application/json")
    @POST("/process_image")
    fun sendImage(@Body request: ImageRequest): Call<MedicineResponse>
}
