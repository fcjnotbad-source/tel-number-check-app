package com.fcj.telnumbercheck.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fcj.telnumbercheck.domain.CallerInfo

/** One lookup session belongs to the home, never to a particular navigation destination. */
internal class HomeLookupSession {
    var result by mutableStateOf<CallerInfo?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var activeNumber by mutableStateOf("")
        private set
    var requestId by mutableStateOf(0L)
        private set
    var retryingLatestCall by mutableStateOf(false)
        private set

    fun start(pending: CallerInfo, retryLatest: Boolean = false) {
        if (pending.normalizedNumber.isBlank()) return
        requestId += 1
        activeNumber = pending.normalizedNumber
        result = pending
        loading = true
        retryingLatestCall = retryLatest
    }

    fun useCached(info: CallerInfo) {
        requestId += 1 // Invalidate callbacks from any superseded request.
        activeNumber = ""
        result = info
        loading = false
        retryingLatestCall = false
    }

    fun matches(number: String, id: Long): Boolean = activeNumber.isNotBlank() &&
        activeNumber == number && requestId == id

    fun receive(number: String, id: Long, info: CallerInfo, complete: Boolean): Boolean {
        if (!matches(number, id) || info.normalizedNumber != number) return false
        result = info
        if (complete) {
            activeNumber = ""
            loading = false
            retryingLatestCall = false
        }
        return true
    }

    fun refreshLabel(info: CallerInfo) {
        if (result?.normalizedNumber == info.normalizedNumber) result = info
    }
}
