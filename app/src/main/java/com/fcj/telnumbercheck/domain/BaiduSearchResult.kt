package com.fcj.telnumbercheck.domain

data class BaiduSearchResult(
    val title: String,
    val snippet: String,
    val targetUrl: String?,
    val sources: Set<SearchEngine> = setOf(SearchEngine.BAIDU),
    val isNumberService: Boolean = false,
)
