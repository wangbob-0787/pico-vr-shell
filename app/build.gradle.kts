plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lele.vrshell"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lele.vrshell"
        minSdk = 29
        // 关键：28 可以绕开 Android 10 起「不能把别的 App 拉到副显示器」的限制
        targetSdk = 28
        versionName = "1.0"
        versionCode = 1
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}
