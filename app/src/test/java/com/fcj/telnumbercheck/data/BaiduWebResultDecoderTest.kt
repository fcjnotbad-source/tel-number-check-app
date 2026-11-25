package com.fcj.telnumbercheck.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaiduWebResultDecoderTest {
    @Test
    fun decodesEvaluateJavascriptStringResult() {
        val pageResult =
            """{"status":"ok","title":"号码信息","snippet":"疑似骚扰电话","url":"https://example.com"}"""

        val decoded = BaiduWebResultDecoder.decode(JSONObject.quote(pageResult))

        assertEquals("ok", decoded?.status)
        assertEquals("号码信息", decoded?.title)
        assertEquals("疑似骚扰电话", decoded?.snippet)
        assertEquals("https://example.com", decoded?.targetUrl)
    }

    @Test
    fun ignoresNullJavascriptResult() {
        assertNull(BaiduWebResultDecoder.decode("null"))
    }
}
