package com.example.servicedeskapk.data.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import com.example.servicedeskapk.ServiceDeskApp
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Persistent CookieJar — сохраняет сессионные куки между запросами
 * и между перезапусками приложения (через SharedPreferences).
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    init {
        // Загружаем куки из SharedPreferences при инициализации
        loadCookies()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        cookieStore[host] = cookies.toMutableList()
        // Сохраняем в SharedPreferences
        saveCookies()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        // Фильтруем просроченные куки
        val now = System.currentTimeMillis()
        val valid = cookies.filter { it.expiresAt > now }
        return valid
    }

    fun clearCookies() {
        cookieStore.clear()
        prefs.edit().clear().apply()
    }

    private fun saveCookies() {
        val editor = prefs.edit()
        editor.clear()
        for ((host, cookies) in cookieStore) {
            val cookieStrings = cookies.map { cookie ->
                "${cookie.name}=${cookie.value}|${cookie.domain}|${cookie.path}|${cookie.expiresAt}|${cookie.secure}|${cookie.httpOnly}"
            }
            editor.putStringSet("cookies_$host", cookieStrings.toSet())
        }
        editor.apply()
    }

    private fun loadCookies() {
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith("cookies_") && value is Set<*>) {
                val host = key.removePrefix("cookies_")
                val cookies = mutableListOf<Cookie>()
                for (cookieString in value) {
                    if (cookieString is String) {
                        try {
                            val parts = cookieString.split("|")
                            if (parts.size >= 6) {
                                val nameValue = parts[0].split("=", limit = 2)
                                if (nameValue.size == 2) {
                                    val builder = Cookie.Builder()
                                        .name(nameValue[0])
                                        .value(nameValue[1])
                                        .domain(parts[1])
                                        .path(parts[2])
                                        .expiresAt(parts[3].toLongOrNull() ?: 0L)
                                    if (parts[4] == "true") builder.secure()
                                    if (parts[5] == "true") builder.httpOnly()
                                    cookies.add(builder.build())
                                }
                            }
                        } catch (_: Exception) {
                            // Пропускаем некорректные куки
                        }
                    }
                }
                if (cookies.isNotEmpty()) {
                    cookieStore[host] = cookies
                }
            }
        }
    }
}

/**
 * Синглтон для управления Retrofit клиентом.
 * Поддерживает изменение базового URL (адреса сервера).
 */
object RetrofitClient {

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null
    private var currentBaseUrl: String = ""
    var cookieJar: PersistentCookieJar? = null
        private set

    fun getApiService(context: Context, baseUrl: String): ApiService {
        val prefs = (context.applicationContext as? ServiceDeskApp)?.preferencesManager
        val useStandardAuthRoutes = prefs?.useStandardAuthRoutes ?: false
        val cacheKey = "$baseUrl|$useStandardAuthRoutes"
        if (apiService == null || cacheKey != currentBaseUrl) {
            currentBaseUrl = cacheKey

            if (cookieJar == null) {
                cookieJar = PersistentCookieJar(context.applicationContext)
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val builder = OkHttpClient.Builder()
                .cookieJar(cookieJar!!)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)

            if (useStandardAuthRoutes) {
                builder.addInterceptor(Interceptor { chain ->
                    val req = chain.request()
                    val path = req.url.encodedPath
                    val newPath = when {
                        path == "/api/auth/login" -> "/login"
                        path == "/api/auth/register" -> "/register"
                        path == "/api/auth/me" -> "/me"
                        path == "/api/auth/logout" -> "/logout"
                        path == "/api/auth/change-password" -> "/change-password"
                        else -> null
                    }
                    if (newPath != null) {
                        val newUrl = req.url.newBuilder().encodedPath(newPath).build()
                        chain.proceed(req.newBuilder().url(newUrl).build())
                    } else {
                        chain.proceed(req)
                    }
                })
            }

            // Для HTTPS с самоподписанным сертификатом — доверяем всем сертификатам
            if (baseUrl.startsWith("https://", ignoreCase = true)) {
                try {
                    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    })
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, trustAllCerts, SecureRandom())
                    builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    builder.hostnameVerifier { _, _ -> true }
                } catch (e: Exception) {
                    // Оставляем стандартную проверку при ошибке
                }
            }

            val okHttpClient = builder.build()

            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit!!.create(ApiService::class.java)
        }
        return apiService!!
    }

    fun clearSession() {
        cookieJar?.clearCookies()
    }

    fun reset() {
        retrofit = null
        apiService = null
        currentBaseUrl = ""
        cookieJar?.clearCookies()
        cookieJar = null
    }
}
