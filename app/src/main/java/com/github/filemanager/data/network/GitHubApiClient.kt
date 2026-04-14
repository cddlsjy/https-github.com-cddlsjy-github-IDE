package com.github.filemanager.data.network

import android.content.Context
import android.content.SharedPreferences
import com.github.filemanager.data.api.GitHubApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GitHubApiClient {

    private const val BASE_URL = "https://api.github.com/"
    private const val PREFS_NAME = "github_prefs"
    private const val KEY_TOKEN = "github_token"
    
    private var token: String? = null
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        token = prefs.getString(KEY_TOKEN, null)
    }

    fun setToken(newToken: String) {
        token = newToken
        prefs.edit().putString(KEY_TOKEN, newToken).apply()
    }

    fun clearToken() {
        token = null
        prefs.edit().remove(KEY_TOKEN).apply()
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
