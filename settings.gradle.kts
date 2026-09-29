pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.1.21"
        id("org.jetbrains.kotlin.jvm") version "2.1.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "CDG"

// Lógica pura (parseo de notificaciones, categorías, análisis): compila y se testea sin Android.
include(":core")

// La app Android solo se incluye si hay un SDK de Android disponible.
val localProps = file("local.properties")
val hasSdkDir = localProps.exists() && localProps.readText().contains("sdk.dir")
if (hasSdkDir || System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) {
    include(":app")
} else {
    logger.lifecycle("Android SDK no encontrado: se compila solo :core")
}
