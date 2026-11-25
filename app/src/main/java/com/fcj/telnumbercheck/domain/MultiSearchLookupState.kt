package com.fcj.telnumbercheck.domain

sealed interface MultiSearchLookupState {
    data object Loading : MultiSearchLookupState
    data class Partial(val results: List<BaiduSearchResult>) : MultiSearchLookupState
    data class Success(val results: List<BaiduSearchResult>) : MultiSearchLookupState
    data class Failure(
        val message: String,
        val requiresVerification: Boolean = false,
    ) : MultiSearchLookupState
}
