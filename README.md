# EasyTier Android (Native)

EasyTier 的原生 Android 客户端。纯 Kotlin + Jetpack Compose 实现 UI 与设备管理，通过 JNI 调用预编译的
EasyTier 核心引擎（`libeasytier_android_jni.so`），不依赖 Tauri / WebView。

## 功能

- 多网络配置管理：创建、编辑、启停，支持 TOML 导入 / 导出
- 系统级 VPN（TUN）接入，可与 SOCKS5 代理模式独立开关
- 对等节点状态、速率与流量实时展示
- 开机自启、深浅色主题跟随系统

## 构建

要求 JDK 17+ 与 Android SDK（API 36）。仓库不含 `libeasytier_android_jni.so`：

- **CI / Release workflow**：自动从本仓库最新的 `jni-*` Release 下载预编译 `.so`，无需本地准备
- **本地构建**：先按下面「JNI 库版本管理」上传一次，或手动把 `.so` 放到
  `app/src/main/jniLibs/arm64-v8a/`（该目录不入库）

```bash
./gradlew :app:assembleDebug    # 调试包
./gradlew :app:assembleRelease  # 发布包（默认 debug 签名，见下）
./gradlew :app:testDebugUnitTest
```

仅包含 `arm64-v8a`。

## JNI 库版本管理

`.so` 在本地从 [EasyTier/EasyTier](https://github.com/EasyTier/EasyTier) 的
`easytier-contrib/easytier-android-jni` 构建后上传到本仓库的 Release（tag 以 `jni-` 开头，
不会触发 APK 发布），CI 取其中最新的一份：

```bash
# 1. 构建（在上游 EasyTier 克隆内）
cd easytier-contrib/easytier-android-jni && ../build.sh

# 2. 按约定命名资产并上传（版本号建议标注上游核心版本或提交）
SO=target/android/arm64-v8a/libeasytier_android_jni.so
cp "$SO" /tmp/libeasytier_android_jni-arm64-v8a.so
gh release create jni-<版本> --repo xihale/easytier-android-native \
  --title "JNI <版本>" --notes "EasyTier 核心 <版本/commit>，arm64-v8a"
gh release upload jni-<版本> /tmp/libeasytier_android_jni-arm64-v8a.so \
  --repo xihale/easytier-android-native --clobber
```

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
  本应用通过其 `easytier-contrib/easytier-android-jni` 提供的 JNI 绑定调用引擎
  （预编译 `.so` 经本仓库 `jni-*` Release 分发）。
