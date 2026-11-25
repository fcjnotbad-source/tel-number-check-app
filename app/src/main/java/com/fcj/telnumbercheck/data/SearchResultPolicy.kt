package com.fcj.telnumbercheck.data

/** Revision 3 accepts only explicit number-query/service cards, never organic or AI answers. */
internal object SearchResultPolicy {
    const val REVISION = 3
    const val REQUERY_MESSAGE = "查询规则已更新，请重新检测此号码"

    fun visibleEvent(event: CallEventSnapshot, storedRevision: Int): CallEventSnapshot {
        if (storedRevision == REVISION || event.lookupStatus != CallLookupStatus.SUCCESS) return event

        // Old summaries have no trustworthy module provenance, even when their source is known.
        // Keep the original caller and diagnostics so the user can immediately retry that number.
        return event.copy(
            lookupStatus = CallLookupStatus.FAILURE,
            resultTitle = null,
            resultSnippet = null,
            errorMessage = REQUERY_MESSAGE,
            requiresVerification = false,
            resultFromCache = false,
            searchResults = emptyList(),
        )
    }
}
