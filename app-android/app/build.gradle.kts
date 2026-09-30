plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.monitoridoso"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.monitoridoso"
        minSdk = 26            // Android 8.0 — cobre praticamente todo celular de idoso em uso
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-mvp"
    }
    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))                  // núcleo testado (5/5)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    // Wake word offline pt-BR (criar a palavra "socorro" no console da Picovoice
    // e colar a accessKey em WakeWordService.kt):
    implementation("ai.picovoice:porcupine-android:3.0.2")
    // Rota A do SOS (backend Twilio):
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Rotina por voz: pulso pela câmera sem preview (rPPG)
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
}
