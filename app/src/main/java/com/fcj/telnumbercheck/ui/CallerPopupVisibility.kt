package com.fcj.telnumbercheck.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CallerPopupVisibility {
    private val mutableVisible = MutableStateFlow(false)
    val visible = mutableVisible.asStateFlow()

    fun setVisible(visible: Boolean) {
        mutableVisible.value = visible
    }
}
