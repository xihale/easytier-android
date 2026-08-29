package com.easytier.android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 按需内置的 Material 图标（官方 path 数据）与应用 logo。
 */
object AppIcons {
    val EasyTierLogo: ImageVector by lazy {
        ImageVector.Builder(
            name = "EasyTierLogo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 1024f,
            viewportHeight = 1024f,
        ).addPath(
            pathData = PathData {
                moveTo(512f, 512f)
                moveToRelative(-100f, 0f)
                arcToRelative(100f, 100f, 0f, isMoreThanHalf = true, isPositiveArc = false, 200f, 0f)
                arcToRelative(100f, 100f, 0f, isMoreThanHalf = true, isPositiveArc = false, -200f, 0f)
            },
            fill = SolidColor(Color(0xFFBB86FC)),
        ).addPath(
            pathData = PathData {
                moveTo(512f, 280f)
                arcTo(232f, 232f, 0f, isMoreThanHalf = false, isPositiveArc = true, 713f, 396f)
            },
            stroke = SolidColor(Color(0xFF03DAC6)),
            strokeLineWidth = 80f,
            strokeLineCap = StrokeCap.Round,
        ).addPath(
            pathData = PathData {
                moveTo(311f, 628f)
                arcTo(232f, 232f, 0f, isMoreThanHalf = false, isPositiveArc = true, 311f, 396f)
            },
            stroke = SolidColor(Color(0xFF03DAC6)),
            strokeLineWidth = 80f,
            strokeLineCap = StrokeCap.Round,
        ).addPath(
            pathData = PathData {
                moveTo(713f, 628f)
                arcTo(232f, 232f, 0f, isMoreThanHalf = false, isPositiveArc = true, 512f, 744f)
            },
            stroke = SolidColor(Color(0xFF03DAC6)),
            strokeLineWidth = 80f,
            strokeLineCap = StrokeCap.Round,
        ).build()
    }


    // Material 图标统一走官方数据：path 原样取自 google/material-design-icons（materialicons 24px），
    // 经 Compose PathParser 解析，避免手抄转写出错。多 path 图标（如 Schedule）按官方顺序逐个 add。
    private fun icon(name: String, vararg pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            pathData.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
        }.build()

    val ArrowDownward: ImageVector by lazy {
        icon(
            "ArrowDownward",
            "M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z",
        )
    }
    
    val ArrowUpward: ImageVector by lazy {
        icon(
            "ArrowUpward",
            "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z",
        )
    }
    
    val CloudOff: ImageVector by lazy {
        icon(
            "CloudOff",
            "M19.35 10.04C18.67 6.59 15.64 4 12 4c-1.48 0-2.85.43-4.01 1.17l1.46 1.46C10.21 6.23 11.08 6 12 6c3.04 0 5.5 2.46 5.5 5.5v.5H19c1.66 0 3 1.34 3 3 0 1.13-.64 2.11-1.56 2.62l1.45 1.45C23.16 18.16 24 16.68 24 15c0-2.64-2.05-4.78-4.65-4.96zM3 5.27l2.75 2.74C2.56 8.15 0 10.77 0 14c0 3.31 2.69 6 6 6h11.73l2 2L21 20.73 4.27 4 3 5.27zM7.73 10l8 8H6c-2.21 0-4-1.79-4-4s1.79-4 4-4h1.73z",
        )
    }
    
    val ContentCopy: ImageVector by lazy {
        icon(
            "ContentCopy",
            "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
        )
    }
    
    val DarkMode: ImageVector by lazy {
        icon(
            "DarkMode",
            "M12,3c-4.97,0-9,4.03-9,9s4.03,9,9,9s9-4.03,9-9c0-0.46-0.04-0.92-0.1-1.36c-0.98,1.37-2.58,2.26-4.4,2.26 c-2.98,0-5.4-2.42-5.4-5.4c0-1.81,0.89-3.42,2.26-4.4C12.92,3.04,12.46,3,12,3L12,3z",
        )
    }
    
    val Description: ImageVector by lazy {
        icon(
            "Description",
            "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z",
        )
    }
    
    val Download: ImageVector by lazy {
        icon(
            "Download",
            "M5,20h14v-2H5V20z M19,9h-4V3H9v6H5l7,7L19,9z",
        )
    }
    
    val ExpandMore: ImageVector by lazy {
        icon(
            "ExpandMore",
            "M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z",
        )
    }
    
    val FolderOpen: ImageVector by lazy {
        icon(
            "FolderOpen",
            "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z",
        )
    }
    
    val Language: ImageVector by lazy {
        icon(
            "Language",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2 0 .68.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2 0-.68.07-1.35.16-2h4.68c.09.65.16 1.32.16 2 0 .68-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2 0-.68-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z",
        )
    }
    
    val Save: ImageVector by lazy {
        icon(
            "Save",
            "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z",
        )
    }
    
    val Schedule: ImageVector by lazy {
        icon(
            "Schedule",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z",
            "M12.5 7H11v6l5.25 3.15.75-1.23-4.5-2.67z",
        )
    }
    
    val Share: ImageVector by lazy {
        icon(
            "Share",
            "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z",
        )
    }
    
    val Shield: ImageVector by lazy {
        icon(
            "Shield",
            "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z",
        )
    }
    
    val Speed: ImageVector by lazy {
        icon(
            "Speed",
            "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z",
        )
    }
    
    val Terminal: ImageVector by lazy {
        icon(
            "Terminal",
            "M20,4H4C2.89,4,2,4.9,2,6v12c0,1.1,0.89,2,2,2h16c1.1,0,2-0.9,2-2V6C22,4.9,21.11,4,20,4z M20,18H4V8h16V18z M18,17h-6v-2 h6V17z M7.5,17l-1.41-1.41L8.67,13l-2.59-2.59L7.5,9l4,4L7.5,17z",
        )
    }
    
    val Upload: ImageVector by lazy {
        icon(
            "Upload",
            "M5,20h14v-2H5V20z M5,10h4v6h6v-6h4l-7-7L5,10z",
        )
    }
    
    val VpnKey: ImageVector by lazy {
        icon(
            "VpnKey",
            "M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z",
        )
    }
    
    val Work: ImageVector by lazy {
        icon(
            "Work",
            "M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z",
        )
    }
    }
