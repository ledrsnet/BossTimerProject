plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bosstimer"
    compileSdk = 34 // 目标 API 34

    defaultConfig {
        applicationId = "com.bosstimer.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // 启用 Compose
    buildFeatures {
        compose = true
    }

    // 配置 Compose 编译器版本
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // 确保与 Kotlin 版本兼容
    }

    // JDK 17 配置
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // 禁用打包资源警告
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 签名配置 (Release 模式需要配置，但Debug模式无需配置)
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {

    // Compose 核心依赖
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose UI 和 Material3
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // LiveData & ViewModel (Compose 集成)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // 可选：如果要实现 Room 或 Preference
    // implementation("androidx.room:room-runtime:2.6.1")
    // implementation("androidx.room:room-ktx:2.6.1")
    // kapt("androidx.room:room-compiler:2.6.1")
}
