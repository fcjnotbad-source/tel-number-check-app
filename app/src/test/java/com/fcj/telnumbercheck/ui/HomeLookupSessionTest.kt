package com.fcj.telnumbercheck.ui

import com.fcj.telnumbercheck.domain.CallerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLookupSessionTest {
    private val number = "075595566"

    @Test
    fun `initial home has no request or fabricated result`() {
        val session = HomeLookupSession()
        assertNull(session.result)
        assertFalse(session.loading)
        assertFalse(session.retryingLatestCall)
        assertFalse(session.matches("", 0))
    }

    @Test
    fun `partial result keeps one active request until completion`() {
        val session = HomeLookupSession()
        session.start(info())
        val id = session.requestId
        assertTrue(session.receive(number, id, info("中国银行"), complete = false))
        assertTrue(session.loading)
        assertTrue(session.matches(number, id))
        assertTrue(session.receive(number, id, info("广东 深圳 中国银行"), complete = true))
        assertFalse(session.loading)
        assertEquals("", session.activeNumber)
        assertEquals("广东 深圳 中国银行", session.result?.detail)
    }

    @Test
    fun `superseded callback cannot overwrite a newer query`() {
        val session = HomeLookupSession()
        session.start(info())
        val oldId = session.requestId
        val other = "10086"
        session.start(info(number = other))
        assertFalse(session.receive(number, oldId, info("old"), complete = true))
        assertEquals(other, session.activeNumber)
        assertTrue(session.loading)
    }

    @Test
    fun `request identity also protects refresh of the same number`() {
        val session = HomeLookupSession()
        session.start(info())
        val oldId = session.requestId
        session.start(info())
        assertFalse(session.receive(number, oldId, info("old"), complete = false))
        assertNull(session.result?.baiduSnippet)
        assertTrue(session.loading)
    }

    @Test
    fun `different result number cannot be inserted into an active request`() {
        val session = HomeLookupSession()
        session.start(info())
        assertFalse(session.receive(number, session.requestId, info("wrong", "10086"), complete = true))
        assertTrue(session.loading)
    }

    @Test
    fun `cache hit invalidates in flight callbacks and retains cached content`() {
        val session = HomeLookupSession()
        session.start(info(), retryLatest = true)
        val oldId = session.requestId
        val cached = info("中国银行").copy(isFromCache = true)
        session.useCached(cached)
        assertEquals(cached, session.result)
        assertFalse(session.loading)
        assertFalse(session.retryingLatestCall)
        assertFalse(session.receive(number, oldId, info("late"), complete = true))
    }

    @Test
    fun `retry target remains marked through partial result then completes`() {
        val session = HomeLookupSession()
        session.start(info(), retryLatest = true)
        val id = session.requestId
        session.receive(number, id, info("partial"), complete = false)
        assertTrue(session.retryingLatestCall)
        session.receive(number, id, info("done"), complete = true)
        assertFalse(session.retryingLatestCall)
        assertFalse(session.receive(number, id, info("duplicate"), complete = true))
    }

    @Test
    fun `local marking does not replace another number or cancel lookup`() {
        val session = HomeLookupSession()
        session.start(info())
        val marked = info().copy(localLabel = "疑似骚扰电话")
        session.refreshLabel(marked)
        assertTrue(session.result!!.isLocallyMarked)
        session.refreshLabel(info("other", "10086"))
        assertEquals(marked, session.result)
        assertTrue(session.loading)
    }

    @Test
    fun `blank pending number cannot destroy an existing session`() {
        val session = HomeLookupSession()
        session.start(info())
        val id = session.requestId
        session.start(info(number = ""))
        assertTrue(session.matches(number, id))
    }

    @Test
    fun `home has three focused destinations in a stable order`() {
        assertEquals(listOf("来电", "查号", "设置"), HomeTab.entries.map { it.label })
    }

    private fun info(detail: String? = null, number: String = this.number) = CallerInfo(
        normalizedNumber = number,
        displayNumber = number,
        baiduTitle = if (detail == null) null else "号码查询",
        baiduSnippet = detail,
        baiduUrl = null,
        localLabel = null,
    )
}
