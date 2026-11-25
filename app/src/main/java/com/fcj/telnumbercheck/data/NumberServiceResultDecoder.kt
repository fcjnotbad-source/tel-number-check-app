package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.json.JSONObject
import org.json.JSONTokener

internal data class NumberServicePageResult(
    val status: String,
    val results: List<BaiduSearchResult> = emptyList(),
)

/** Accepts only explicit number-service cards from the shared DOM extractor. */
internal object NumberServiceResultDecoder {
    fun decode(value: String?, engine: SearchEngine): NumberServicePageResult? = runCatching {
        if (value.isNullOrBlank() || value == "null") return null
        val unwrapped = JSONTokener(value).nextValue() as? String ?: return null
        val json = JSONObject(unwrapped)
        val status = json.optString("status")
        if (status != "ok") return NumberServicePageResult(status)
        val entries = json.optJSONArray("results") ?: return NumberServicePageResult(status)
        val results = (0 until entries.length()).mapNotNull { index ->
            val item = entries.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("kind") != "number_service") return@mapNotNull null
            val title = (item.opt("title") as? String).orEmpty().trim()
            val snippet = (item.opt("snippet") as? String).orEmpty().trim()
            if (title.isBlank() || snippet.isBlank()) return@mapNotNull null
            BaiduSearchResult(
                title = title,
                snippet = snippet,
                targetUrl = item.optString("url").takeIf { it.startsWith("https://") },
                sources = setOf(engine),
                isNumberService = true,
            )
        }
        NumberServicePageResult(status, results)
    }.getOrNull()
}
