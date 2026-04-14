package com.github.filemanager.data.network

import com.github.filemanager.data.api.GitHubApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GitHubApiClient {

    private const val BASE_URL = "https://api.github.com/"
    
    private var token: String? = null

    fun setToken(newToken: String) {
        token = newToken
    }

    fun clearToken() {
        token = null
    }

    fun getToken(): String? = token

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { token })
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val apiService: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
    }
}
