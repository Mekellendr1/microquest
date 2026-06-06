package com.example.microquest.network

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.microquest.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    const val SERVER_IP = "10.0.2.2"
    const val BASE_URL = "http://$SERVER_IP:8090/"

    private val TOKEN_KEY = stringPreferencesKey("jwt_token")

    @Volatile
    var cachedToken: String? = null

    @Volatile
    private var _service: ApiService? = null

    fun get(context: Context): ApiService {
        if (_service == null) synchronized(this) {
            if (_service == null) _service = build(context.applicationContext)
        }
        return _service!!
    }

    suspend fun loadToken(ctx: Context) {
        cachedToken = ctx.dataStore.data.map { it[TOKEN_KEY] }.first()
        Log.d("ApiClient", "Token loaded: ${if (cachedToken != null) "yes" else "none"}")
    }

    private fun build(ctx: Context): ApiService {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                cachedToken?.let { addHeader("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
