plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)     apply false
    alias(libs.plugins.kotlin.compose)     apply false
    // tell Gradle about the Google services plugin
    id("com.google.gms.google-services")   apply false
}
