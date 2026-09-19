plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.bleyzos.ai.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.bleyzos.ai"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Фиксированный debug-keystore (app/debug.keystore, закоммичен в репозиторий).
    // Стандартный debug.keystore Android Studio генерируется локально на каждой машине
    // и имеет случайный SHA-1/SHA-256, из-за чего ломаются Google Sign-In, Maps API key
    // и т.п. Здесь у всех, кто собирает debug из этого репозитория, один и тот же ключ.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":modules:bleyzosAIDesignModule"))
}
