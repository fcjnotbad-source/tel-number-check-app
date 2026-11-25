package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountrySuffixedLocationTest {
    private val number = "1304831151"
    private val expected = "广东 深圳 · 广告营销 · 中国联通 骚扰电话 · 营销推广电话 被19人标记"

    @Test
    fun `screenshot country suffix no longer leaves a repeated province and city`() {
        val value = SearchResultPresentation.summarize(number, screenshotResults())

        assertEquals(expected, value)
        assertEquals(1, occurrences(compact(value), "广东深圳"))
        assertEquals(1, occurrences(value, "中国")) // The carrier's 中国 remains.
        assertFalse(value.contains(number))
    }

    @Test
    fun `source order does not restore the country suffixed location`() {
        val results = screenshotResults()
        listOf(results.reversed(), results.drop(1) + results.first()).forEach { ordered ->
            val value = SearchResultPresentation.summarize(number, ordered)

            assertEquals(value, 1, occurrences(compact(value), "广东深圳"))
            listOf("广告营销", "中国联通", "骚扰电话", "营销推广电话", "被19人标记").forEach {
                assertTrue(value, value.contains(it))
            }
            assertEquals(value, 1, occurrences(value, "中国"))
        }
    }

    @Test
    fun `legacy saved summaries receive the same cleanup`() {
        assertEquals(
            expected,
            SearchResultPresentation.summarizeText(
                number,
                "广东 深圳 广告营销\r\n中国联通 骚扰电话\n营销推广电话 被19人标记 广东深圳中国",
            ),
        )
    }

    @Test
    fun `structured cache is formatted without rewriting its raw search data`() {
        val entry = CachedSearchResult(screenshotResults(), savedAtMillis = 123_456L)
        val encoded = SearchResultCacheCodec.encode(entry)
        val decoded = SearchResultCacheCodec.decode(encoded)!!

        assertEquals(expected, SearchResultPresentation.summarize(number, decoded.results))
        assertEquals(entry, decoded)
        assertEquals(encoded, SearchResultCacheCodec.encode(decoded))
        assertEquals("营销推广电话 被19人标记 广东深圳中国", decoded.results.last().snippet)
        assertEquals(setOf(SearchEngine.SOGOU), decoded.results.last().sources)
        assertEquals("https://example.invalid/sogou", decoded.results.last().targetUrl)
    }

    @Test
    fun `all packaged province city pairs remove their own country suffixed duplicate`() {
        val rows = checkNotNull(javaClass.getResourceAsStream("/geo/province-cities.tsv"))
            .bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') }.toList()
            }
        assertEquals(370, rows.size)

        rows.forEach { fields ->
            val place = checkNotNull(AdministrativeDivisionIndex.resolveExact(fields[1] + fields[3]))
            listOf(compact(place.displayName), fields[1] + fields[3]).distinct().forEach { location ->
                listOf("中国", "中华人民共和国").forEach { country ->
                    assertEquals(
                        "${place.key}: $location$country",
                        "标签",
                        RepeatedLocationText.clean("标签 $location$country", place.displayName),
                    )
                }
            }
        }
    }

    @Test
    fun `country field can use whitespace punctuation or full administrative names`() {
        listOf(
            "广东深圳中国",
            "广东 深圳 中国",
            "广东省深圳市中华人民共和国",
            "广东省 深圳市 · 中国",
            "广东、深圳，中国",
            "广东\u3000深圳\u00a0中国",
        ).forEach { metadata ->
            assertEquals(metadata, "被19人标记", RepeatedLocationText.clean("被19人标记 $metadata", "广东 深圳"))
        }
    }

    @Test
    fun `a separate country prefix is included in repeated location cleanup`() {
        listOf(
            "中国广东深圳",
            "中国 广东 深圳",
            "中华人民共和国广东省深圳市",
            "中华人民共和国 · 广东省 深圳市",
            "中国广东深圳中国",
        ).forEach { metadata ->
            assertEquals(metadata, "广告营销", RepeatedLocationText.clean("广告营销 $metadata", "广东 深圳"))
        }
    }

    @Test
    fun `country prefix inside a carrier or bank name is never consumed`() {
        listOf("中国联通", "中国移动", "中国电信", "中国广电", "中国银行").forEach { name ->
            val text = "广东深圳$name"
            assertEquals(text, text, RepeatedLocationText.clean(text, "广东 深圳"))
        }

        assertEquals("中国联通", RepeatedLocationText.clean("广东深圳 中国联通", "广东 深圳"))
    }

    @Test
    fun `country suffixed company identities and addresses remain complete`() {
        listOf(
            "广东深圳中国海风科技有限公司",
            "海风公司 广东深圳中国",
            "地址：广东深圳中国",
            "办公地址：中国广东省深圳市南山区科技路18号",
            "广东深圳中国 南山区科技路18号",
        ).forEach { text ->
            assertEquals(text, text, RepeatedLocationText.clean(text, "广东 深圳"))
        }
    }

    @Test
    fun `country suffixed negative and uncertain location claims remain complete`() {
        listOf(
            "并非 广东深圳中国",
            "不在广东深圳中国",
            "可能 中国广东深圳",
            "无法确认 广东省深圳市中华人民共和国",
        ).forEach { text ->
            assertEquals(text, text, RepeatedLocationText.clean(text, "广东 深圳"))
        }
    }

    @Test
    fun `another city and an invalid province city pair are not erased`() {
        listOf("标签 浙江杭州中国", "标签 广东广州中国", "标签 广东杭州中国").forEach { text ->
            assertEquals(text, text, RepeatedLocationText.clean(text, "广东 深圳"))
        }
    }

    private fun screenshotResults() = listOf(
        result("广东 深圳 广告营销", SearchEngine.BAIDU),
        result("中国联通 骚扰电话", SearchEngine.QIHOO),
        result("营销推广电话 被19人标记 广东深圳中国", SearchEngine.SOGOU),
    )

    private fun result(snippet: String, source: SearchEngine) = BaiduSearchResult(
        title = "${number}号码查询",
        snippet = snippet,
        targetUrl = "https://example.invalid/${source.id}",
        sources = setOf(source),
        isNumberService = true,
    )

    private fun compact(text: String): String = Regex("[\\p{P}\\p{Z}\\s]+").replace(text, "")
    private fun occurrences(text: String, value: String): Int = Regex(Regex.escape(value)).findAll(text).count()
}
