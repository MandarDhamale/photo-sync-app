package com.mandar.photosync

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PhotoSyncApiService {

    @GET("api/test")
    suspend fun testConnection(): Response<String>

    @Multipart
    @POST("api/upload")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part?
    ): Response<ApiResponse>
}