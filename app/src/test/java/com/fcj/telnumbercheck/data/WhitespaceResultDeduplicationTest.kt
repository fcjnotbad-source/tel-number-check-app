package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitespaceResultDeduplicationTest {
    private val number = "4001144618"

    @Test
    fun `screenshot brand is shown once across number service results`() {
        val value = summarize(
            result("未知归属地 京东金融", SearchEngine.BAIDU),
            result("京东金融", SearchEngine.SOGOU),
        )

        assertEquals("未知归属地 京东金融", value)
        assertEquals(1, occurrences(value, "京东金融"))
        assertFalse(value.contains(number))
    }

    @Test
    fun `space newline carriage return tab and Unicode spaces separate repeated groups`() {
        listOf(" ", "  ", "\n", "\r", "\r\n", "\t", "\u00a0", "\u3000").forEach { separator ->
            val value = summarize(result("未知归属地${separator}京东金融${separator}京东金融"))

            assertEquals("separator=${separator.toCharArray().map(Char::code)}", "未知归属地 京东金融", value)
        }
    }

    @Test
    fun `mixed spaces and newlines remove repeated groups within one snippet`() {
        val value = summarize(result("未知归属地  京东金融\r\n服务热线\t京东金融\n服务热线"))

        assertEquals(listOf("未知归属地", "京东金融", "服务热线"), tokens(value))
        assertEquals(1, occurrences(value, "京东金融"))
        assertEquals(1, occurrences(value, "服务热线"))
    }

    @Test
    fun `first occurrence order is retained rather than sorting words`() {
        val value = summarize(result("服务热线 京东金融 未知归属地 京东金融 服务热线"))

        assertEquals(listOf("服务热线", "京东金融", "未知归属地"), tokens(value))
    }

    @Test
    fun `first source order is retained when a later source adds another field`() {
        val value = summarize(
            result("京东金融", SearchEngine.SOGOU),
            result("未知归属地 京东金融", SearchEngine.BAIDU),
        )

        assertEquals(listOf("京东金融", "未知归属地"), tokens(value))
        assertEquals(1, occurrences(value, "京东金融"))
    }

    @Test
    fun `existing flattened summaries also deduplicate newline separated brands`() {
        val value = SearchResultPresentation.summarizeText(
            number,
            "未知归属地 京东金融\r\n京东金融\n京东金融",
        )

        assertEquals("未知归属地 京东金融", value)
    }

    @Test
    fun `existing structured cache is cleaned at display time without rewriting source data`() {
        val entry = CachedSearchResult(
            results = listOf(result("未知归属地 京东金融"), result("京东金融", SearchEngine.SOGOU)),
            savedAtMillis = 123_456L,
        )
        val encoded = SearchResultCacheCodec.encode(entry)
        val restored = SearchResultCacheCodec.decode(encoded)!!

        assertEquals("未知归属地 京东金融", SearchResultPresentation.summarize(number, restored.results))
        assertEquals(entry, restored)
        assertEquals(encoded, SearchResultCacheCodec.encode(restored))
    }

    @Test
    fun `deduplication does not change provider identities links or raw snippets`() {
        val first = result("未知归属地 京东金融", SearchEngine.BAIDU)
        val second = result("京东金融", SearchEngine.SOGOU)
        val values = listOf(first, second)
        val original = values.map { it.copy(sources = it.sources.toSet()) }

        assertEquals("未知归属地 京东金融", SearchResultPresentation.summarize(number, values))
        assertEquals(original, values)
        assertEquals("https://example.invalid/sogou", second.targetUrl)
        assertEquals(setOf(SearchEngine.SOGOU), second.sources)
    }

    @Test
    fun `negative statement stays complete even when its subject appeared earlier`() {
        val value = summarize(result("骚扰电话"), result("不是 骚扰电话"))

        assertTrue(value, value.contains("不是 骚扰电话"))
        assertEquals(2, occurrences(value, "骚扰电话"))
    }

    @Test
    fun `uncertain statement is not reduced to an orphan qualifier`() {
        val value = summarize(result("骚扰电话"), result("疑似 骚扰电话"))

        assertTrue(value, value.contains("疑似 骚扰电话"))
        assertEquals(2, occurrences(value, "骚扰电话"))
    }

    @Test
    fun `denial appearing first cannot erase a later affirmative claim`() {
        val value = summarize(result("不是 骚扰电话"), result("骚扰电话"))

        assertEquals("不是 骚扰电话 · 骚扰电话", value)
        assertEquals(2, occurrences(value, "骚扰电话"))
    }

    @Test
    fun `uncertainty appearing first cannot erase a later affirmative claim`() {
        val value = summarize(result("疑似 骚扰电话"), result("骚扰电话"))

        assertEquals("疑似 骚扰电话 · 骚扰电话", value)
        assertEquals(2, occurrences(value, "骚扰电话"))
    }

    @Test
    fun `counted statement participates in ordinary token deduplication`() {
        val fields = listOf("被 12 人标记 骚扰电话", "骚扰电话")
        assertEquals(listOf(fields.first(), ""), OrderedWhitespaceFacts.clean(fields))
        // The earlier semantic stage can still prefer a richer affirmative counted claim.
        assertEquals(
            "被 12 人标记 骚扰电话",
            summarize(result(fields[0]), result(fields[1])),
        )
    }

    @Test
    fun `different user counts retain each distinct number without repeating shared fields`() {
        val value = summarize(result("被 12 人标记骚扰电话"), result("被 2 人标记骚扰电话"))

        assertEquals(listOf("被", "12", "人标记骚扰电话", "2"), tokens(value))
    }

    @Test
    fun `different geographic blocks retain their own carrier associations`() {
        val value = summarize(result("广东 深圳 中国联通客服"), result("广东 广州 中国移动客服"))

        assertEquals("广东 深圳 · 中国联通客服\n广东 广州 · 中国移动客服", value)
    }

    @Test
    fun `different business scopes retain identical opening hours`() {
        val values = listOf(
            BaiduSearchResult("$number 海风书店", "每天 9 点至 18 点营业", null),
            BaiduSearchResult("$number 山川花店", "每天 9 点至 18 点营业", null),
        )

        assertEquals(
            "海风书店：每天 9 点至 18 点营业\n山川花店：每天 9 点至 18 点营业",
            SearchResultPresentation.summarize(number, values),
        )
    }

    @Test
    fun `empty whitespace groups do not leave empty facts or dangling separators`() {
        val value = summarize(result("  未知归属地 \n\n 京东金融  \t 京东金融  "))

        assertEquals("未知归属地 京东金融", value)
        assertFalse(value.startsWith("·"))
        assertFalse(value.endsWith("·"))
    }

    private fun summarize(vararg results: BaiduSearchResult): String =
        SearchResultPresentation.summarize(number, results.toList())

    private fun result(snippet: String, source: SearchEngine = SearchEngine.BAIDU) = BaiduSearchResult(
        title = "${number}号码查询",
        snippet = snippet,
        targetUrl = "https://example.invalid/${source.id}",
        sources = setOf(source),
        isNumberService = true,
    )

    private fun tokens(value: String): List<String> = value.split(Regex("[\\s\\p{Z}·]+"))
        .filter(String::isNotBlank)

    private fun occurrences(value: String, token: String): Int = Regex(Regex.escape(token)).findAll(value).count()
}
