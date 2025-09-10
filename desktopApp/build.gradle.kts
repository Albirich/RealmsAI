import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  kotlin("jvm")
  id("org.jetbrains.kotlin.plugin.compose")   // required on Kotlin 2.x
  id("org.jetbrains.compose")                 // Compose Multiplatform Gradle plugin
  application
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

kotlin { jvmToolchain(17) }

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "RealmsAI"
            packageVersion = "0.1.0"
        }
    }
}
