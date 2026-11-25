package com.fcj.telnumbercheck.data

import android.content.Context
import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.CallerInfo

class PhoneLookupRepository(context: Context) {
    private val labels = LocalLabelStore(context)
    private val cache = SearchResultCache(context)

    fun cached(rawNumber: String): CallerInfo? {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        if (normalized.isBlank()) return null
        val cached = cache.get(normalized) ?: return null
        val results = SearchResultAggregator.merge(normalized, cached.results)
        if (results.isEmpty()) return null
        return callerInfo(
            normalized = normalized,
            results = results,
            isFromCache = true,
            cachedAtMillis = cached.savedAtMillis,
        )
    }

    fun pending(rawNumber: String): CallerInfo {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        return CallerInfo(
            normalizedNumber = normalized,
            displayNumber = PhoneNumberNormalizer.display(normalized),
            baiduTitle = null,
            baiduSnippet = null,
            baiduUrl = null,
            localLabel = labels.get(normalized),
            errorMessage = "正在通过搜索引擎检测…",
        )
    }

    fun withResult(rawNumber: String, result: BaiduSearchResult): CallerInfo {
        return withResults(rawNumber, listOf(result))
    }

    fun withResults(
        rawNumber: String,
        results: List<BaiduSearchResult>,
        cacheResult: Boolean = true,
    ): CallerInfo {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val merged = SearchResultAggregator.merge(normalized, results)
        if (merged.isEmpty()) return withError(normalized, "暂未查询到有价值的号码信息", false)
        val savedAt = if (cacheResult) cache.put(normalized, merged).savedAtMillis else null
        return callerInfo(
            normalized = normalized,
            results = merged,
            isFromCache = false,
            cachedAtMillis = savedAt,
            isRefreshing = !cacheResult,
        )
    }

    private fun callerInfo(
        normalized: String,
        results: List<BaiduSearchResult>,
        isFromCache: Boolean,
        cachedAtMillis: Long?,
        isRefreshing: Boolean = false,
    ): CallerInfo =
        CallerInfo(
            normalizedNumber = normalized,
            displayNumber = PhoneNumberNormalizer.display(normalized),
            baiduTitle = results.first().title,
            baiduSnippet = SearchResultPresentation.summarize(normalized, results),
            baiduUrl = results.first().targetUrl,
            localLabel = labels.get(normalized),
            isFromCache = isFromCache,
            cachedAtMillis = cachedAtMillis,
            searchResults = results,
            isRefreshing = isRefreshing,
        )

    fun withError(rawNumber: String, message: String, requiresVerification: Boolean): CallerInfo {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        return CallerInfo(
            normalizedNumber = normalized,
            displayNumber = PhoneNumberNormalizer.display(normalized),
            baiduTitle = null,
            baiduSnippet = null,
            baiduUrl = null,
            localLabel = labels.get(normalized),
            errorMessage = message,
            requiresVerification = requiresVerification,
        )
    }

    fun markAsSpam(rawNumber: String) {
        val number = PhoneNumberNormalizer.normalize(rawNumber)
        if (number.isNotBlank()) labels.put(number, "疑似骚扰电话")
    }

    fun clearLocalLabel(rawNumber: String) {
        labels.remove(PhoneNumberNormalizer.normalize(rawNumber))
    }

    fun refreshLocalLabel(info: CallerInfo): CallerInfo =
        info.copy(localLabel = labels.get(info.normalizedNumber))
}
