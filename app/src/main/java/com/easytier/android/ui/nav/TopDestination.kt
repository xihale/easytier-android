package com.easytier.android.ui.nav

import androidx.annotation.StringRes
import com.easytier.android.R

/** 底部导航目的地。labelRes 为导航栏标题资源，UI 侧用 stringResource 解析。 */
enum class TopDestination(@StringRes val labelRes: Int) {
    Networks(R.string.nav_networks),
    Status(R.string.nav_status),
    Settings(R.string.nav_settings),
}
