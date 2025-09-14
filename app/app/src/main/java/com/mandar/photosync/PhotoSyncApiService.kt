package com.mandar.photosync // Make sure this is your package name

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PhotoSyncApiService {

    // Add this simple test endpoint
    @GET("test")
    suspend fun testConnection(): Response<String> // Returns a plain String response

    // existing upload endpoint
    @Multipart
    @POST("upload")
    suspend fun uploadPhoto(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part?
    ): Response<ApiResponse>
}