import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")                 // version is provided from classpath (AGP pulls Kotlin 2.0.21)
    id("org.jetbrains.compose") version "1.7.0"  // keep the version here (or centralize in settings)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
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
