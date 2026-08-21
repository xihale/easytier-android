plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.easytier.android"
    compileSdk = 36

    defaultConfig {
        // 加 .native 后缀与原版（com.kkrainbow.easytier，Tauri 版）区分，可并排安装；
        // namespace 保持不变（native 是 Java 关键字，不能用作代码包名）
        applicationId = "com.easytier.android.native"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "2.0.0-native"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 本地开发先用 debug 签名便于安装调试；正式发布时换独立 keystore
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isJniDebuggable = true
            packaging {
                jniLibs.keepDebugSymbols += "**/libeasytier*.so"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // 压缩 .so（默认 Stored 直读），APK 减 ~1/3；安装后解压到本地，运行时无差异
        jniLibs.useLegacyPackaging = true
    }
}

// Kotlin 2.4 默认 JVM target 21，与 compileOptions 17 对齐
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // TOML 解析（导入配置用）；checker-qual 为 tomlj 编译期注解依赖
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("org.checkerframework:checker-qual:3.49.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 仅引入实际用到的扩展图标（core 集缺少这些）
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
