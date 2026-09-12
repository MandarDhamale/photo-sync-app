package com.mandar.photosync

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private var apiService: PhotoSyncApiService? = null

    fun getApi(context: Context): PhotoSyncApiService {
        if (apiService == null) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // Interceptor to dynamically change Host, Port, and add Auth Header
            val dynamicHostInterceptor = Interceptor { chain ->
                var request = chain.request()
                val prefs = context.getSharedPreferences("photosync", Context.MODE_PRIVATE)
                val ip = prefs.getString("server_ip", null)
                val port = prefs.getInt("server_port", 8080)
                val token = prefs.getString("server_token", null)

                if (ip != null) {
                    val newUrl = request.url.newBuilder()
                        .scheme("http")
                        .host(ip)
                        .port(port)
                        .build()

                    val requestBuilder = request.newBuilder().url(newUrl)
                    if (token != null) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                    request = requestBuilder.build()
                }
                chain.proceed(request)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(dynamicHostInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            apiService = Retrofit.Builder()
                .baseUrl("http://localhost/") // This will be replaced by the interceptor
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PhotoSyncApiService::class.java)
        }
        return apiService!!
    }
}