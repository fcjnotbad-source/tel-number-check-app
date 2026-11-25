package com.fcj.telnumbercheck.data

import android.content.Context
import android.content.SharedPreferences
import com.fcj.telnumbercheck.domain.CallerInfo
import com.fcj.telnumbercheck.domain.BaiduSearchResult
import org.json.JSONArray

data class CallEventSnapshot(
    val number: String,
    val receivedAtMillis: Long,
    val popupOpenedAtMillis: Long,
    val notificationPosted: Boolean,
    val lookupStatus: CallLookupStatus,
    val resultTitle: String?,
    val resultSnippet: String?,
    val errorMessage: String?,
    val requiresVerification: Boolean,
    val resultFromCache: Boolean,
    val searchResults: List<BaiduSearchResult> = emptyList(),
)

enum class CallLookupStatus {
    LOADING,
    SUCCESS,
    FAILURE,
}

class CallEventStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun recordReceived(rawNumber: String) {
        preferences.edit()
            .putString(KEY_NUMBER, PhoneNumberNormalizer.normalize(rawNumber))
            .putLong(KEY_RECEIVED_AT, System.currentTimeMillis())
            .putLong(KEY_POPUP_OPENED_AT, 0L)
            .putBoolean(KEY_NOTIFICATION_POSTED, false)
            .putString(KEY_LOOKUP_STATUS, CallLookupStatus.LOADING.name)
            .putInt(KEY_RESULT_POLICY_REVISION, SearchResultPolicy.REVISION)
            .remove(KEY_RESULT_TITLE)
            .remove(KEY_RESULT_SNIPPET)
            .remove(KEY_SEARCH_RESULTS)
            .remove(KEY_ERROR_MESSAGE)
            .putBoolean(KEY_REQUIRES_VERIFICATION, false)
            .putBoolean(KEY_RESULT_FROM_CACHE, false)
            .apply()
    }

    fun recordNotificationPosted(posted: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFICATION_POSTED, posted).apply()
    }

    fun recordPopupOpened(rawNumber: String) {
        preferences.edit()
            .putString(KEY_NUMBER, PhoneNumberNormalizer.normalize(rawNumber))
            .putLong(KEY_POPUP_OPENED_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordLookupRetry(rawNumber: String) {
        preferences.edit()
            .putString(KEY_NUMBER, PhoneNumberNormalizer.normalize(rawNumber))
            .putString(KEY_LOOKUP_STATUS, CallLookupStatus.LOADING.name)
            .putInt(KEY_RESULT_POLICY_REVISION, SearchResultPolicy.REVISION)
            .remove(KEY_RESULT_TITLE)
            .remove(KEY_RESULT_SNIPPET)
            .remove(KEY_SEARCH_RESULTS)
            .remove(KEY_ERROR_MESSAGE)
            .putBoolean(KEY_REQUIRES_VERIFICATION, false)
            .putBoolean(KEY_RESULT_FROM_CACHE, false)
            .apply()
    }

    fun recordLookupSuccess(info: CallerInfo) {
        if (!matchesCurrentNumber(info.normalizedNumber)) return
        preferences.edit()
            .putString(KEY_LOOKUP_STATUS, CallLookupStatus.SUCCESS.name)
            .putInt(KEY_RESULT_POLICY_REVISION, SearchResultPolicy.REVISION)
            .putString(KEY_RESULT_TITLE, info.headline)
            .putString(KEY_RESULT_SNIPPET, info.detail)
            .putString(KEY_SEARCH_RESULTS, SearchResultsCodec.encode(info.searchResults).toString())
            .remove(KEY_ERROR_MESSAGE)
            .putBoolean(KEY_REQUIRES_VERIFICATION, false)
            .putBoolean(KEY_RESULT_FROM_CACHE, info.isFromCache)
            .apply()
    }

    fun recordLookupFailure(
        message: String,
        requiresVerification: Boolean,
        expectedNumber: String? = null,
    ) {
        if (expectedNumber != null && !matchesCurrentNumber(expectedNumber)) return
        preferences.edit()
            .putString(KEY_LOOKUP_STATUS, CallLookupStatus.FAILURE.name)
            .putInt(KEY_RESULT_POLICY_REVISION, SearchResultPolicy.REVISION)
            .remove(KEY_RESULT_TITLE)
            .remove(KEY_RESULT_SNIPPET)
            .remove(KEY_SEARCH_RESULTS)
            .putString(KEY_ERROR_MESSAGE, message)
            .putBoolean(KEY_REQUIRES_VERIFICATION, requiresVerification)
            .putBoolean(KEY_RESULT_FROM_CACHE, false)
            .apply()
    }

    fun latest(): CallEventSnapshot? {
        val receivedAt = preferences.getLong(KEY_RECEIVED_AT, 0L)
        if (receivedAt <= 0L) return null
        val snapshot = CallEventSnapshot(
            number = preferences.getString(KEY_NUMBER, "").orEmpty(),
            receivedAtMillis = receivedAt,
            popupOpenedAtMillis = preferences.getLong(KEY_POPUP_OPENED_AT, 0L),
            notificationPosted = preferences.getBoolean(KEY_NOTIFICATION_POSTED, false),
            lookupStatus = runCatching {
                CallLookupStatus.valueOf(
                    preferences.getString(KEY_LOOKUP_STATUS, CallLookupStatus.LOADING.name).orEmpty(),
                )
            }.getOrDefault(CallLookupStatus.LOADING),
            resultTitle = preferences.getString(KEY_RESULT_TITLE, null),
            resultSnippet = preferences.getString(KEY_RESULT_SNIPPET, null),
            errorMessage = preferences.getString(KEY_ERROR_MESSAGE, null),
            requiresVerification = preferences.getBoolean(KEY_REQUIRES_VERIFICATION, false),
            resultFromCache = preferences.getBoolean(KEY_RESULT_FROM_CACHE, false),
            searchResults = runCatching {
                SearchResultsCodec.decode(JSONArray(preferences.getString(KEY_SEARCH_RESULTS, "[]")))
            }.getOrDefault(emptyList()),
        )
        val storedRevision = runCatching { preferences.getInt(KEY_RESULT_POLICY_REVISION, 0) }
            .getOrDefault(0)
        return SearchResultPolicy.visibleEvent(snapshot, storedRevision)
    }

    fun registerListener(onChanged: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChanged() }
            .also(preferences::registerOnSharedPreferenceChangeListener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun matchesCurrentNumber(rawNumber: String): Boolean =
        PhoneNumberNormalizer.normalize(rawNumber) == preferences.getString(KEY_NUMBER, "")

    companion object {
        private const val PREFERENCES_NAME = "call_event_diagnostics"
        private const val KEY_NUMBER = "number"
        private const val KEY_RECEIVED_AT = "received_at"
        private const val KEY_POPUP_OPENED_AT = "popup_opened_at"
        private const val KEY_NOTIFICATION_POSTED = "notification_posted"
        private const val KEY_LOOKUP_STATUS = "lookup_status"
        private const val KEY_RESULT_POLICY_REVISION = "result_policy_revision"
        private const val KEY_RESULT_TITLE = "result_title"
        private const val KEY_RESULT_SNIPPET = "result_snippet"
        private const val KEY_SEARCH_RESULTS = "search_results"
        private const val KEY_ERROR_MESSAGE = "error_message"
        private const val KEY_REQUIRES_VERIFICATION = "requires_verification"
        private const val KEY_RESULT_FROM_CACHE = "result_from_cache"
    }
}
