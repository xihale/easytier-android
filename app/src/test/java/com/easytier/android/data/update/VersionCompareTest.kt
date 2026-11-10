package com.easytier.android.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `更高版本被判为新版本`() {
        assertTrue(isNewerVersion("0.2.0", "0.1.5"))
        assertTrue(isNewerVersion("0.1.6", "0.1.5"))
        assertTrue(isNewerVersion("1.0.0", "0.9.9"))
    }

    @Test
    fun `相同或更低版本不提示`() {
        assertFalse(isNewerVersion("0.2.0", "0.2.0"))
        assertFalse(isNewerVersion("0.1.5", "0.2.0"))
    }

    @Test
    fun `本地 describe 版本号按所属 tag 比较`() {
        // git describe 产物（tag 后有提交 / 工作区有改动）不等于 tag 本身，也不该更高
        assertFalse(isNewerVersion("0.2.0", "0.2.0-3-gabcdef"))
        assertFalse(isNewerVersion("0.2.0", "0.2.0-dirty"))
        assertTrue(isNewerVersion("0.2.0", "0.1.5-3-gabcdef"))
        assertFalse(isNewerVersion("0.1.5", "0.1.5-3-gabcdef"))
    }

    @Test
    fun `非数字版本段视为不大于`() {
        assertFalse(isNewerVersion("abc", "0.1.5"))
    }
}
