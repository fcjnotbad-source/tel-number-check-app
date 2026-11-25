package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultExplanationCutoffTest {
    private val number = "4001234567"
    private val useful = "京东汽车 广告推销 营销推广电话 被3025人标记"

    @Test
    fun `cuts screenshot explanation and every following paragraph after useful prefix`() {
        val text = "$useful\n这个号段到底是啥：目前没有明确的官方归属记录，" +
            "它是一个中国移动400号段的企业客服类号码。\n2百度百科\n" +
            "是什么电话？_百度教育：是400电话的一种，属于中国移动新号段。"

        assertEquals(useful, SearchResultSanitizer.truncate(text))
    }

    @Test
    fun `cuts common number and number segment question introductions`() {
        listOf(
            "这个号码段到底是啥：",
            "这个号段到底是啥：",
            "这个号码到底是什么电话？",
            "该号段究竟是什么？",
            "本号码是什么号码？",
            "号码段是什么？",
        ).forEach { marker ->
            assertEquals(marker, useful, SearchResultSanitizer.truncate("$useful，$marker 中国移动客服介绍"))
        }
    }

    @Test
    fun `cuts literal what is explanations including question punctuation and newline`() {
        listOf("是什么电话？", "是什么号码？", "是什么号段？", "是什么？", "是什么：", "是 什么电话？").forEach { marker ->
            assertEquals(marker, useful, SearchResultSanitizer.truncate("$useful；$marker\n百科介绍\n更多内容"))
        }
    }

    @Test
    fun `entire explanation becomes blank when there is no useful prefix`() {
        assertEquals("", SearchResultSanitizer.truncate("这个号码段到底是啥：中国移动400号码的介绍"))
        assertEquals("", SearchResultSanitizer.truncate("是什么电话？中国移动客服号段"))
    }

    @Test
    fun `uses earliest cutoff between complaint marker and explanation`() {
        assertEquals(useful, SearchResultSanitizer.truncate("$useful 12321 投诉 这个号段到底是啥：科普"))
        assertEquals(useful, SearchResultSanitizer.truncate("$useful 是什么电话？介绍 12321 投诉"))
    }

    @Test
    fun `retains useful snippet before question while removing queried number`() {
        val result = SearchResultSanitizer.sanitize(
            result("$number $useful 是什么电话？中国移动400号段的企业客服介绍"),
            number,
        )

        assertEquals("${number}号码查询", result.title)
        assertEquals(useful, result.snippet)
    }

    @Test
    fun `question title excludes its whole explanatory snippet from aggregation`() {
        val results = listOf(
            BaiduSearchResult("这个号码段到底是啥：$number", "中国移动400号段的企业客服类号码", null),
            BaiduSearchResult("${number}是什么电话？_百度教育", "属于中国移动新号段，主要用于企业售前售后", null),
        )

        assertTrue(SearchResultAggregator.merge(number, results).isEmpty())
    }

    @Test
    fun `structured cached question titles do not leak their snippets into presentation`() {
        val result = BaiduSearchResult(
            "${number}是什么电话？_百度教育",
            "是400电话的一种，属于中国移动新号段，是主被叫分摊业务。",
            null,
        )

        assertEquals("", SearchResultPresentation.summarize(number, listOf(result)))
    }

    @Test
    fun `combines useful search cards without retaining generic question cards`() {
        val results = listOf(
            result("京东汽车 广告推销"),
            result("营销推广电话 被3025人标记"),
            BaiduSearchResult("这个号段到底是啥：$number", "中国移动400号段的企业客服介绍", null),
            BaiduSearchResult("$number 是什么电话？_百度教育", "400电话的一种，主被叫分摊费用。", null),
        )
        val value = SearchResultPresentation.summarize(number, SearchResultAggregator.merge(number, results))

        listOf("京东汽车", "广告推销", "营销推广电话", "被3025人标记").forEach { assertTrue(value, value.contains(it)) }
        listOf("这个号段", "是什么", "400号段", "百度教育", "分摊费用").forEach { assertFalse(value, value.contains(it)) }
    }

    @Test
    fun `legacy flattened summaries lose entire multiline tail not only first question line`() {
        val value = SearchResultPresentation.summarizeText(
            number,
            "$useful\n这个号码段到底是啥：目前没有明确记录\n中国移动400号段的企业客服类号码\n" +
                "接到时需要核实\n百度百科\n是什么电话？\n主被叫分摊费用",
        )

        assertEquals(useful, value)
    }

    @Test
    fun `new independent useful card remains after excluded explanation card`() {
        val results = listOf(
            BaiduSearchResult("$number 是什么电话？", "中国移动400号段介绍", null),
            result("京东汽车 广告推销 被3025人标记", SearchEngine.QIHOO),
        )
        val merged = SearchResultAggregator.merge(number, results)

        assertEquals(1, merged.size)
        assertEquals("京东汽车 广告推销 被3025人标记", merged.single().snippet)
    }

    @Test
    fun `keeps genuine business address negative and uncertainty facts intact`() {
        listOf(
            "京东汽车客服",
            "广东 深圳 中国联通",
            "广东省深圳市南山区科技路12号 海风书店",
            "该号码未被标记为骚扰",
            "疑似营销电话，并非官方客服",
            "号码 10000123219 正常展示",
        ).forEach { text ->
            assertEquals(text, text, SearchResultSanitizer.truncate(text))
        }
    }

    private fun result(snippet: String, engine: SearchEngine = SearchEngine.BAIDU) = BaiduSearchResult(
        title = "${number}号码查询",
        snippet = snippet,
        targetUrl = null,
        sources = setOf(engine),
    )
}
