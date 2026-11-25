package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class SearchResultCacheTest {
    @Test
    fun `cache validity is 180 days`() {
        assertEquals(180L * 24 * 60 * 60 * 1_000, SearchResultCache.MAX_AGE_MILLIS)
    }

    @Test
    fun `current number card cache round trips results from every source`() {
        val entry = CachedSearchResult(
            results = listOf(
                BaiduSearchResult(
                    title = "号码信息",
                    snippet = "疑似营销电话",
                    targetUrl = "https://example.com/result",
                    sources = setOf(SearchEngine.BAIDU, SearchEngine.QIHOO),
                ),
                BaiduSearchResult("号码信息", "广东深圳联通", null, setOf(SearchEngine.SOGOU)),
            ),
            savedAtMillis = 123_456L,
        )

        val encoded = SearchResultCacheCodec.encode(entry)
        assertEquals(SearchResultPolicy.REVISION, JSONObject(encoded).getInt("version"))
        assertEquals(entry, SearchResultCacheCodec.decode(encoded))
    }

    @Test
    fun `codec preserves a missing result url`() {
        val entry = CachedSearchResult(
            results = listOf(BaiduSearchResult("号码信息", "暂无摘要", null)),
            savedAtMillis = 123_456L,
        )

        assertEquals(entry, SearchResultCacheCodec.decode(SearchResultCacheCodec.encode(entry)))
    }

    @Test
    fun `codec preserves explicit number service provenance`() {
        val entry = CachedSearchResult(
            results = listOf(
                BaiduSearchResult(
                    title = "号码查询",
                    snippet = "中国银行 服务热线",
                    targetUrl = null,
                    sources = setOf(SearchEngine.SOGOU),
                    isNumberService = true,
                ),
            ),
            savedAtMillis = 123_456L,
        )

        val decoded = SearchResultCacheCodec.decode(SearchResultCacheCodec.encode(entry))

        assertEquals(entry, decoded)
        assertTrue(decoded!!.results.single().isNumberService)
    }

    @Test
    fun `codec does not invent service provenance when the field is absent`() {
        val entry = CachedSearchResult(
            results = listOf(BaiduSearchResult("号码查询", "中国银行", null)),
            savedAtMillis = 123_456L,
        )
        val payload = JSONObject(SearchResultCacheCodec.encode(entry))
        payload.getJSONArray("results").getJSONObject(0).remove("isNumberService")

        val decoded = SearchResultCacheCodec.decode(payload.toString())

        assertFalse(decoded!!.results.single().isNumberService)
    }

    @Test
    fun `malformed cache is ignored`() {
        assertNull(SearchResultCacheCodec.decode("not-json"))
    }

    @Test
    fun `old unversioned cache cannot suppress a strict number card lookup`() {
        assertNull(
            SearchResultCacheCodec.decode(
                """{"title":"号码查询","snippet":"广东深圳联通","savedAtMillis":123456}""",
            ),
        )
    }

    @Test
    fun `old version two cache is rejected even with a fresh useful looking answer`() {
        val entry = CachedSearchResult(
            results = listOf(BaiduSearchResult("号码信息", "中国银行 广东深圳", null)),
            savedAtMillis = System.currentTimeMillis(),
        )
        val oldPayload = JSONObject(SearchResultCacheCodec.encode(entry)).put("version", 2).toString()

        assertNull(SearchResultCacheCodec.decode(oldPayload))
    }

    @Test
    fun `unknown policy revision cache is ignored`() {
        val entry = CachedSearchResult(
            results = listOf(BaiduSearchResult("号码信息", "中国银行 广东深圳", null)),
            savedAtMillis = 123_456L,
        )
        val futurePayload = JSONObject(SearchResultCacheCodec.encode(entry))
            .put("version", SearchResultPolicy.REVISION + 1).toString()

        assertNull(SearchResultCacheCodec.decode(futurePayload))
    }

    @Test
    fun `empty aggregate cache is ignored`() {
        val payload = JSONObject()
            .put("version", SearchResultPolicy.REVISION)
            .put("results", org.json.JSONArray())
            .put("savedAtMillis", 123456L)
        assertNull(SearchResultCacheCodec.decode(payload.toString()))
    }

    @Test
    fun `cache remains fresh through its expiry boundary`() {
        val maxAge = SearchResultCache.MAX_AGE_MILLIS
        assertTrue(SearchResultCachePolicy.isFresh(1_000L, 1_000L + maxAge, maxAge))
        assertFalse(SearchResultCachePolicy.isFresh(1_000L, 1_001L + maxAge, maxAge))
    }

    @Test
    fun `future cache timestamps are rejected`() {
        assertFalse(SearchResultCachePolicy.isFresh(2_000L, 1_000L, 10_000L))
    }
}
