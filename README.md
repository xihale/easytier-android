# EasyTier Android (Native)

EasyTier 的原生 Android 客户端。纯 Kotlin + Jetpack Compose 实现 UI 与设备管理，通过 JNI 调用预编译的
EasyTier 核心引擎（`libeasytier_android_jni.so`），不依赖 Tauri / WebView。

## 功能

- 多网络配置管理：创建、编辑、启停，支持 TOML 导入 / 导出
- 系统级 VPN（TUN）接入，可与 SOCKS5 代理模式独立开关
- 对等节点状态、速率与流量实时展示
- 开机自启、深浅色主题跟随系统

## 构建

要求 JDK 17+ 与 Android SDK（API 36）。

```bash
./gradlew :app:assembleDebug    # 调试包
./gradlew :app:assembleRelease  # 发布包（默认 debug 签名，见下）
./gradlew :app:testDebugUnitTest
```

仅包含 `arm64-v8a`。

## 发布签名

CI 在 tag（`v*`）推送时自动构建并发布 Release APK。签名通过仓库 Secrets 配置：

| Secret | 说明 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | keystore 文件的 Base64 |
| `SIGNING_KEYSTORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | key 别名 |
| `SIGNING_KEY_PASSWORD` | key 密码 |

未配置 Secrets 时回退为 debug 签名。

## 致谢

- [EasyTier](https://github.com/EasyTier/EasyTier)：去中心化组网核心，遵循 LGPL-3.0。
  本仓库 `jniLibs/` 内的 `libeasytier_android_jni.so` 为预编译产物（JNI 封装源码不在本仓库内）。
