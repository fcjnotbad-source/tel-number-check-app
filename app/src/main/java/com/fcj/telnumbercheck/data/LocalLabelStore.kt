package com.fcj.telnumbercheck.data

import android.content.Context

class LocalLabelStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_number_labels", Context.MODE_PRIVATE)

    fun get(number: String): String? = preferences.getString(number, null)

    fun put(number: String, label: String) {
        preferences.edit().putString(number, label.trim()).apply()
    }

    fun remove(number: String) {
        preferences.edit().remove(number).apply()
    }
}
