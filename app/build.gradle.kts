plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.pixelwhisper"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pixelwhisper"
        minSdk = 37
        targetSdk = 37
        versionCode = 2
        versionName = "2.0.0"
        ndk { abiFilters += "arm64-v8a" }
    }
}

dependencies {
    // sherpa-onnx release AAR (Whisper + Silero VAD on ONNX Runtime), fetched by scripts/setup-phone.sh.
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
}
