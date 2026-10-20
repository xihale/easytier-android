package com.easytier.android.ui.components

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TomlHighlightTest {

    private fun coloredRanges(s: String, color: Color) =
        highlightToml(s).spanStyles.filter { it.item.color == color }
            .map { s.substring(it.start, it.end) }

    @Test
    fun `string table bool number comment`() {
        val toml = """
            # 注释 comment
            [network_identity]
            network_name = "mesh"
            port = 8080
            latency_first = true
        """.trimIndent()

        val emerald = Color(0xFF80D9C7)
        val indigo = Color(0xFFB7C3FF)
        val amber = Color(0xFFFBBF24)
        val muted = Color(0xFF8D92A8)

        assertEquals(listOf("# 注释 comment"), coloredRanges(toml, muted))
        // 靛蓝 = 表头 + 布尔（同色，靠字重区分）
        assertEquals(listOf("[network_identity]", "true"), coloredRanges(toml, indigo))
        assertEquals(listOf("\"mesh\""), coloredRanges(toml, emerald))
        assertEquals(listOf("8080"), coloredRanges(toml, amber))

        // 布尔是靛蓝（与表头同色），断言文本内容
        val bool = highlightToml(toml).spanStyles
            .filter { it.item.fontWeight == FontWeight.Medium }
            .map { toml.substring(it.start, it.end) }
        assertEquals(listOf("true"), bool)
    }

    @Test
    fun `hash inside string is not comment`() {
        val s = "name = \"a#b\" # tail"
        val emerald = Color(0xFF80D9C7)
        val muted = Color(0xFF8D92A8)
        assertEquals(listOf("\"a#b\""), coloredRanges(s, emerald))
        assertEquals(listOf("# tail"), coloredRanges(s, muted))
    }

    @Test
    fun `comment span is italic and unterminated string ends at eol`() {
        // 注释斜体
        val comment = highlightToml("# hi").spanStyles.single()
        assertEquals(FontStyle.Italic, comment.item.fontStyle)

        // 未闭合的单行字符串：高亮止于行尾
        val s = "key = \"open"
        val str = highlightToml(s).spanStyles.filter { it.item.color == Color(0xFF80D9C7) }
        assertEquals(1, str.size)
        assertEquals("\"open", s.substring(str[0].start, str[0].end))
    }

    @Test
    fun `escape keeps string open`() {
        val s = "a = \"x\\\"y\" z"
        val emerald = Color(0xFF80D9C7)
        assertEquals(listOf("\"x\\\"y\""), coloredRanges(s, emerald))
    }

    @Test
    fun `mid-line bracket is not a table header`() {
        val s = "listeners = [1]"
        // 无表头靛蓝 SemiBold：唯一靛蓝 span 若存在，不应是 "[1]"
        val indigo = coloredRanges(s, Color(0xFFB7C3FF))
        assertEquals(emptyList<String>(), indigo.filter { it.startsWith("[") })
    }
}
