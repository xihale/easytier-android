package com.easytier.android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * 按需内置的 Material 图标（提取自 material-icons-extended，避免引入整个图标库）。
 * 仅包含应用实际用到的图标，APK 体积远小于依赖 extended 库。
 */
object AppIcons {

    private fun icon(name: String, builder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathData(builder),
            fill = SolidColor(Color.Black),
        ).build()


    val VpnKey: ImageVector by lazy {
        icon("VpnKey") {
            moveTo(12.65f, 10.0f)
            curveTo(11.83f, 7.67f, 9.61f, 6.0f, 7.0f, 6.0f)
            curveToRelative(-3.31f, 0.0f, -6.0f, 2.69f, -6.0f, 6.0f)
            reflectiveCurveToRelative(2.69f, 6.0f, 6.0f, 6.0f)
            curveToRelative(2.61f, 0.0f, 4.83f, -1.67f, 5.65f, -4.0f)
            horizontalLineTo(17.0f)
            verticalLineToRelative(4.0f)
            horizontalLineToRelative(4.0f)
            verticalLineToRelative(-4.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(-4.0f)
            horizontalLineTo(12.65f)
            close()
            moveTo(7.0f, 14.0f)
            curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
            reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
            reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
            reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
            close()
        }
    }

    val ArrowUpward: ImageVector by lazy {
        icon("ArrowUpward") {
            moveTo(4.0f, 12.0f)
            lineToRelative(1.41f, 1.41f)
            lineTo(11.0f, 7.83f)
            verticalLineTo(20.0f)
            horizontalLineToRelative(2.0f)
            verticalLineTo(7.83f)
            lineToRelative(5.58f, 5.59f)
            lineTo(20.0f, 12.0f)
            lineToRelative(-8.0f, -8.0f)
            lineToRelative(-8.0f, 8.0f)
            close()
        }
    }

    val ArrowDownward: ImageVector by lazy {
        icon("ArrowDownward") {
            moveTo(20.0f, 12.0f)
            lineToRelative(-1.41f, -1.41f)
            lineTo(13.0f, 16.17f)
            verticalLineTo(4.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(12.17f)
            lineToRelative(-5.58f, -5.59f)
            lineTo(4.0f, 12.0f)
            lineToRelative(8.0f, 8.0f)
            lineToRelative(8.0f, -8.0f)
            close()
        }
    }

    val Terminal: ImageVector by lazy {
        icon("Terminal") {
            moveTo(20.0f, 4.0f)
            horizontalLineTo(4.0f)
            curveTo(2.89f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
            verticalLineToRelative(12.0f)
            curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(16.0f)
            curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
            verticalLineTo(6.0f)
            curveTo(22.0f, 4.9f, 21.11f, 4.0f, 20.0f, 4.0f)
            close()
            moveTo(20.0f, 18.0f)
            horizontalLineTo(4.0f)
            verticalLineTo(8.0f)
            horizontalLineToRelative(16.0f)
            verticalLineTo(18.0f)
            close()
            moveTo(18.0f, 17.0f)
            horizontalLineToRelative(-6.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(6.0f)
            verticalLineTo(17.0f)
            close()
            moveTo(7.5f, 17.0f)
            lineToRelative(-1.41f, -1.41f)
            lineTo(8.67f, 13.0f)
            lineToRelative(-2.59f, -2.59f)
            lineTo(7.5f, 9.0f)
            lineToRelative(4.0f, 4.0f)
            lineTo(7.5f, 17.0f)
            close()
        }
    }

    val Shield: ImageVector by lazy {
        icon("Shield") {
            moveTo(12.0f, 1.0f)
            lineTo(3.0f, 5.0f)
            verticalLineToRelative(6.0f)
            curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
            curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
            verticalLineTo(5.0f)
            lineToRelative(-9.0f, -4.0f)
            close()
        }
    }

    val Save: ImageVector by lazy {
        icon("Save") {
            moveTo(17.0f, 3.0f)
            lineTo(5.0f, 3.0f)
            curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
            verticalLineToRelative(14.0f)
            curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(14.0f)
            curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
            lineTo(21.0f, 7.0f)
            lineToRelative(-4.0f, -4.0f)
            close()
            moveTo(12.0f, 19.0f)
            curveToRelative(-1.66f, 0.0f, -3.0f, -1.34f, -3.0f, -3.0f)
            reflectiveCurveToRelative(1.34f, -3.0f, 3.0f, -3.0f)
            reflectiveCurveToRelative(3.0f, 1.34f, 3.0f, 3.0f)
            reflectiveCurveToRelative(-1.34f, 3.0f, -3.0f, 3.0f)
            close()
            moveTo(15.0f, 9.0f)
            lineTo(5.0f, 9.0f)
            lineTo(5.0f, 5.0f)
            horizontalLineToRelative(10.0f)
            verticalLineToRelative(4.0f)
            close()
        }
    }

    val Work: ImageVector by lazy {
        icon("Work") {
            moveTo(20.0f, 6.0f)
            horizontalLineToRelative(-4.0f)
            lineTo(16.0f, 4.0f)
            curveToRelative(0.0f, -1.11f, -0.89f, -2.0f, -2.0f, -2.0f)
            horizontalLineToRelative(-4.0f)
            curveToRelative(-1.11f, 0.0f, -2.0f, 0.89f, -2.0f, 2.0f)
            verticalLineToRelative(2.0f)
            lineTo(4.0f, 6.0f)
            curveToRelative(-1.11f, 0.0f, -1.99f, 0.89f, -1.99f, 2.0f)
            lineTo(2.0f, 19.0f)
            curveToRelative(0.0f, 1.11f, 0.89f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(16.0f)
            curveToRelative(1.11f, 0.0f, 2.0f, -0.89f, 2.0f, -2.0f)
            lineTo(22.0f, 8.0f)
            curveToRelative(0.0f, -1.11f, -0.89f, -2.0f, -2.0f, -2.0f)
            close()
            moveTo(14.0f, 6.0f)
            horizontalLineToRelative(-4.0f)
            lineTo(10.0f, 4.0f)
            horizontalLineToRelative(4.0f)
            verticalLineToRelative(2.0f)
            close()
        }
    }

    val Upload: ImageVector by lazy {
        icon("Upload") {
            moveTo(5.0f, 20.0f)
            horizontalLineToRelative(14.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineTo(5.0f)
            verticalLineTo(20.0f)
            close()
            moveTo(5.0f, 10.0f)
            horizontalLineToRelative(4.0f)
            verticalLineToRelative(6.0f)
            horizontalLineToRelative(6.0f)
            verticalLineToRelative(-6.0f)
            horizontalLineToRelative(4.0f)
            lineToRelative(-7.0f, -7.0f)
            lineTo(5.0f, 10.0f)
            close()
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            moveTo(19.0f, 9.0f)
            horizontalLineToRelative(-4.0f)
            verticalLineTo(3.0f)
            horizontalLineTo(9.0f)
            verticalLineToRelative(6.0f)
            horizontalLineTo(5.0f)
            lineToRelative(7.0f, 7.0f)
            lineToRelative(7.0f, -7.0f)
            close()
            moveTo(5.0f, 18.0f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(14.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineTo(5.0f)
            close()
        }
    }

    val Share: ImageVector by lazy {
        icon("Share") {
            moveTo(18.0f, 16.08f)
            curveToRelative(-0.76f, 0.0f, -1.44f, 0.3f, -1.96f, 0.77f)
            lineTo(8.91f, 12.7f)
            curveToRelative(0.05f, -0.23f, 0.09f, -0.46f, 0.09f, -0.7f)
            reflectiveCurveToRelative(-0.04f, -0.47f, -0.09f, -0.7f)
            lineToRelative(7.05f, -4.11f)
            curveToRelative(0.54f, 0.5f, 1.25f, 0.81f, 2.04f, 0.81f)
            curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
            reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
            reflectiveCurveToRelative(-3.0f, 1.34f, -3.0f, 3.0f)
            curveToRelative(0.0f, 0.24f, 0.04f, 0.47f, 0.09f, 0.7f)
            lineTo(8.04f, 9.81f)
            curveTo(7.5f, 9.31f, 6.79f, 9.0f, 6.0f, 9.0f)
            curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
            reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
            curveToRelative(0.79f, 0.0f, 1.5f, -0.31f, 2.04f, -0.81f)
            lineToRelative(7.12f, 4.16f)
            curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
            curveToRelative(0.0f, 1.61f, 1.31f, 2.92f, 2.92f, 2.92f)
            reflectiveCurveToRelative(2.92f, -1.31f, 2.92f, -2.92f)
            reflectiveCurveToRelative(-1.31f, -2.92f, -2.92f, -2.92f)
            close()
        }
    }

    val ContentCopy: ImageVector by lazy {
        icon("ContentCopy") {
            moveTo(16.0f, 1.0f)
            horizontalLineTo(4.0f)
            curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
            verticalLineToRelative(14.0f)
            horizontalLineToRelative(2.0f)
            verticalLineTo(3.0f)
            horizontalLineToRelative(12.0f)
            verticalLineTo(1.0f)
            close()
            moveTo(19.0f, 5.0f)
            horizontalLineTo(8.0f)
            curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
            verticalLineToRelative(14.0f)
            curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(11.0f)
            curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
            verticalLineTo(7.0f)
            curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
            close()
            moveTo(19.0f, 21.0f)
            horizontalLineTo(8.0f)
            verticalLineTo(7.0f)
            horizontalLineToRelative(11.0f)
            verticalLineToRelative(14.0f)
            close()
        }
    }

    val FolderOpen: ImageVector by lazy {
        icon("FolderOpen") {
            moveTo(20.0f, 6.0f)
            horizontalLineToRelative(-8.0f)
            lineToRelative(-2.0f, -2.0f)
            horizontalLineTo(4.0f)
            curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
            lineTo(2.0f, 18.0f)
            curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(16.0f)
            curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
            verticalLineTo(8.0f)
            curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
            close()
            moveTo(20.0f, 18.0f)
            horizontalLineTo(4.0f)
            verticalLineTo(8.0f)
            horizontalLineToRelative(16.0f)
            verticalLineToRelative(10.0f)
            close()
        }
    }

    val Speed: ImageVector by lazy {
        icon("Speed") {
            moveTo(20.38f, 8.57f)
            lineToRelative(-1.23f, 1.85f)
            arcToRelative(8.0f, 8.0f, 0.0f, false, true, -0.22f, 7.58f)
            lineTo(5.07f, 18.0f)
            arcTo(8.0f, 8.0f, 0.0f, false, true, 15.58f, 6.85f)
            lineToRelative(1.85f, -1.23f)
            arcTo(10.0f, 10.0f, 0.0f, false, false, 3.35f, 19.0f)
            arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.72f, 1.0f)
            horizontalLineToRelative(13.85f)
            arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.74f, -1.0f)
            arcToRelative(10.0f, 10.0f, 0.0f, false, false, -0.27f, -10.44f)
            close()
            moveTo(10.59f, 15.41f)
            arcToRelative(2.0f, 2.0f, 0.0f, false, false, 2.83f, 0.0f)
            lineToRelative(5.66f, -8.49f)
            lineToRelative(-8.49f, 5.66f)
            arcToRelative(2.0f, 2.0f, 0.0f, false, false, 0.0f, 2.83f)
            close()
        }
    }

    val Language: ImageVector by lazy {
        icon("Language") {
            moveTo(11.99f, 2.0f)
            curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
            reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
            curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
            reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
            close()
            moveTo(18.92f, 8.0f)
            horizontalLineToRelative(-2.95f)
            curveToRelative(-0.32f, -1.25f, -0.78f, -2.45f, -1.38f, -3.56f)
            curveToRelative(1.84f, 0.63f, 3.37f, 1.91f, 4.33f, 3.56f)
            close()
            moveTo(12.0f, 4.04f)
            curveToRelative(0.83f, 1.2f, 1.48f, 2.53f, 1.91f, 3.96f)
            horizontalLineToRelative(-3.82f)
            curveToRelative(0.43f, -1.43f, 1.08f, -2.76f, 1.91f, -3.96f)
            close()
            moveTo(4.26f, 14.0f)
            curveTo(4.1f, 13.36f, 4.0f, 12.69f, 4.0f, 12.0f)
            reflectiveCurveToRelative(0.1f, -1.36f, 0.26f, -2.0f)
            horizontalLineToRelative(3.38f)
            curveToRelative(-0.08f, 0.66f, -0.14f, 1.32f, -0.14f, 2.0f)
            curveToRelative(0.0f, 0.68f, 0.06f, 1.34f, 0.14f, 2.0f)
            lineTo(4.26f, 14.0f)
            close()
            moveTo(5.08f, 16.0f)
            horizontalLineToRelative(2.95f)
            curveToRelative(0.32f, 1.25f, 0.78f, 2.45f, 1.38f, 3.56f)
            curveToRelative(-1.84f, -0.63f, -3.37f, -1.9f, -4.33f, -3.56f)
            close()
            moveTo(8.03f, 8.0f)
            lineTo(5.08f, 8.0f)
            curveToRelative(0.96f, -1.66f, 2.49f, -2.93f, 4.33f, -3.56f)
            curveTo(8.81f, 5.55f, 8.35f, 6.75f, 8.03f, 8.0f)
            close()
            moveTo(12.0f, 19.96f)
            curveToRelative(-0.83f, -1.2f, -1.48f, -2.53f, -1.91f, -3.96f)
            horizontalLineToRelative(3.82f)
            curveToRelative(-0.43f, 1.43f, -1.08f, 2.76f, -1.91f, 3.96f)
            close()
            moveTo(14.34f, 14.0f)
            lineTo(9.66f, 14.0f)
            curveToRelative(-0.09f, -0.66f, -0.16f, -1.32f, -0.16f, -2.0f)
            curveToRelative(0.0f, -0.68f, 0.07f, -1.35f, 0.16f, -2.0f)
            horizontalLineToRelative(4.68f)
            curveToRelative(0.09f, 0.65f, 0.16f, 1.32f, 0.16f, 2.0f)
            curveToRelative(0.0f, 0.68f, -0.07f, 1.34f, -0.16f, 2.0f)
            close()
            moveTo(14.59f, 19.56f)
            curveToRelative(0.6f, -1.11f, 1.06f, -2.31f, 1.38f, -3.56f)
            horizontalLineToRelative(2.95f)
            curveToRelative(-0.96f, 1.65f, -2.49f, 2.93f, -4.33f, 3.56f)
            close()
            moveTo(16.36f, 14.0f)
            curveToRelative(0.08f, -0.66f, 0.14f, -1.32f, 0.14f, -2.0f)
            curveToRelative(0.0f, -0.68f, -0.06f, -1.34f, -0.14f, -2.0f)
            horizontalLineToRelative(3.38f)
            curveToRelative(0.16f, 0.64f, 0.26f, 1.31f, 0.26f, 2.0f)
            reflectiveCurveToRelative(-0.1f, 1.36f, -0.26f, 2.0f)
            horizontalLineToRelative(-3.38f)
            close()
        }
    }

    val ExpandMore: ImageVector by lazy {
        icon("ExpandMore") {
            moveTo(16.59f, 8.59f)
            lineTo(12.0f, 13.17f)
            lineTo(7.41f, 8.59f)
            lineTo(6.0f, 10.0f)
            lineToRelative(6.0f, 6.0f)
            lineToRelative(6.0f, -6.0f)
            close()
        }
    }

    val DarkMode: ImageVector by lazy {
        icon("DarkMode") {
            moveTo(12.0f, 3.0f)
            curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
            reflectiveCurveToRelative(4.03f, 9.0f, 9.0f, 9.0f)
            reflectiveCurveToRelative(9.0f, -4.03f, 9.0f, -9.0f)
            curveToRelative(0.0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
            curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
            curveToRelative(-2.98f, 0.0f, -5.4f, -2.42f, -5.4f, -5.4f)
            curveToRelative(0.0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
            curveTo(12.92f, 3.04f, 12.46f, 3.0f, 12.0f, 3.0f)
            lineTo(12.0f, 3.0f)
            close()
        }
    }

    val CloudOff: ImageVector by lazy {
        icon("CloudOff") {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
            curveToRelative(-1.48f, 0.0f, -2.85f, 0.43f, -4.01f, 1.17f)
            lineToRelative(1.46f, 1.46f)
            curveTo(10.21f, 6.23f, 11.08f, 6.0f, 12.0f, 6.0f)
            curveToRelative(3.04f, 0.0f, 5.5f, 2.46f, 5.5f, 5.5f)
            verticalLineToRelative(0.5f)
            horizontalLineTo(19.0f)
            curveToRelative(1.66f, 0.0f, 3.0f, 1.34f, 3.0f, 3.0f)
            curveToRelative(0.0f, 1.13f, -0.64f, 2.11f, -1.56f, 2.62f)
            lineToRelative(1.45f, 1.45f)
            curveTo(23.16f, 18.16f, 24.0f, 16.68f, 24.0f, 15.0f)
            curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
            close()
            moveTo(3.0f, 5.27f)
            lineToRelative(2.75f, 2.74f)
            curveTo(2.56f, 8.15f, 0.0f, 10.77f, 0.0f, 14.0f)
            curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
            horizontalLineToRelative(11.73f)
            lineToRelative(2.0f, 2.0f)
            lineTo(21.0f, 20.73f)
            lineTo(4.27f, 4.0f)
            lineTo(3.0f, 5.27f)
            close()
            moveTo(7.73f, 10.0f)
            lineToRelative(8.0f, 8.0f)
            horizontalLineTo(6.0f)
            curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
            reflectiveCurveToRelative(1.79f, -4.0f, 4.0f, -4.0f)
            horizontalLineToRelative(1.73f)
            close()
        }
    }

    val Description: ImageVector by lazy {
        icon("Description") {
            moveTo(14.0f, 2.0f)
            horizontalLineTo(6.0f)
            curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
            lineTo(4.0f, 20.0f)
            curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
            horizontalLineTo(18.0f)
            curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
            verticalLineTo(8.0f)
            lineTo(14.0f, 2.0f)
            close()
            moveTo(16.0f, 18.0f)
            horizontalLineTo(8.0f)
            verticalLineTo(16.0f)
            horizontalLineTo(16.0f)
            verticalLineTo(18.0f)
            close()
            moveTo(16.0f, 14.0f)
            horizontalLineTo(8.0f)
            verticalLineTo(12.0f)
            horizontalLineTo(16.0f)
            verticalLineTo(14.0f)
            close()
            moveTo(13.0f, 9.0f)
            verticalLineTo(3.5f)
            lineTo(18.5f, 9.0f)
            horizontalLineTo(13.0f)
            close()
        }
    }

}
