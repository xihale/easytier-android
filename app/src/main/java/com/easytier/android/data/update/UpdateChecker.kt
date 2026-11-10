package com.easytier.android.data.update

import com.easytier.android.BuildConfig
import com.easytier.android.data.store.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 从 GitHub Releases 检查最新版本。
 *
 * - 零额外依赖：复用已有的 kotlinx-serialization + java.net.HttpURLConnection。
 * - 自动检查在应用冷启动时静默运行，失败不上报。
 * - 更新源为本仓库的 GitHub 发布页：xihale/easytier-android。
 */
class UpdateChecker(
    private val settingsRepository: com.easytier.android.data.store.SettingsRepository,
) {

    /** 应用冷启动时调用：根据频率决定是否执行一次安静检查。 */
    suspend fun maybeAutoCheck() = withContext(Dispatchers.IO) {
        val s: AppSettings = settingsRepository.settings.first()

        // 覆盖安装不会清 DataStore，升级后旧的「待查看新版本」标记还在，会一直提示有新版本。
        // 这一步不依赖自动检查频率，且必须在下面的提前 return 之前做。
        if (s.pendingUpdateVersion.isNotBlank() && !isNewerVersion(s.pendingUpdateVersion, currentVersion())) {
            settingsRepository.clearPendingUpdate()
        }

        val interval = s.updateCheckInterval
        if (interval == Interval.OFF) return@withContext

        if (interval != Interval.STARTUP) {
            val periodMs = when (interval) {
                Interval.DAILY -> TimeUnit.DAYS.toMillis(1)
                Interval.WEEKLY -> TimeUnit.DAYS.toMillis(7)
                else -> return@withContext
            }
            // 距上次检查未满周期，跳过
            if (System.currentTimeMillis() - s.lastUpdateCheckAt < periodMs) return@withContext
        }

        val result = checkNow()
        val now = System.currentTimeMillis()
        settingsRepository.setLastUpdateCheckAt(now)
        when (result) {
            is UpdateCheckResult.Newer -> settingsRepository.setPendingUpdate(result.info)
            is UpdateCheckResult.UpToDate -> settingsRepository.clearPendingUpdate()
            UpdateCheckResult.Error, UpdateCheckResult.Checking, UpdateCheckResult.Idle -> Unit
        }
    }

    /** 手动点击「检查更新」时调用。 */
    suspend fun checkNow(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val response = fetchLatestReleaseJson()
            json.decodeFromString<GithubRelease>(response)
                .takeIf { it.tagName.isNotBlank() }
                ?.let { release ->
                    val latest = release.tagName.removePrefix("v").removePrefix("V")
                    if (isNewerVersion(latest, currentVersion())) {
                        UpdateCheckResult.Newer(
                            ReleaseInfo(version = latest, url = release.htmlUrl, notes = release.body)
                        )
                    } else {
                        UpdateCheckResult.UpToDate
                    }
                } ?: UpdateCheckResult.Error
        } catch (e: IOException) {
            UpdateCheckResult.Error
        }
    }

    /** 当前安装包的版本号，去掉可能的 v 前缀（正式包是干净 semver，本地构建带 describe 尾巴）。 */
    private fun currentVersion(): String =
        BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V")

    private fun fetchLatestReleaseJson(): String =
        (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).let { conn ->
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "easytier-android")
            conn.inputStream.use { it.readBytes().decodeToString() }
        }

    /** 自动检查频率选项键值。 */
    object Interval {
        const val OFF = "off"
        const val STARTUP = "startup"
        const val DAILY = "daily"
        const val WEEKLY = "weekly"
    }

    private companion object {
        const val REPO = "xihale/easytier-android"
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

/**
 * 比较两个 "x.y.z" 版本号，逐段比数字；任一侧某段取不出数字时视为不大于。
 *
 * 本地构建的 versionName 可能是 git describe 产物（如 "0.1.5-3-gcdf5651"），
 * 每段只取前导数字，所以它等价于 "0.1.5"——这类构建不会被判成比同 tag 的正式版更新。
 */
internal fun isNewerVersion(latest: String, current: String): Boolean {
    if (latest == current) return false
    val l = latest.split(".")
    val c = current.split(".")
    val n = maxOf(l.size, c.size)
    for (i in 0 until n) {
        val lv = l.getOrElse(i) { "0" }.takeWhile { it.isDigit() }.toIntOrNull()
            ?: return false
        val cv = c.getOrElse(i) { "0" }.takeWhile { it.isDigit() }.toIntOrNull()
            ?: return false
        if (lv != cv) return lv > cv
    }
    return false
}

/** 本仓库发布的某个 GitHub Release 条目（仅需的字段）。 */
@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
)

/** 一次版本检查的结果。 */
data class ReleaseInfo(
    val version: String,   // 去掉 v 前缀，如 "0.1.1"
    val url: String,       // 发布页
    val notes: String?,    // 发布说明
)

sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult
    data object Checking : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Newer(val info: ReleaseInfo) : UpdateCheckResult
    data object Error : UpdateCheckResult
}
