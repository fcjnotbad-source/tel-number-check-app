package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultPresentationTest {
    private val number = "10000000007"

    @Test
    fun `combines screenshot location carrier and risk without repeating location`() {
        assertEquals("广东 深圳 · 中国电信 · 广告推销", summarize("广东 深圳 中国电信", "广东 深圳 广告推销", "广东深圳"))
    }

    @Test
    fun `normalizes compact location and carrier variants`() {
        assertEquals("广东 深圳 · 中国联通", summarize("广东深圳联通", "广东 深圳 中国联通", "广东省 深圳市 中国联通"))
    }

    @Test
    fun `keeps fuller descriptions and deduplicates punctuation variants`() {
        assertEquals("广东 深圳 · 广告推销电话", summarize("广东深圳广告推销", "广东 深圳 广告推销电话", "广东深圳广告、推销电话"))
    }

    @Test
    fun `retains different regions and carrier associations`() {
        assertEquals("广东 深圳 · 中国联通客服\n广东 广州 · 中国移动客服", summarize("广东深圳中国联通客服", "广东广州中国移动客服"))
    }

    @Test
    fun `retains qualified claims while deduplicating shared counted fields`() {
        val value = summarize("广东深圳骚扰电话", "广东深圳未标记骚扰电话", "广东深圳疑似骚扰电话", "广东深圳曾被标记骚扰电话", "广东深圳被 12 人标记骚扰电话", "广东深圳被 2 人标记骚扰电话")
        assertEquals(
            "广东 深圳 · 被 12 人标记骚扰电话 · 未标记骚扰电话 · 疑似骚扰电话 · 曾被标记骚扰电话 · 2",
            value,
        )
    }

    @Test
    fun `does not merge fraud with a denial`() {
        val value = summarize("诈骗电话", "不是诈骗电话")
        assertEquals("诈骗电话 · 不是诈骗电话", value)
    }

    @Test
    fun `does not move unsupported negative wording into a location heading`() {
        listOf("非诈骗电话", "没有骚扰记录", "无骚扰记录", "未发现诈骗记录", "不属于广告推销", "并非骚扰电话", "非营销电话").forEach { claim ->
            val value = summarize("广东深圳$claim")
            assertTrue(value, value.contains(claim))
            assertFalse(value, value.contains(" · "))
        }
    }

    @Test
    fun `does not erase carrier identity when telecom fraud is another description`() {
        assertEquals("广东 深圳 · 中国电信 · 电信诈骗电话", summarize("广东深圳中国电信", "广东深圳电信诈骗电话"))
    }

    @Test
    fun `retains affirmative claims together with bare negative wording`() {
        listOf("无骚扰记录", "不骚扰").forEach { claim ->
            val value = summarize("广东深圳骚扰", "广东深圳$claim")
            assertTrue(value, value.contains(" · 骚扰 · "))
            assertTrue(value, value.contains(claim))
        }
    }

    @Test
    fun `preserves distinct company titles even with identical snippets`() {
        val value = SearchResultPresentation.summarize(number, listOf(
            BaiduSearchResult("$number 海风书店", "每天 9 点至 18 点营业", null),
            BaiduSearchResult("$number 山川花店", "每天 9 点至 18 点营业", null),
        ))
        assertEquals("海风书店：每天 9 点至 18 点营业\n山川花店：每天 9 点至 18 点营业", value)
    }

    @Test
    fun `removes repeated company heading without losing business details`() {
        val value = SearchResultPresentation.summarize(number, listOf(
            BaiduSearchResult("$number 海风书店", "海风书店 每天 9 点营业", null),
        ))
        assertEquals("海风书店：每天 9 点营业", value)
    }

    @Test
    fun `deduplicates unscoped descriptions already present in located results`() {
        assertEquals("广东 深圳 · 中国电信 · 广告推销", summarize("广东深圳中国电信", "中国电信", "广告推销"))
    }

    @Test
    fun `retains different chinese user counts`() {
        val value = summarize("被二人标记骚扰电话", "被十二人标记骚扰电话")
        assertTrue(value.contains("被二人标记"))
        assertTrue(value.contains("被十二人标记"))
    }

    @Test
    fun `removes queried numbers including country code and 12321 tail`() {
        assertEquals("广东 深圳 · 中国电信", summarize("广东 深圳 中国电信 +86 100 0000-0007 12321 投诉"))
    }

    @Test
    fun `discards placeholders but retains useful title and location`() {
        val results = listOf(
            result("搜索结果没有摘要"),
            result("广东深圳中国电信，暂无标记"),
            BaiduSearchResult("$number 海风书店", "暂无摘要", null),
        )
        assertEquals("广东 深圳 · 中国电信\n海风书店", SearchResultPresentation.summarize(number, results))
    }

    @Test
    fun `formats older multiline summaries without changing source data`() {
        val value = SearchResultPresentation.summarizeText(number, "广东 深圳 中国电信\n广东 深圳 广告推销\n广东深圳")
        assertEquals("广东 深圳 · 中国电信 · 广告推销", value)
        val source = result("广东 深圳 中国电信 $number")
        SearchResultPresentation.summarize(number, listOf(source))
        assertTrue(source.snippet.contains(number))
        assertFalse(value.contains(number))
    }

    private fun summarize(vararg snippets: String) = SearchResultPresentation.summarize(number, snippets.map(::result))
    private fun result(snippet: String) = BaiduSearchResult("${number}号码查询", snippet, null)
}
