pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.android") version "2.0.21"   // if you use it in :app
        id("org.jetbrains.compose") version "1.7.0"
        id("com.android.application") version "8.9.1"          // whatever you already use
        id("com.google.gms.google-services") version "4.4.2"   // if present in root/app
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}
rootProject.name = "RealmsAI-DesktopBootstrap"
include(":desktopApp")
