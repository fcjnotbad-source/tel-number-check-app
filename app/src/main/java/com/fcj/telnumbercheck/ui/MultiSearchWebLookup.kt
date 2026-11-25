package com.fcj.telnumbercheck.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.fcj.telnumbercheck.domain.MultiSearchLookupState
import com.fcj.telnumbercheck.search.MultiEngineLookupView

@Composable
fun MultiSearchWebLookup(
    phoneNumber: String,
    requestId: Long,
    onState: (MultiSearchLookupState) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> MultiEngineLookupView(context) },
        update = { view ->
            view.onState = onState
            view.search(phoneNumber, requestId)
        },
        onRelease = { it.dispose() },
    )
}
