package com.easytier.android.util

import java.util.concurrent.TimeUnit

/** 展示格式化工具。 */
object Format {

    fun bytes(v: Long): String = when {
        v < 1024 -> "$v B"
        v < 1024 * 1024 -> "%.1f KB".format(v / 1024.0)
        v < 1024 * 1024 * 1024 -> "%.1f MB".format(v / 1024.0 / 1024.0)
        else -> "%.2f GB".format(v / 1024.0 / 1024.0 / 1024.0)
    }

    fun bps(bytesPerSec: Long): String = when {
        bytesPerSec < 1024 -> "$bytesPerSec B/s"
        bytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
        else -> "%.2f MB/s".format(bytesPerSec / 1024.0 / 1024.0)
    }

    fun duration(ms: Long): String {
        if (ms <= 0) return "0s"
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun latency(ms: Long): String = when {
        ms <= 0 -> "--"
        ms < 1000 -> "$ms ms"
        else -> "%.1f s".format(ms / 1000.0)
    }

    /** 简短化 UUID（列表展示用）。 */
    fun shortId(id: String): String =
        if (id.length <= 8) id else id.take(8) + "-" + id.substring(8, 12).lowercase()
}
