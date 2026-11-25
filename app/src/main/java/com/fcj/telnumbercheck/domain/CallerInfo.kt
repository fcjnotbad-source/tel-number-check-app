package com.fcj.telnumbercheck.domain

data class CallerInfo(
    val normalizedNumber: String,
    val displayNumber: String,
    val baiduTitle: String?,
    val baiduSnippet: String?,
    val baiduUrl: String?,
    val localLabel: String?,
    val errorMessage: String? = null,
    val requiresVerification: Boolean = false,
    val isFromCache: Boolean = false,
    val cachedAtMillis: Long? = null,
    val searchResults: List<BaiduSearchResult> = emptyList(),
    val isRefreshing: Boolean = false,
) {
    val headline: String
        get() = baiduTitle ?: localLabel ?: "搜索引擎暂未返回结果"

    val detail: String
        get() = baiduSnippet ?: errorMessage ?: "没有可显示的摘要"

    val isLocallyMarked: Boolean
        get() = localLabel != null
}
