plugins {
    id("com.android.application")
}

android {
    namespace = "com.douyin.clearmode"
    // libxposed service/api 102.0.0 的 AAR 元数据要求 minCompileSdk=37，
    // 因此 compileSdk 必须 >= 37，且 AGP 需支持 minor SDK（8.9+）。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.douyin.clearmode"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // LSPosed 关键：META-INF/xposed/* 必须打包进 APK，且不被资源合并排除
    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    // Hook 侧：libxposed API 仅编译期依赖，不打进 APK
    compileOnly("io.github.libxposed:api:102.0.0")

    // 模块 App 与框架通信（Remote Preferences / scope / hot reload）
    implementation("io.github.libxposed:service:102.0.0")

    // Java 模板使用 androidx.annotation.NonNull
    compileOnly("androidx.annotation:annotation:1.9.1")

    // 模块 UI
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.8.2")
    implementation("com.google.android.material:material:1.11.0")
}
