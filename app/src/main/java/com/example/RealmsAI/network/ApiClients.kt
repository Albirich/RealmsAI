package com.example.RealmsAI.network

import com.example.RealmsAI.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiClients {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)         // e.g. "https://openrouter.ai/api/v1/"
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    private val mixtralClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.request().newBuilder()
                .header("Authorization", "Bearer ${BuildConfig.MIXTRAL_API_KEY}")
                .build()
                .let(chain::proceed)
        }
        .build()

    val mixtral: MixtralApiService by lazy {
        retrofit(
            BuildConfig.MIXTRAL_URL,   // your local.properties key
            mixtralClient
        )
            .create(MixtralApiService::class.java)
    }

    private val openAiClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.request().newBuilder()
                .header("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .build()
                .let(chain::proceed)
        }
        .build()

    val openai: OpenAiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(openAiClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenAiService::class.java)
    }
}
