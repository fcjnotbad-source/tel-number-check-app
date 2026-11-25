package com.fcj.telnumbercheck.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.fcj.telnumbercheck.domain.BaiduLookupState

@Composable
fun BaiduWebLookup(
    phoneNumber: String,
    requestId: Long,
    onState: (BaiduLookupState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnState by rememberUpdatedState(onState)
    var lookupView by remember { mutableStateOf<BaiduLookupWebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            BaiduLookupWebView(context).also { view ->
                lookupView = view
                view.onState = { latestOnState(it) }
            }
        },
        update = { view ->
            view.onState = { latestOnState(it) }
            view.search(phoneNumber, requestId)
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            lookupView?.stopLoading()
            lookupView?.destroy()
            lookupView = null
        }
    }
}
