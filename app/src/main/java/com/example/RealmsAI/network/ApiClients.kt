// ApiClients.kt
package com.example.RealmsAI.network

import android.util.Log
import com.example.RealmsAI.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiClients {
    // 1) Shared Moshi
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // 2) A helper that builds a Retrofit instance with a custom client
    private fun retrofit(baseUrl: String, client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    // 3) OpenAI client (with logging)
    private val openAiClient = OkHttpClient.Builder()
        // <-- Add this logging interceptor -->
        .addInterceptor(
            HttpLoggingInterceptor { message -> Log.d("HTTP-OpenAI", message) }
                .apply { level = HttpLoggingInterceptor.Level.BODY }
        )
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()


    val openai: OpenAiService by lazy {
        retrofit("https://api.openai.com/v1/", openAiClient)
            .create(OpenAiService::class.java)
    }

    // 4) Mixtral / OpenRouter client
    private val mixtralClient: OkHttpClient by lazy {
        // Add a logging interceptor so you actually see your requests & responses
        val logger = HttpLoggingInterceptor { message -> Log.d("HTTP", message) }
            .apply { level = HttpLoggingInterceptor.Level.BODY }

        OkHttpClient.Builder()
            .addInterceptor(logger)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.MIXTRAL_API_KEY}")
                    // optional, but if your key has no Referer restriction you can omit:
                    .addHeader("HTTP-Referer", "http://localhost")
                    .addHeader("X-Title", "RealmsAI")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    val mixtral: MixtralApiService by lazy {
        // Make sure your MIXTRAL_URL in local.properties is exactly "https://openrouter.ai/api/v1/"
        retrofit(BuildConfig.MIXTRAL_URL, mixtralClient)
            .create(MixtralApiService::class.java)
    }
}
