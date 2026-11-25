package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultSanitizerTest {
    @Test
    fun `removes standalone 12321 and everything after it`() {
        assertEquals(
            "广东 深圳 中国联通 10000000001",
            SearchResultSanitizer.truncate(
                "广东 深圳 中国联通 10000000001 12321 投诉 号码认证 号码AI助手",
            ),
        )
    }

    @Test
    fun `keeps 12321 when it is part of a longer phone number`() {
        assertEquals(
            "号码 10000123219 正常展示",
            SearchResultSanitizer.truncate("号码 10000123219 正常展示"),
        )
    }

    @Test
    fun `removes separators immediately before marker`() {
        assertEquals(
            "广东 深圳 中国联通",
            SearchResultSanitizer.truncate("广东 深圳 中国联通，12321 后续内容"),
        )
    }

    @Test
    fun `sanitizes both title and snippet`() {
        val result = SearchResultSanitizer.sanitize(
            BaiduSearchResult(
                title = "号码查询 12321 更多",
                snippet = "广东 深圳 12321 投诉",
                targetUrl = "https://example.com",
            ),
            phoneNumber = "10000000001",
        )

        assertEquals("号码查询", result.title)
        assertEquals("广东 深圳", result.snippet)
    }

    @Test
    fun `removes queried phone number from snippet but keeps title`() {
        val result = SearchResultSanitizer.sanitize(
            BaiduSearchResult(
                title = "10000000001号码查询",
                snippet = "广东 深圳 中国联通 100 0000-0001",
                targetUrl = null,
            ),
            phoneNumber = "10000000001",
        )

        assertEquals("10000000001号码查询", result.title)
        assertEquals("广东 深圳 中国联通", result.snippet)
    }

    @Test
    fun `removes optional country code along with the queried number`() {
        listOf("+86（100）0000-0007", "0086 10000000007", "86 100 0000 0007").forEach { formatted ->
            assertEquals(
                "广东 深圳 中国电信",
                SearchResultSanitizer.removeQueriedNumber(
                    "广东 深圳 中国电信 $formatted",
                    "10000000007",
                ),
            )
        }
    }

    @Test
    fun `aggregation and presentation do not leave a detached country code`() {
        val number = "10000000007"
        val results = SearchResultAggregator.merge(
            number,
            listOf(BaiduSearchResult(
                title = "${number}号码查询",
                snippet = "广东 深圳 中国电信 +86 100 0000-0007 12321 投诉",
                targetUrl = null,
            )),
        )
        assertEquals("广东 深圳 · 中国电信", SearchResultPresentation.summarize(number, results))
    }
}
