package com.fcj.telnumbercheck.call

import android.content.Context
import com.fcj.telnumbercheck.data.PhoneNumberNormalizer

data class PendingCallerPopup(
    val number: String,
    val receivedAtMillis: Long,
)

class PendingCallerPopupStore(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(rawNumber: String) {
        val number = PhoneNumberNormalizer.normalize(rawNumber)
        if (number.isBlank()) return
        preferences.edit()
            .putString(KEY_NUMBER, number)
            .putLong(KEY_RECEIVED_AT, nowMillis())
            .apply()
    }

    fun fresh(): PendingCallerPopup? {
        val number = preferences.getString(KEY_NUMBER, "").orEmpty()
        val receivedAt = preferences.getLong(KEY_RECEIVED_AT, 0L)
        if (!PendingCallerPopupPolicy.isFresh(receivedAt, nowMillis(), MAX_AGE_MILLIS)) {
            clear()
            return null
        }
        return PendingCallerPopup(number = number, receivedAtMillis = receivedAt)
            .takeIf { it.number.isNotBlank() }
    }

    fun clear(rawNumber: String? = null) {
        val expectedNumber = rawNumber?.let(PhoneNumberNormalizer::normalize)
        if (
            expectedNumber != null &&
            expectedNumber != preferences.getString(KEY_NUMBER, "").orEmpty()
        ) {
            return
        }
        preferences.edit().clear().apply()
    }

    companion object {
        const val MAX_AGE_MILLIS = 60_000L
        private const val PREFERENCES_NAME = "pending_caller_popup"
        private const val KEY_NUMBER = "number"
        private const val KEY_RECEIVED_AT = "received_at"
    }
}

internal object PendingCallerPopupPolicy {
    fun isFresh(receivedAtMillis: Long, nowMillis: Long, maxAgeMillis: Long): Boolean =
        receivedAtMillis > 0L &&
            receivedAtMillis <= nowMillis &&
            nowMillis - receivedAtMillis <= maxAgeMillis
}
