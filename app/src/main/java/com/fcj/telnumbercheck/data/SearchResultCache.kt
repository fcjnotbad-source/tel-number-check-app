package com.fcj.telnumbercheck.data

import android.content.Context
import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import org.json.JSONArray
import org.json.JSONObject

data class CachedSearchResult(
    val results: List<BaiduSearchResult>,
    val savedAtMillis: Long,
) {
    val result: BaiduSearchResult get() = results.first()
}

class SearchResultCache(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        removeExpiredEntries()
    }

    fun get(number: String): CachedSearchResult? {
        val rawValue = preferences.getString(number, null) ?: return null
        val entry = SearchResultCacheCodec.decode(rawValue)
        val isFresh = entry != null && SearchResultCachePolicy.isFresh(
            savedAtMillis = entry.savedAtMillis,
            nowMillis = nowMillis(),
            maxAgeMillis = MAX_AGE_MILLIS,
        )
        if (!isFresh) {
            preferences.edit().remove(number).apply()
            return null
        }
        return entry
    }

    fun put(number: String, result: BaiduSearchResult): CachedSearchResult {
        return put(number, listOf(result))
    }

    fun put(number: String, results: List<BaiduSearchResult>): CachedSearchResult {
        require(results.isNotEmpty())
        val entry = CachedSearchResult(results = results, savedAtMillis = nowMillis())
        preferences.edit().putString(number, SearchResultCacheCodec.encode(entry)).apply()
        return entry
    }

    private fun removeExpiredEntries() {
        val currentTime = nowMillis()
        val expiredKeys = preferences.all.mapNotNull { (key, value) ->
            val entry = (value as? String)?.let(SearchResultCacheCodec::decode)
            val isFresh = entry != null && SearchResultCachePolicy.isFresh(
                savedAtMillis = entry.savedAtMillis,
                nowMillis = currentTime,
                maxAgeMillis = MAX_AGE_MILLIS,
            )
            key.takeUnless { isFresh }
        }
        if (expiredKeys.isNotEmpty()) {
            preferences.edit().also { editor ->
                expiredKeys.forEach(editor::remove)
            }.apply()
        }
    }

    companion object {
        const val MAX_AGE_MILLIS = 180L * 24 * 60 * 60 * 1_000
        private const val PREFERENCES_NAME = "search_result_cache"
    }
}

internal object SearchResultCachePolicy {
    fun isFresh(savedAtMillis: Long, nowMillis: Long, maxAgeMillis: Long): Boolean {
        if (savedAtMillis <= 0 || savedAtMillis > nowMillis) return false
        return nowMillis - savedAtMillis <= maxAgeMillis
    }
}

internal object SearchResultCacheCodec {
    fun encode(entry: CachedSearchResult): String = JSONObject()
        .put("version", SearchResultPolicy.REVISION)
        .put("results", SearchResultsCodec.encode(entry.results))
        .put("savedAtMillis", entry.savedAtMillis)
        .toString()

    fun decode(rawValue: String): CachedSearchResult? = runCatching {
        val json = JSONObject(rawValue)
        // Earlier entries can contain ordinary web pages or AI summaries, not just number cards.
        require(json.optInt("version") == SearchResultPolicy.REVISION)
        val results = SearchResultsCodec.decode(json.getJSONArray("results"))
        require(results.isNotEmpty())
        CachedSearchResult(
            results = results,
            savedAtMillis = json.getLong("savedAtMillis"),
        )
    }.getOrNull()
}

internal object SearchResultsCodec {
    fun encode(results: List<BaiduSearchResult>): JSONArray = JSONArray().also { json ->
        results.forEach { result ->
            json.put(
                JSONObject()
                    .put("title", result.title)
                    .put("snippet", result.snippet)
                    .put("targetUrl", result.targetUrl ?: JSONObject.NULL)
                    .put("isNumberService", result.isNumberService)
                    .put("sources", JSONArray(result.sources.map(SearchEngine::id))),
            )
        }
    }

    fun decode(json: JSONArray): List<BaiduSearchResult> = buildList {
        for (index in 0 until json.length()) {
            val item = json.getJSONObject(index)
            val title = item.getString("title").trim()
            require(title.isNotBlank())
            val sourceIds = item.getJSONArray("sources")
            val sources = (0 until sourceIds.length())
                .mapNotNull { SearchEngine.fromId(sourceIds.getString(it)) }
                .toSet()
            require(sources.isNotEmpty())
            add(
                BaiduSearchResult(
                    title = title,
                    snippet = item.getString("snippet").trim(),
                    targetUrl = if (item.isNull("targetUrl")) null else {
                        item.optString("targetUrl").trim().ifBlank { null }
                    },
                    sources = sources,
                    isNumberService = item.optBoolean("isNumberService", false),
                ),
            )
        }
    }
}
