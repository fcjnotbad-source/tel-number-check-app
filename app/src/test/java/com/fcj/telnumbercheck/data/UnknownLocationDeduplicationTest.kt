package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnknownLocationDeduplicationTest {
    private val number = "4008205206"
    private val unknownLocation = "未知归属地"
    private val countedClaim = "营销推广电话 被22798人标记"

    @Test
    fun `screenshot placeholder appears once while brand category and count remain`() {
        val value = SearchResultPresentation.summarize(number, screenshotResults())

        assertScreenshotFacts(value)
        assertFalse(value.contains(number))
    }

    @Test
    fun `counted source appearing first still deduplicates the placeholder`() {
        val results = screenshotResults()
        listOf(results.reversed(), results.drop(1) + results.first()).forEach { ordered ->
            assertScreenshotFacts(SearchResultPresentation.summarize(number, ordered))
        }
    }

    @Test
    fun `legacy flattened diagnostics deduplicate the screenshot placeholder`() {
        val lines = listOf("未知归属地 唯品会", "广告推销", "$countedClaim 未知归属地")
        listOf(lines, lines.reversed()).forEach { ordered ->
            assertScreenshotFacts(SearchResultPresentation.summarizeText(number, ordered.joinToString("\r\n")))
        }
    }

    @Test
    fun `spaces and line breaks separate duplicate placeholders in a counted result`() {
        listOf(" ", "  ", "\n", "\r", "\r\n", "\t", "\u00a0", "\u3000").forEach { separator ->
            val value = SearchResultPresentation.summarize(
                number,
                listOf(result("未知归属地${separator}唯品会${separator}$countedClaim${separator}未知归属地")),
            )

            assertEquals("separator=${separator.toCharArray().map(Char::code)}", 1, occurrences(value, unknownLocation))
            assertTrue(value, value.contains("唯品会"))
            assertTrue(value, value.contains(countedClaim))
        }
    }

    @Test
    fun `counted claims retain distinct user counts and deduplicate their shared category`() {
        val value = SearchResultPresentation.summarize(
            number,
            listOf(
                result("营销推广电话 被22798人标记 未知归属地"),
                result("营销推广电话 被12人标记 未知归属地", SearchEngine.SOGOU),
            ),
        )

        assertEquals(1, occurrences(value, unknownLocation))
        assertEquals(1, occurrences(value, "营销推广电话"))
        assertTrue(value, value.contains("被22798人标记"))
        assertTrue(value, value.contains("被12人标记"))
    }

    @Test
    fun `real denial and uncertainty claims remain complete`() {
        val value = SearchResultPresentation.summarize(
            number,
            listOf(
                result("未知归属地 唯品会"),
                result("不是 骚扰电话 未知归属地", SearchEngine.QIHOO),
                result("疑似 骚扰电话 未知归属地", SearchEngine.SOGOU),
            ),
        )

        assertEquals(3, occurrences(value, unknownLocation))
        assertTrue(value, value.contains("不是 骚扰电话"))
        assertTrue(value, value.contains("疑似 骚扰电话"))
    }

    @Test
    fun `a negated unknown location statement cannot become an orphan qualifier`() {
        val value = SearchResultPresentation.summarize(
            number,
            listOf(result("未知归属地 唯品会"), result("不是 未知归属地", SearchEngine.SOGOU)),
        )

        assertTrue(value, value.contains("不是 未知归属地"))
    }

    @Test
    fun `split counted fields participate in ordinary whitespace deduplication`() {
        assertEquals(
            listOf("未知归属地 唯品会", "广告推销", "营销推广电话 被 22798 人标记"),
            OrderedWhitespaceFacts.clean(
                listOf("未知归属地 唯品会", "广告推销", "营销推广电话 被 22798 人标记 未知归属地"),
            ),
        )
    }

    @Test
    fun `placeholder inside a business name is not removed as a substring`() {
        val value = SearchResultPresentation.summarize(
            number,
            listOf(result("未知归属地 唯品会"), result("未知归属地信息咨询公司", SearchEngine.SOGOU)),
        )

        assertTrue(value, value.contains("未知归属地信息咨询公司"))
        assertEquals(1, occurrences(value, "唯品会"))
    }

    @Test
    fun `embedded location uncertainty remains complete instead of being partially erased`() {
        val value = SearchResultPresentation.summarize(
            number,
            listOf(result("未知归属地 唯品会"), result("仍属未知归属地范围", SearchEngine.SOGOU)),
        )

        assertTrue(value, value.contains("仍属未知归属地范围"))
    }

    @Test
    fun `separate business contexts retain their own location statement`() {
        val value = SearchResultPresentation.summarize(
            number,
            listOf(
                BaiduSearchResult("海风书店", "未知归属地 服务热线", null, isNumberService = true),
                BaiduSearchResult("山川花店", "未知归属地 服务热线", null, isNumberService = true),
            ),
        )

        assertEquals("海风书店：未知归属地 服务热线\n山川花店：未知归属地 服务热线", value)
    }

    @Test
    fun `existing structured cache is cleaned only for display`() {
        val entry = CachedSearchResult(results = screenshotResults(), savedAtMillis = 123_456L)
        val encoded = SearchResultCacheCodec.encode(entry)
        val restored = SearchResultCacheCodec.decode(encoded)!!

        assertScreenshotFacts(SearchResultPresentation.summarize(number, restored.results))
        assertEquals(entry, restored)
        assertEquals(encoded, SearchResultCacheCodec.encode(restored))
        assertEquals(setOf(SearchEngine.SOGOU), restored.results.last().sources)
        assertEquals("https://example.invalid/sogou", restored.results.last().targetUrl)
        assertEquals("$countedClaim 未知归属地", restored.results.last().snippet)
    }

    private fun screenshotResults() = listOf(
        result("未知归属地 唯品会", SearchEngine.BAIDU),
        result("广告推销", SearchEngine.QIHOO),
        result("$countedClaim 未知归属地", SearchEngine.SOGOU),
    )

    private fun assertScreenshotFacts(value: String) {
        assertEquals(value, 1, occurrences(value, unknownLocation))
        assertEquals(value, 1, occurrences(value, "唯品会"))
        assertTrue(value, value.contains("广告推销"))
        assertTrue(value, value.contains(countedClaim))
    }

    private fun result(snippet: String, source: SearchEngine = SearchEngine.BAIDU) = BaiduSearchResult(
        title = "${number}号码查询",
        snippet = snippet,
        targetUrl = "https://example.invalid/${source.id}",
        sources = setOf(source),
        isNumberService = true,
    )

    private fun occurrences(value: String, token: String): Int = Regex(Regex.escape(token)).findAll(value).count()
}
