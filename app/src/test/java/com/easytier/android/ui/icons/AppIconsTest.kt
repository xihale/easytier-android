package com.easytier.android.ui.icons

import org.junit.Test
import java.lang.reflect.Modifier

/**
 * 内置 Material 图标 path 数据原样取自 google/material-design-icons 官方仓库。
 * PathParser 在 ImageVector 首次构建（lazy）时解析 path，本测试强制初始化全部图标，
 * 官方数据被误改导致解析失败时在此暴露，而不是用户点开界面时崩溃。
 */
class AppIconsTest {

    @Test
    fun allIconPathsParse() {
        val icons = AppIcons::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Lazy::class.java }
            .map {
                it.isAccessible = true
                it.get(null) as Lazy<*>
            }
        check(icons.size >= 20) { "应至少有 20 个图标（logo + 19 个 Material 图标），实际发现 ${icons.size}" }
        // 触发全部 lazy 初始化 = 执行 PathParser().parsePathString(...)，失败即抛异常
        icons.forEach { it.value }
    }
}
