package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultPolicyTest {
    @Test
    fun `old structured successful diagnostic becomes retriable without leaking its answers`() {
        val legacy = event(
            results = listOf(
                BaiduSearchResult("这个号码到底是哪家的", "中国移动400号段介绍", null),
                BaiduSearchResult("来电显示最有可能是", "答案b，本题考察", null, setOf(SearchEngine.SOGOU)),
            ),
        )

        val migrated = SearchResultPolicy.visibleEvent(legacy, storedRevision = 2)

        assertEquals(CallLookupStatus.FAILURE, migrated.lookupStatus)
        assertEquals(SearchResultPolicy.REQUERY_MESSAGE, migrated.errorMessage)
        assertNull(migrated.resultTitle)
        assertNull(migrated.resultSnippet)
        assertTrue(migrated.searchResults.isEmpty())
        assertFalse(migrated.requiresVerification)
        assertFalse(migrated.resultFromCache)
        assertEquals(legacy.number, migrated.number)
        assertEquals(legacy.receivedAtMillis, migrated.receivedAtMillis)
        assertEquals(legacy.popupOpenedAtMillis, migrated.popupOpenedAtMillis)
        assertEquals(legacy.notificationPosted, migrated.notificationPosted)
    }

    @Test
    fun `unversioned flattened successful diagnostic cannot reappear`() {
        val legacy = event().copy(
            searchResults = emptyList(),
            resultSnippet = "中国银行\n这个号码到底是哪家的\nAI解读\n答案b 本题考察",
        )

        val migrated = SearchResultPolicy.visibleEvent(legacy, storedRevision = 0)

        assertEquals(CallLookupStatus.FAILURE, migrated.lookupStatus)
        assertEquals("075595566", migrated.number)
        assertNull(migrated.resultSnippet)
        assertTrue(migrated.searchResults.isEmpty())
    }

    @Test
    fun `current successful number card diagnostic remains intact`() {
        val current = event(results = listOf(BaiduSearchResult("号码查询", "中国银行 广东深圳", null)))

        assertSame(current, SearchResultPolicy.visibleEvent(current, SearchResultPolicy.REVISION))
    }

    @Test
    fun `unknown diagnostic policy revision is not trusted`() {
        listOf(-1, SearchResultPolicy.REVISION + 1).forEach { revision ->
            assertEquals(
                CallLookupStatus.FAILURE,
                SearchResultPolicy.visibleEvent(event(), revision).lookupStatus,
            )
        }
    }

    @Test
    fun `old failure still displays its useful retry error instead of a new migration error`() {
        val failed = event().copy(
            lookupStatus = CallLookupStatus.FAILURE,
            resultTitle = null,
            resultSnippet = null,
            errorMessage = "查询超时，请重试",
            searchResults = emptyList(),
        )

        assertSame(failed, SearchResultPolicy.visibleEvent(failed, storedRevision = 0))
    }

    @Test
    fun `loading diagnostic is not converted into a failure while lookup is running`() {
        val loading = event().copy(
            lookupStatus = CallLookupStatus.LOADING,
            resultTitle = null,
            resultSnippet = null,
            searchResults = emptyList(),
        )

        assertSame(loading, SearchResultPolicy.visibleEvent(loading, SearchResultPolicy.REVISION))
    }

    private fun event(results: List<BaiduSearchResult> = emptyList()) = CallEventSnapshot(
        number = "075595566",
        receivedAtMillis = 123_456L,
        popupOpenedAtMillis = 123_460L,
        notificationPosted = true,
        lookupStatus = CallLookupStatus.SUCCESS,
        resultTitle = "075595566号码查询",
        resultSnippet = "旧的混杂查询内容",
        errorMessage = null,
        requiresVerification = true,
        resultFromCache = true,
        searchResults = results,
    )
}
