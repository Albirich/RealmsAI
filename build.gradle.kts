plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false

    // Kotlin
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false

    // JetBrains Compose Multiplatform
    id("org.jetbrains.compose") version "1.7.0" apply false

    // Google Services (if the app module needs it)
    id("com.google.gms.google-services") version "4.4.2" apply false
}
