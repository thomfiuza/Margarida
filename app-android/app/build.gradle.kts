plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "br.com.monitoridoso"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.monitoridoso"
        minSdk = 26            // Android 8.0 — cobre praticamente todo celular de idoso em uso
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0-rotina"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

chaquopy {
    defaultConfig {
        pip {
            install("numpy")
            install("opencv-python")
        }
    }
}

dependencies {
    implementation(project(":core"))                  // núcleo testado (13/13)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Wake word offline pt-BR (criar a palavra "socorro" no console da Picovoice
    // e colar a accessKey em WakeWordService.kt):
    implementation("ai.picovoice:porcupine-android:3.0.2")
    implementation("com.alphacephei:vosk-android:0.3.47")
    // Rota A do SOS (backend Twilio):
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
