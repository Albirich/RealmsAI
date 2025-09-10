import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")                 // keep versionless to use 2.0.21 already on classpath
    id("org.jetbrains.compose") version "1.7.0"   // ← add this version
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
