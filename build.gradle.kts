plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)     apply false
    alias(libs.plugins.kotlin.compose)     apply false
    // tell Gradle about the Google services plugin
    id("com.google.gms.google-services")   apply false
  kotlin("jvm") version "2.0.21" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
  id("org.jetbrains.compose") version "1.7.0" apply false
}

