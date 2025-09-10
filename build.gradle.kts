plugins {
    // Android stuff (only if your :app module needs it)
    id("com.android.application") version "8.9.1" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false

    // Kotlin & Compose (desktop + compiler)
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
}
}

