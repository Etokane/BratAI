plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nikolai.bratai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nikolai.bratai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

