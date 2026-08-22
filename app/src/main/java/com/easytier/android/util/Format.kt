package com.easytier.android.util

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
}
