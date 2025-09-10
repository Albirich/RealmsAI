import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")                                   // no version here (uses 2.0.21 from classpath)
    id("org.jetbrains.compose") version "1.7.0"     // keep this
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" // ← add this
}

// no repositories {} block here (keep repos centralized in settings)

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // If you later see a resolve error for material3, swap to:
    // implementation("org.jetbrains.compose.material3:material3-desktop:1.7.0")
}

kotlin { jvmToolchain(17) }

compose.desktop {
    application {
        mainClass = "com.realms.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "RealmsAI"
            packageVersion = "0.1.0"
        }
    }
}
