pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    // (Optional) centralize plugin versions here
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.compose") version "1.7.0"
        id("com.android.application") version "8.9.1"
        id("com.google.gms.google-services") version "4.4.2"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "RealmsAI"
include(":app")
include(":desktopApp")
