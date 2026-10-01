plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.localthemeloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.localthemeloader"
        minSdk = 29
        targetSdk = 35
        versionCode = 200
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    signingConfigs {
        create("release") {
            val signingPath = System.getenv("SHIGUANG_KEYSTORE")
            if (!signingPath.isNullOrBlank()) {
                storeFile = file(signingPath)
                storePassword = System.getenv("SHIGUANG_STORE_PASSWORD")
                keyAlias = "shiguang"
                keyPassword = System.getenv("SHIGUANG_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".debug" }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isDebuggable = false
        }
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
