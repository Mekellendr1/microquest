package com.example.microquest.network

import android.content.Context
import android.net.wifi.WifiManager
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object ApiClient {

    const val PORT = 8090

    /**
     * Список IP-адресов для автоопределения сервера.
     * Порядок проверки:
     *   1. 192.168.137.1 — Windows Mobile Hotspot (телефон раздаёт интернет с компа)
     *   2. Шлюз по DHCP  — обычная Wi-Fi сеть (роутер → компьютер)
     *   3. 10.0.2.2      — эмулятор Android
     */
    @Volatile var BASE_URL: String = "http://192.168.137.1:$PORT/"
        private set

    private val TOKEN_KEY = stringPreferencesKey("jwt_token")

    @Volatile var cachedToken: String? = null

    @Volatile private var _service: ApiService? = null

    fun get(context: Context): ApiService =
        _service ?: synchronized(this) {
            _service ?: build(context.applicationContext).also { _service = it }
        }

    /**
     * Вызывается один раз при старте приложения (в AuthViewModel).
     * Пробует кандидатов по очереди, выбирает первый отвечающий сервер,
     * затем пересоздаёт Retrofit-клиент с правильным BASE_URL.
     */
    suspend fun init(ctx: Context) {
        cachedToken = ctx.dataStore.data.map { it[TOKEN_KEY] }.first()
        Log.d("ApiClient", "Token loaded: ${if (cachedToken != null) "yes" else "none"}")

        val candidates = buildList {
            add("192.168.137.1")          // Windows Mobile Hotspot — комп всегда здесь
            add(getGatewayIp(ctx))         // шлюз из DHCP (обычный Wi-Fi роутер)
            add("10.0.2.2")               // эмулятор Android
        }.filterNotNull().distinct()

        for (ip in candidates) {
            val url = "http://$ip:$PORT/health"
            Log.d("ApiClient", "Checking $url …")
            if (pingServer(url)) {
                BASE_URL = "http://$ip:$PORT/"
                Log.d("ApiClient", "Server found at $BASE_URL")
                break
            }
        }
        if (BASE_URL.isEmpty()) Log.w("ApiClient", "No server found, keeping default")

        // Пересоздать клиент с актуальным BASE_URL
        synchronized(this) { _service = build(ctx.applicationContext) }
    }

    /** @deprecated use init() instead */
    suspend fun loadToken(ctx: Context) = init(ctx)

    private fun getGatewayIp(ctx: Context): String? = runCatching {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val gw = wm.dhcpInfo?.gateway ?: return@runCatching null
        if (gw == 0) return@runCatching null
        "%d.%d.%d.%d".format(gw and 0xff, gw shr 8 and 0xff, gw shr 16 and 0xff, gw shr 24 and 0xff)
    }.getOrNull()

    private fun pingServer(url: String): Boolean = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 2_000
        conn.readTimeout    = 2_000
        conn.requestMethod  = "GET"
        val code = conn.responseCode
        conn.disconnect()
        code in 200..299
    }.getOrDefault(false)

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
