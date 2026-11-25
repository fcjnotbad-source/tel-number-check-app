package com.fcj.telnumbercheck.data

import org.json.JSONObject
import org.json.JSONTokener

data class BaiduWebResult(
    val status: String,
    val title: String = "",
    val snippet: String = "",
    val targetUrl: String? = null,
)

object BaiduWebResultDecoder {
    fun decode(evaluateJavascriptValue: String?): BaiduWebResult? {
        if (evaluateJavascriptValue.isNullOrBlank() || evaluateJavascriptValue == "null") return null

        return runCatching {
            val unwrapped = JSONTokener(evaluateJavascriptValue).nextValue() as? String
                ?: return null
            val json = JSONObject(unwrapped)
            BaiduWebResult(
                status = json.optString("status"),
                title = json.optString("title"),
                snippet = json.optString("snippet"),
                targetUrl = json.optString("url").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }
}
