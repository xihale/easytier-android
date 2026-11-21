plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 版本号由 git tag 派生，避免 build.gradle 与 tag 手工同步漂移：
// versionName = git describe 去 v 前缀——精确打在 tag 上时是干净 semver（如 0.1.2），
//   tag 之后形如 0.1.2-1-g<sha>，工作区有未提交改动再加 -dirty；
// versionCode = major*1_000_000 + minor*10_000 + patch*100 + tag 后提交数（封顶 99），单调递增
fun runGit(vararg args: String): String? = runCatching {
    val execOutput = providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
        isIgnoreExitValue = true
    }
    val exitCode = execOutput.result.get().exitValue
    execOutput.standardOutput.asText.get().trim().takeIf { exitCode == 0 }
}.getOrNull()

val appVersion: Pair<String, Int> = run {
    val describe = runGit("describe", "--tags", "--long", "--dirty", "--always")
    val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-(\d+)-g[0-9a-f]+)?(-dirty)?$""")
        .find(describe.orEmpty())
    if (match == null) {
        logger.warn("无法从 git describe 解析版本号（describe=$describe），回退 0.0.0")
        "0.0.0" to 1
    } else {
        val (major, minor, patch) = match.destructured
        val commitsAfter = match.groupValues[4].toIntOrNull() ?: 0
        // 精确在 tag 上时去掉 --long 的 -0-g<sha> 尾巴，发布产物拿到干净 semver
        val name = describe!!.removePrefix("v").let {
            if (commitsAfter == 0) it.replace(Regex("""-0-g[0-9a-f]+"""), "") else it
        }
        val code = major.toInt() * 1_000_000 + minor.toInt() * 10_000 +
            patch.toInt() * 100 + commitsAfter.coerceAtMost(99)
        name to code
    }
}

// 正式签名必须从环境变量提供；缺失时 release 构建直接失败，不再回退 debug 签名。
// debug 签名包与正式包签名不同，会被系统视为另一个发布者，后续无法覆盖安装。
val signingEnvVars = listOf(
    "SIGNING_KEYSTORE_FILE",
    "SIGNING_KEYSTORE_PASSWORD",
    "SIGNING_KEY_ALIAS",
    "SIGNING_KEY_PASSWORD",
)
val signingEnv = signingEnvVars.associateWith { name ->
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
}
val missingSigningEnv = signingEnvVars.filter { signingEnv[it] == null }
val hasReleaseSigning = missingSigningEnv.isEmpty()

android {
    namespace = "com.easytier.android"
    compileSdk = 36

    defaultConfig {
        // 加 .native 后缀与原版（com.kkrainbow.easytier，Tauri 版）区分，可并排安装；
        // namespace 保持不变（native 是 Java 关键字，不能用作代码包名）
        applicationId = "com.easytier.android.native"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersion.second
        versionName = appVersion.first

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // 正式签名只在环境变量齐全时创建；缺失时产物无签名，由下方 taskGraph 校验直接报错
    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(signingEnv.getValue("SIGNING_KEYSTORE_FILE")!!)
                check(storeFile!!.exists()) {
                    "签名 keystore 不存在：${storeFile!!.absolutePath}（SIGNING_KEYSTORE_FILE）"
                }
                storePassword = signingEnv.getValue("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = signingEnv.getValue("SIGNING_KEY_ALIAS")
                keyPassword = signingEnv.getValue("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 未配置签名时产物不带签名，由下方 taskGraph 校验直接报错
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // 与 release 版（com.easytier.android.native）区分，可并排安装
            applicationIdSuffix = ".debug"
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

    androidResources {
        localeFilters += listOf("en", "zh-rCN")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/**/LICENSE.txt",
            "/META-INF/*.version",
            "DebugProbesKt.bin",
        )
        // 压缩 .so（默认 Stored 直读），APK 减 ~1/3；安装后解压到本地，运行时无差异
        jniLibs.useLegacyPackaging = true
    }
}

// release 变体缺少签名环境变量时直接失败，避免静默产出 debug 签名/无签名包
val projectPathPrefix = if (path == ":") ":" else "$path:"
gradle.taskGraph.whenReady {
    if (hasReleaseSigning) return@whenReady
    val releaseTask = allTasks.firstOrNull { task ->
        task.path.startsWith(projectPathPrefix) && task.name.contains("Release") &&
            listOf("assemble", "bundle", "package", "sign").any { task.name.startsWith(it) }
    }
    if (releaseTask != null) {
        throw GradleException(
            "缺少签名环境变量：${missingSigningEnv.joinToString("、")}。" +
                "release 构建不再回退 debug 签名，请按 README「发布签名」配置后重试。",
        )
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

    testImplementation("junit:junit:4.13.2")
}
