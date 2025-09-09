import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}


repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
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
