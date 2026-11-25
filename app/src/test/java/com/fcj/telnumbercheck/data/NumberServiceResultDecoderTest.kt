package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberServiceResultDecoderTest {
    private val number = "075595566"

    @Test
    fun `accepts only explicitly tagged number service cards`() {
        val decoded = decode(
            entry("number_service", "中国银行 服务热线"),
            entry(null, "这个号码到底是哪家的 中国银行 深圳"),
            entry("organic", "来电显示+075595566最有可能是 参考答案B"),
            entry("ai", "公开资料显示它可能与中国银行有关"),
        )!!

        assertEquals("ok", decoded.status)
        assertEquals(1, decoded.results.size)
        assertEquals("中国银行 服务热线", decoded.results.single().snippet)
        assertTrue(decoded.results.single().isNumberService)
        assertEquals(setOf(SearchEngine.SOGOU), decoded.results.single().sources)
    }

    @Test
    fun `untagged exact number and telecom keywords cannot bypass card boundary`() {
        assertTrue(decode(entry(null, "$number 广东深圳 中国银行 客服 本题考察"))!!.results.isEmpty())
    }

    @Test
    fun `verified short business names survive aggregation`() {
        listOf("中国银行", "美团").forEach { name ->
            val cards = decode(entry("number_service", name))!!.results
            val merged = SearchResultAggregator.merge(number, cards)
            assertEquals(name, name, merged.single().snippet)
            assertEquals(name, SearchResultPresentation.summarize(number, merged))
        }
    }

    @Test
    fun `short business exception does not apply to unverified text`() {
        val unverified = BaiduSearchResult("${number}号码查询", "中国银行", null)
        assertFalse(unverified.isNumberService)
        assertTrue(SearchResultAggregator.merge(number, listOf(unverified)).isEmpty())
    }

    @Test
    fun `verified location and bank service cards combine without repeated regions or names`() {
        val cards = listOf(
            BaiduSearchResult("${number}号码查询", "广东 深圳 中国银行", null, setOf(SearchEngine.BAIDU), true),
            BaiduSearchResult("${number}号码查询", "广东 深圳", null, setOf(SearchEngine.QIHOO), true),
            BaiduSearchResult("${number}号码查询", "中国银行 服务热线", null, setOf(SearchEngine.SOGOU), true),
        )

        assertEquals(
            "广东 深圳 · 中国银行 服务热线",
            SearchResultPresentation.summarize(number, SearchResultAggregator.merge(number, cards)),
        )
    }

    @Test
    fun `verified card still preserves company names with no location separator and full addresses`() {
        listOf("广东深圳国际酒店", "广东深圳电信公司", "广东省深圳市南山区科技路12号 海风书店").forEach { name ->
            val cards = decode(entry("number_service", name))!!.results
            assertEquals(name, SearchResultPresentation.summarize(number, cards))
        }
    }

    @Test
    fun `verified marker does not make empty placeholder or blocked page useful`() {
        listOf("号码查询", "没有可显示的摘要", "请输入验证码后继续", "搜索超时").forEach { text ->
            assertTrue(text, SearchResultAggregator.merge(number, decode(entry("number_service", text))!!.results).isEmpty())
        }
    }

    @Test
    fun `rejects blank or non text payloads`() {
        val blank = entry("number_service", " ")
        val titleMissing = entry("number_service", "中国银行").put("title", JSONObject.NULL)
        val objectSnippet = entry("number_service", "中国银行").put("snippet", JSONObject())
        assertTrue(decode(blank, titleMissing, objectSnippet)!!.results.isEmpty())
    }

    @Test
    fun `unsafe target link is not propagated`() {
        val result = decode(entry("number_service", "中国银行").put("url", "javascript:alert(1)"))!!.results.single()
        assertNull(result.targetUrl)
    }

    @Test
    fun `source identity comes from current query not page payload`() {
        val result = decode(entry("number_service", "中国银行").put("sources", JSONArray(listOf("baidu"))))!!.results.single()
        assertEquals(setOf(SearchEngine.SOGOU), result.sources)
    }

    @Test
    fun `verification empty and pending statuses never expose result payload`() {
        listOf("verify", "empty", "pending").forEach { status ->
            val value = JSONObject.quote(JSONObject().put("status", status)
                .put("results", JSONArray().put(entry("number_service", "中国银行"))).toString())
            val decoded = NumberServiceResultDecoder.decode(value, SearchEngine.SOGOU)!!
            assertEquals(status, decoded.status)
            assertTrue(decoded.results.isEmpty())
        }
    }

    @Test
    fun `malformed and missing bridge results are safe to ignore`() {
        listOf(null, "", "null", "not-json", JSONObject.quote("not-json"), "{}").forEach { value ->
            assertNull(NumberServiceResultDecoder.decode(value, SearchEngine.BAIDU))
        }
        assertNotNull(NumberServiceResultDecoder.decode(JSONObject.quote("{\"status\":\"ok\"}"), SearchEngine.BAIDU))
    }

    private fun entry(kind: String?, snippet: String): JSONObject = JSONObject()
        .put("title", "${number}号码查询")
        .put("snippet", snippet)
        .put("url", "https://m.sogou.com/web/searchList.jsp?keyword=$number")
        .also { if (kind != null) it.put("kind", kind) }

    private fun decode(vararg entries: JSONObject): NumberServicePageResult? = NumberServiceResultDecoder.decode(
        JSONObject.quote(JSONObject().put("status", "ok").put("results", JSONArray(entries.toList())).toString()),
        SearchEngine.SOGOU,
    )
}
