plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.neoarama.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neoarama.app"
        minSdk = 26
        targetSdk = 35
        // GitHub'daki her derleme numarası bir öncekinden büyüktür; güncellemeler sorunsuz kurulur.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.0.$run"
    }

    // Her derlemede AYNI anahtar kullanılır; böylece yeni APK eskisinin üzerine güncelleme olarak kurulur.
    // Anahtar depoda değil, GitHub "secrets" içinde durur; derleme sırasında ortam değişkenleriyle gelir.
    val keystorePath = System.getenv("NEO_KEYSTORE_FILE")
    val keystorePass = System.getenv("NEO_KEYSTORE_PASSWORD")
    val hasKey = !keystorePath.isNullOrBlank() && !keystorePass.isNullOrBlank()

    signingConfigs {
        create("neo") {
            if (hasKey) {
                storeFile = file(keystorePath!!)
                storePassword = keystorePass
                keyAlias = "neoarama"
                keyPassword = keystorePass
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            if (hasKey) signingConfig = signingConfigs.getByName("neo")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (hasKey) signingConfigs.getByName("neo") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
