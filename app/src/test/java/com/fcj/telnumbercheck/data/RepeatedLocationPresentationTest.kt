package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatedLocationPresentationTest {
    private val number = "10000000007"

    @Test
    fun `combines screenshot metadata into one location without modifying raw results`() {
        val first = result("广东 深圳 中国联通", SearchEngine.BAIDU)
        val second = result("美团 网络收录数据 广东 深圳", SearchEngine.SOGOU)
        val results = listOf(first, second)
        val snapshot = results.map { it.copy(sources = it.sources.toSet()) }

        assertEquals("广东 深圳 · 中国联通 · 美团", SearchResultPresentation.summarize(number, results))
        assertEquals(snapshot, results)
        assertEquals("https://example.com/sogou", second.targetUrl)
    }

    @Test
    fun `removes repeated location and metadata template inside one complete snippet`() {
        val value = summarize("广东 深圳 中国联通 美团 网络收录数据 广东 深圳")
        assertEquals(value, 1, occurrences(compact(value), "广东深圳"))
        assertTrue(value, value.contains("中国联通"))
        assertTrue(value, value.contains("美团"))
        assertFalse(value, value.contains("网络收录数据"))
    }

    @Test
    fun `recognizes complete trailing location with spacing punctuation and administrative suffixes`() {
        listOf(
            "美团 网络收录数据 广东深圳",
            "美团 · 网络收录数据 · 广东省 深圳市",
            "美团，网络收录数据，广东、深圳",
        ).forEach { tail ->
            assertEquals(tail, "广东 深圳 · 中国联通 · 美团", summarize("广东 深圳 中国联通", tail))
        }
    }

    @Test
    fun `removes complete middle metadata block while retaining business and risk description`() {
        val value = summarize("广东 深圳 中国联通", "美团，广东 深圳，广告推销")

        assertEquals(value, 1, occurrences(compact(value), "广东深圳"))
        assertTrue(value, value.contains("美团"))
        assertTrue(value, value.contains("广告推销"))
        assertTrue(value, value.contains("中国联通"))
    }

    @Test
    fun `preserves different cities provinces and their carrier information`() {
        val value = compact(summarize(
            "广东 深圳 中国联通",
            "美团 网络收录数据 广东 广州",
            "浙江 杭州 中国移动",
        ))

        listOf("广东深圳", "广东广州", "浙江杭州", "中国联通", "中国移动", "美团").forEach {
            assertTrue(value, value.contains(it))
        }
    }

    @Test
    fun `a province alone is not enough evidence to remove a different description`() {
        val value = compact(summarize("广东 深圳 中国联通", "服务覆盖广东多个城市"))

        assertTrue(value, value.contains("广东深圳"))
        assertTrue(value, value.contains("服务覆盖广东多个城市"))
    }

    @Test
    fun `retains location words that belong to a company name or a full postal address`() {
        val company = "广东深圳海风科技有限公司"
        val address = "办公地址：广东省深圳市南山区科技路18号"
        val value = compact(SearchResultPresentation.summarize(number, listOf(
            result("广东 深圳 中国联通"),
            BaiduSearchResult("$number $company", address, "https://example.com/company"),
        )))

        assertTrue(value, value.contains(company))
        assertTrue(value, value.contains(compact(address)))
    }

    @Test
    fun `retains complete location denial instead of removing its geographic words`() {
        listOf("并非广东深圳", "不在广东深圳").forEach { denial ->
            val value = compact(summarize("广东 深圳 中国联通", denial))
            assertTrue(value, value.contains(denial))
            assertTrue(value, value.contains("中国联通"))
        }
    }

    @Test
    fun `retains uncertainty user counts and labels after removing repeated metadata`() {
        val value = summarize(
            "广东 深圳 中国联通",
            "美团 网络收录数据 广东 深圳，疑似广告推销，被12人标记",
        )

        assertEquals(value, 1, occurrences(compact(value), "广东深圳"))
        listOf("中国联通", "美团", "疑似广告推销", "被12人标记").forEach {
            assertTrue(value, compact(value).contains(compact(it)))
        }
        assertFalse(value, value.contains("网络收录数据"))
    }

    @Test
    fun `applies the same cleanup immediately to old multiline saved summaries`() {
        assertEquals(
            "广东 深圳 · 中国联通 · 美团",
            SearchResultPresentation.summarizeText(
                number,
                "广东 深圳 中国联通\n美团 网络收录数据 广东 深圳",
            ),
        )
    }

    @Test
    fun `deduplicates a scoped business location when there is only one geographic context`() {
        val value = SearchResultPresentation.summarize(number, listOf(
            result("广东 深圳 中国联通"),
            BaiduSearchResult("美团", "网络收录数据 广东 深圳", "https://example.com/meituan"),
        ))

        assertEquals(value, 1, occurrences(compact(value), "广东深圳"))
        assertTrue(value, value.contains("美团"))
        assertTrue(value, value.contains("中国联通"))
        assertFalse(value, value.contains("网络收录数据"))
    }

    @Test
    fun `preserves scoped business geographic association when different regions conflict`() {
        val value = SearchResultPresentation.summarize(number, listOf(
            result("广东 深圳 中国联通"),
            result("北京 中国电信"),
            BaiduSearchResult("美团", "网络收录数据 广东 深圳", "https://example.com/meituan"),
        ))

        val merchantLine = value.lineSequence().firstOrNull { it.contains("美团") }.orEmpty()
        assertTrue(value, compact(merchantLine).contains("广东深圳"))
        assertTrue(value, value.contains("北京"))
        assertTrue(value, value.contains("中国电信"))
        assertTrue(value, value.contains("中国联通"))
    }

    private fun summarize(vararg snippets: String): String =
        SearchResultPresentation.summarize(number, snippets.map { result(it) })

    @Test
    fun `does not erase the subject of a qualified statement about collected data`() {
        listOf("网络收录数据可能不准确", "网络收录数据 可能不准确", "网络收录数据公司").forEach { statement ->
            val value = summarize("广东 深圳 中国联通", "美团 $statement")
            assertTrue(value, value.contains(statement))
        }
    }

    @Test
    fun `removes orphan separators after removing repeated middle metadata`() {
        val value = summarize("广东 深圳 中国联通", "美团，广东 深圳，广告推销")
        assertTrue(value, value.contains("美团 · 广告推销"))
        assertFalse(value, value.contains("， ，"))
    }

    private fun result(snippet: String, engine: SearchEngine = SearchEngine.BAIDU) = BaiduSearchResult(
        title = "${number}号码查询",
        snippet = snippet,
        targetUrl = "https://example.com/${engine.id}",
        sources = setOf(engine),
    )

    private fun compact(text: String): String = Regex("[\\p{P}\\p{Z}\\s]+").replace(text, "")
    private fun occurrences(text: String, value: String): Int = Regex(Regex.escape(value)).findAll(text).count()
}
