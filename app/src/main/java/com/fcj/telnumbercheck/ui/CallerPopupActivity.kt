package com.fcj.telnumbercheck.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fcj.telnumbercheck.TelNumberCheckApp
import com.fcj.telnumbercheck.call.IncomingCallAlert
import com.fcj.telnumbercheck.call.PendingCallerPopupStore
import com.fcj.telnumbercheck.data.CallEventStore
import com.fcj.telnumbercheck.data.PhoneNumberNormalizer
import com.fcj.telnumbercheck.domain.MultiSearchLookupState
import com.fcj.telnumbercheck.ui.theme.TelNumberCheckTheme
import kotlinx.coroutines.delay

class CallerPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()

        val rawNumber = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val normalizedNumber = PhoneNumberNormalizer.normalize(rawNumber)
        val fromIncomingCall = intent.getBooleanExtra(EXTRA_FROM_INCOMING_CALL, false)
        val callEventStore = CallEventStore(this)
        val app = application as TelNumberCheckApp
        if (fromIncomingCall) {
            IncomingCallAlert(this).cancel()
        }
        val repository = app.phoneLookupRepository
        val initialInfo = repository.cached(normalizedNumber) ?: repository.pending(normalizedNumber)
        setContent {
            TelNumberCheckTheme {
                var info by remember { mutableStateOf(initialInfo) }
                var lookupNumber by remember {
                    mutableStateOf(if (initialInfo.isFromCache) "" else normalizedNumber)
                }
                var lookupRequestId by remember { mutableStateOf(1L) }
                var loading by remember { mutableStateOf(!initialInfo.isFromCache) }
                LaunchedEffect("auto-dismiss") {
                    delay(AUTO_DISMISS_MILLIS)
                    finish()
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 14.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.82f).dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("来电识别", fontWeight = FontWeight.Bold)
                            IconButton(onClick = ::finish) {
                                Icon(Icons.Rounded.Close, contentDescription = "关闭")
                            }
                        }
                        if (lookupNumber.isNotBlank()) {
                            val requestedNumber = lookupNumber
                            val requestedId = lookupRequestId
                            MultiSearchWebLookup(
                                phoneNumber = requestedNumber,
                                requestId = requestedId,
                                onState = { state ->
                                    if (lookupNumber != requestedNumber || lookupRequestId != requestedId) {
                                        return@MultiSearchWebLookup
                                    }
                                    when (state) {
                                        MultiSearchLookupState.Loading -> loading = true
                                        is MultiSearchLookupState.Partial -> {
                                            val partialInfo = repository.withResults(
                                                requestedNumber,
                                                state.results,
                                                cacheResult = false,
                                            )
                                            info = partialInfo
                                            if (fromIncomingCall) callEventStore.recordLookupSuccess(partialInfo)
                                        }
                                        is MultiSearchLookupState.Success -> {
                                            val resolvedInfo = repository.withResults(requestedNumber, state.results)
                                            info = resolvedInfo
                                            if (fromIncomingCall) {
                                                callEventStore.recordLookupSuccess(resolvedInfo)
                                            }
                                            loading = false
                                            lookupNumber = ""
                                        }
                                        is MultiSearchLookupState.Failure -> {
                                            info = repository.withError(
                                                requestedNumber,
                                                state.message,
                                                state.requiresVerification,
                                            )
                                            if (fromIncomingCall) {
                                                callEventStore.recordLookupFailure(
                                                    state.message,
                                                    state.requiresVerification,
                                                    expectedNumber = requestedNumber,
                                                )
                                            }
                                            loading = false
                                            lookupNumber = ""
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .alpha(0.01f),
                            )
                        }
                        if (loading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        CallerInfoCard(info)
                        if (!loading) {
                            OutlinedButton(
                                onClick = {
                                    lookupNumber = info.normalizedNumber
                                    lookupRequestId += 1
                                    loading = true
                                    info = repository.pending(info.normalizedNumber)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("重新查询")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (info.baiduUrl != null && !info.requiresVerification) {
                                        BaiduSearch.openResult(this@CallerPopupActivity, info.baiduUrl)
                                    } else {
                                        BaiduSearch.open(this@CallerPopupActivity, info.normalizedNumber)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    when {
                                        info.requiresVerification -> "完成验证"
                                        info.baiduUrl != null -> "打开结果"
                                        else -> "打开搜索页"
                                    },
                                )
                            }
                            Button(
                                onClick = {
                                    if (info.isLocallyMarked) {
                                        repository.clearLocalLabel(info.normalizedNumber)
                                        Toast.makeText(this@CallerPopupActivity, "已清除标记", Toast.LENGTH_SHORT).show()
                                    } else {
                                        repository.markAsSpam(info.normalizedNumber)
                                        Toast.makeText(this@CallerPopupActivity, "已标记为疑似骚扰", Toast.LENGTH_SHORT).show()
                                    }
                                    info = repository.refreshLocalLabel(info)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (info.isLocallyMarked) "清除标记" else "标记骚扰")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as TelNumberCheckApp).callerPopupVisibility.setVisible(true)
        if (intent.getBooleanExtra(EXTRA_FROM_INCOMING_CALL, false)) {
            val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
            CallEventStore(this).recordPopupOpened(number)
            (application as TelNumberCheckApp).incomingCallLookupCoordinator.cancelForVisiblePopup(number)
            PendingCallerPopupStore(this).clear(number)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onPause() {
        (application as TelNumberCheckApp).callerPopupVisibility.setVisible(false)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    private fun configureWindow() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.statusBarColor = Color.TRANSPARENT
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setGravity(Gravity.CENTER)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        )
        window.attributes = window.attributes.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.CENTER
        }
    }

    companion object {
        private const val AUTO_DISMISS_MILLIS = 30_000L
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_FROM_INCOMING_CALL = "from_incoming_call"

        fun intent(
            context: Context,
            rawNumber: String,
            fromIncomingCall: Boolean = false,
        ): Intent =
            Intent(context, CallerPopupActivity::class.java).apply {
                putExtra(EXTRA_NUMBER, rawNumber)
                putExtra(EXTRA_FROM_INCOMING_CALL, fromIncomingCall)
                if (fromIncomingCall) {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    )
                }
            }
    }
}
