// top of your module-level build.gradle.kts
import java.util.*

val localProps = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.parcelize")
    id("com.google.gms.google-services")
}

android {
    namespace   = "com.example.RealmsAI"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.example.RealmsAI"
        minSdk        = 23
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"

        // grab your keys out of local.properties
        val openaiKey  = localProps.getProperty("OPENAI_API_KEY", "")
        val mixtralKey = localProps.getProperty("MIXTRAL_API_KEY", "")
        val mixtralUrl = localProps.getProperty("MIXTRAL_URL", "")

// and still do
        buildConfigField("String", "OPENAI_API_KEY",  "\"$openaiKey\"")
        buildConfigField("String", "MIXTRAL_API_KEY", "\"$mixtralKey\"")
        buildConfigField("String", "MIXTRAL_URL",     "\"$mixtralUrl\"")
    }

    buildFeatures {
        compose     = true             // turn on Jetpack Compose
        buildConfig = true             // emit your custom BuildConfig fields
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // use Java 11 (or 1.8) for both source & target
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        // make the Kotlin compiler emit bytecode for the same target
        jvmTarget = "11"
    }

    composeOptions {
        // you can hard-code or pull from your TOML’s versions
        kotlinCompilerExtensionVersion = "2.0.21"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")



    // AndroidX & UI
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-ktx:1.7.2")

    // Use the Compose BOM to pull in consistent versions:
    implementation(platform("androidx.compose:compose-bom:2024.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Gson (if you still need it elsewhere)
    implementation("com.google.code.gson:gson:2.10.1")

    // Retrofit / OkHttp
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

    // Moshi (reflection-only, no codegen)
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    // (optional) if you call .add(KotlinJsonAdapterFactory())
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.10")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    // Image loading
    implementation("com.github.bumptech.glide:glide:4.15.1")

    // Pull in the Compose BOM so all Compose libs stay in sync:
    implementation(platform("androidx.compose:compose-bom:${libs.versions.composeBom.get()}"))

    // Core Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Material 3
    implementation("androidx.compose.material3:material3")

    // Tooling for @Preview etc
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")


    implementation("com.google.firebase:firebase-auth-ktx:23.2.0")
    implementation("com.google.firebase:firebase-firestore-ktx:24.2.0")
}
