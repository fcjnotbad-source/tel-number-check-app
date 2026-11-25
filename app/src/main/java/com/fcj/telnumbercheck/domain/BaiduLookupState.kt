package com.fcj.telnumbercheck.domain

sealed interface BaiduLookupState {
    data object Loading : BaiduLookupState

    data class Success(val result: BaiduSearchResult) : BaiduLookupState

    data class Failure(
        val message: String,
        val requiresVerification: Boolean = false,
    ) : BaiduLookupState
}
