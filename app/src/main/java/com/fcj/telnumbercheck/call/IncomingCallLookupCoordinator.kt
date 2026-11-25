package com.fcj.telnumbercheck.call

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.fcj.telnumbercheck.TelNumberCheckApp
import com.fcj.telnumbercheck.data.CallEventStore
import com.fcj.telnumbercheck.data.PhoneLookupRepository
import com.fcj.telnumbercheck.data.PhoneNumberNormalizer
import com.fcj.telnumbercheck.domain.MultiSearchLookupState
import com.fcj.telnumbercheck.search.MultiEngineLookupView

class IncomingCallLookupCoordinator(
    context: Context,
    private val repository: PhoneLookupRepository,
) {
    private val appContext = context.applicationContext
    private val eventStore = CallEventStore(appContext)
    private val alert = IncomingCallAlert(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var webView: MultiEngineLookupView? = null
    private var windowManager: WindowManager? = null
    private var activeToken = 0L
    private var activeNumber = ""
    private var timeout: Runnable? = null
    private var attachmentRetry: Runnable? = null

    fun start(rawNumber: String) {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        if (normalized.isBlank()) return

        repository.cached(normalized)?.let { cached ->
            eventStore.recordLookupSuccess(cached)
            if (!popupIsOpen()) alert.showResult(normalized, cached)
            return
        }

        if (!Settings.canDrawOverlays(appContext)) {
            val message = "需要允许后台悬浮查询，请打开应用完成授权"
            eventStore.recordLookupFailure(message, false, expectedNumber = normalized)
            alert.showFailure(normalized, message)
            return
        }

        mainHandler.post {
            cancelActiveLookup()
            val token = System.nanoTime()
            activeToken = token
            activeNumber = normalized
            timeout = Runnable {
                if (token == activeToken) {
                    finishFailure(
                        token,
                        normalized,
                        "查询超时，请打开应用点击“重新检测此号码”",
                        false,
                    )
                }
            }.also { mainHandler.postDelayed(it, LOOKUP_TIMEOUT_MILLIS) }
            attachLookupWindowWithRetry(token, normalized, attempt = 0)
        }
    }

    fun cancelForVisiblePopup(rawNumber: String) {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val cancel = {
            if (normalized == activeNumber) cancelActiveLookup()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cancel() else mainHandler.post { cancel() }
    }

    private fun attachLookupWindowWithRetry(token: Long, number: String, attempt: Int) {
        if (token != activeToken) return
        val error = runCatching {
            attachLookupWindow(
                token = token,
                number = number,
                useDedicatedWindowContext = attempt % 2 == 0,
            )
        }.exceptionOrNull()
        if (error == null) return

        Log.w(TAG, "Background lookup window attach attempt ${attempt + 1} failed", error)
        if (attempt < ATTACH_RETRY_DELAYS_MILLIS.size) {
            attachmentRetry = Runnable {
                attachmentRetry = null
                attachLookupWindowWithRetry(token, number, attempt + 1)
            }.also {
                mainHandler.postDelayed(it, ATTACH_RETRY_DELAYS_MILLIS[attempt])
            }
        } else {
            finishFailure(
                token,
                number,
                "后台查询启动失败，请打开应用点击“重新检测此号码”",
                false,
            )
        }
    }

    private fun attachLookupWindow(
        token: Long,
        number: String,
        useDedicatedWindowContext: Boolean,
    ) {
        val windowContext = if (
            useDedicatedWindowContext && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            appContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            appContext
        }
        val manager = windowContext.getSystemService(WindowManager::class.java)
        val lookupView = MultiEngineLookupView(windowContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0f
        }
        var added = false
        try {
            manager.addView(lookupView, params)
            added = true
            windowManager = manager
            webView = lookupView
            lookupView.onState = { state -> handleState(token, number, state) }
            lookupView.search(number, token)
        } catch (error: Throwable) {
            if (added && lookupView.isAttachedToWindow) {
                runCatching { manager.removeViewImmediate(lookupView) }
            }
            if (webView === lookupView) {
                webView = null
                windowManager = null
            }
            lookupView.dispose()
            throw error
        }
    }

    private fun handleState(token: Long, number: String, state: MultiSearchLookupState) {
        if (token != activeToken) return
        when (state) {
            MultiSearchLookupState.Loading -> Unit
            is MultiSearchLookupState.Partial -> {
                val info = repository.withResults(number, state.results, cacheResult = false)
                eventStore.recordLookupSuccess(info)
            }
            is MultiSearchLookupState.Success -> {
                val info = repository.withResults(number, state.results)
                eventStore.recordLookupSuccess(info)
                if (popupIsOpen()) alert.cancel() else alert.showResult(number, info)
                finish(token)
            }
            is MultiSearchLookupState.Failure -> finishFailure(
                token = token,
                number = number,
                message = state.message,
                requiresVerification = state.requiresVerification,
            )
        }
    }

    private fun finishFailure(
        token: Long,
        number: String,
        message: String,
        requiresVerification: Boolean,
    ) {
        if (token != activeToken) return
        eventStore.recordLookupFailure(message, requiresVerification, expectedNumber = number)
        if (popupIsOpen()) alert.cancel() else alert.showFailure(number, message)
        finish(token)
    }

    private fun popupIsOpen(): Boolean =
        (appContext as TelNumberCheckApp).callerPopupVisibility.visible.value

    private fun finish(token: Long) {
        if (token != activeToken) return
        activeToken = 0L
        activeNumber = ""
        attachmentRetry?.let(mainHandler::removeCallbacks)
        attachmentRetry = null
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
        val finishedView = webView
        val finishedWindowManager = windowManager
        webView = null
        windowManager = null
        mainHandler.post {
            if (finishedView?.isAttachedToWindow == true) {
                runCatching { finishedWindowManager?.removeViewImmediate(finishedView) }
            }
            finishedView?.dispose()
        }
    }

    private fun cancelActiveLookup() {
        activeToken = 0L
        activeNumber = ""
        attachmentRetry?.let(mainHandler::removeCallbacks)
        attachmentRetry = null
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
        val cancelledView = webView
        if (cancelledView?.isAttachedToWindow == true) {
            runCatching { windowManager?.removeViewImmediate(cancelledView) }
        }
        cancelledView?.dispose()
        webView = null
        windowManager = null
    }

    companion object {
        private const val TAG = "IncomingCallLookup"
        private const val LOOKUP_TIMEOUT_MILLIS = 15_000L
        private val ATTACH_RETRY_DELAYS_MILLIS = longArrayOf(350L, 1_000L)
    }
}
