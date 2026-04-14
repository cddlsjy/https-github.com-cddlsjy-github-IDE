package com.github.filemanager.data.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenProvider()
        
        val request = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
        } else {
            originalRequest
        }
        
        return chain.proceed(request)
    }
}
