package com.turboguys.myaibot.di

import com.turboguys.myaibot.data.api.GigaChatApi
import com.turboguys.myaibot.data.api.GigaChatOAuthApi
import com.turboguys.myaibot.data.repository.ChatRepository
import com.turboguys.myaibot.presentation.chat.ChatViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

val appModule = module {
    single {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // Создаем TrustManager, который принимает все сертификаты
        // ВНИМАНИЕ: Это решение для разработки. Для продакшена используйте правильные сертификаты
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true } // Принимаем все hostname
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Retrofit для OAuth (отдельный базовый URL)
    single(named("oauthRetrofit")) {
        Retrofit.Builder()
            .baseUrl("https://ngw.devices.sberbank.ru:9443/api/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    // Retrofit для GigaChat API
    single(named("apiRetrofit")) {
        Retrofit.Builder()
            .baseUrl("https://gigachat.devices.sberbank.ru/api/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    single<GigaChatOAuthApi> {
        get<Retrofit>(named("oauthRetrofit")).create(GigaChatOAuthApi::class.java)
    }
    
    single<GigaChatApi> {
        get<Retrofit>(named("apiRetrofit")).create(GigaChatApi::class.java)
    }
    
    single {
        // Используем ключ из AuthConfig (который должен быть в .gitignore)
        val authKey = com.turboguys.myaibot.config.AuthConfig.AUTHORIZATION_KEY
        ChatRepository(
            api = get(),
            oauthApi = get(),
            authKey = authKey
        )
    }
    
    viewModel { ChatViewModel(repository = get()) }
}

