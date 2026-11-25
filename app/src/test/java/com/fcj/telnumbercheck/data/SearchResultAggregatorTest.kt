package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultAggregatorTest {
    private val number = "10000000001"

    @Test
    fun `keeps useful location when risk label is unavailable`() {
        val results = SearchResultAggregator.merge(
            number,
            listOf(result("广东 深圳 中国联通，暂无标记", SearchEngine.QIHOO)),
        )
        assertEquals(1, results.size)
        assertEquals("广东 深圳 中国联通", results.single().snippet)
    }

    @Test
    fun `merges real location samples with punctuation spacing and carrier naming differences`() {
        val merged = SearchResultAggregator.merge(
            number,
            listOf(
                result("广东 深圳 联通", SearchEngine.QIHOO),
                result("广东深圳中国联通", SearchEngine.SOGOU),
                result("广东、深圳、中国联通 $number 12321 投诉", SearchEngine.BAIDU),
            ),
        )

        assertEquals(1, merged.size)
        assertEquals(SearchEngine.entries.toSet(), merged.single().sources)
        assertTrue(!merged.single().snippet.contains("12321"))
        assertTrue(!merged.single().snippet.contains(number))
    }

    @Test
    fun `drops blocked no result and generic search tool output while retaining useful answer`() {
        val merged = SearchResultAggregator.merge(
            number,
            listOf(
                result("请完成安全验证后继续搜索", SearchEngine.BAIDU),
                result("没有找到相关结果", SearchEngine.SOGOU),
                result("请输入手机号码查询归属地", SearchEngine.QIHOO),
                result("疑似营销电话，被 12 位用户标记", SearchEngine.QIHOO),
            ),
        )

        assertEquals(1, merged.size)
        assertEquals(setOf(SearchEngine.QIHOO), merged.single().sources)
        assertTrue(merged.single().snippet.contains("营销"))
    }

    @Test
    fun `keeps conflicting classifications and locations as separate answers`() {
        val merged = SearchResultAggregator.merge(
            number,
            listOf(
                result("广东深圳中国联通", SearchEngine.BAIDU),
                result("广东深圳中国联通 疑似诈骗", SearchEngine.QIHOO),
                result("广东广州中国移动", SearchEngine.SOGOU),
            ),
        )

        assertEquals(3, merged.size)
    }

    @Test
    fun `retains a number relevant business answer without telecom keywords`() {
        val merged = SearchResultAggregator.merge(
            number,
            listOf(BaiduSearchResult("$number 海风书店", "每天 9 点至 18 点营业", null)),
        )

        assertEquals(1, merged.size)
    }

    @Test
    fun `does not collapse different businesses with identical opening hours`() {
        val merged = SearchResultAggregator.merge(
            number,
            listOf(
                BaiduSearchResult("$number 海风书店", "每天 9 点至 18 点营业", null),
                BaiduSearchResult("$number 山川花店", "每天 9 点至 18 点营业", null, setOf(SearchEngine.SOGOU)),
            ),
        )

        assertEquals(2, merged.size)
    }

    @Test
    fun `all unsuccessful outputs produce no useful results`() {
        assertTrue(
            SearchResultAggregator.merge(
                number,
                listOf(result("搜索超时", SearchEngine.BAIDU), result("暂无相关信息", SearchEngine.SOGOU)),
            ).isEmpty(),
        )
    }

    @Test
    fun `number in a title does not make missing summary or lookup failure useful`() {
        assertTrue(
            SearchResultAggregator.merge(
                number,
                listOf(
                    result("搜索结果没有摘要", SearchEngine.BAIDU),
                    result("未查询到该号码的相关信息", SearchEngine.QIHOO),
                    result("未查到该号码相关记录", SearchEngine.SOGOU),
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `useful business title can stand alone without showing unavailable summary placeholder`() {
        val merged = SearchResultAggregator.merge(
            number,
            listOf(BaiduSearchResult("$number 海风书店", "搜索结果没有摘要", null)),
        )

        assertEquals(1, merged.size)
        assertEquals("", merged.single().snippet)
        assertEquals("$number 海风书店", merged.single().title)
    }

    private fun result(snippet: String, engine: SearchEngine) = BaiduSearchResult(
        title = "${number}号码查询",
        snippet = snippet,
        targetUrl = null,
        sources = setOf(engine),
    )
}
