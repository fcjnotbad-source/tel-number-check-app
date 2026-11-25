package com.fcj.telnumbercheck

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.fcj.telnumbercheck.call.CallerPopupWakeReceiver
import com.fcj.telnumbercheck.call.IncomingCallAlert
import com.fcj.telnumbercheck.call.IncomingCallLookupCoordinator
import com.fcj.telnumbercheck.data.PhoneLookupRepository
import com.fcj.telnumbercheck.factory.AppUpdateChecker
import com.fcj.telnumbercheck.ui.CallerPopupVisibility

class TelNumberCheckApp : Application() {
    private val callerPopupWakeReceiver = CallerPopupWakeReceiver()
    val callerPopupVisibility = CallerPopupVisibility()
    val phoneLookupRepository by lazy { PhoneLookupRepository(this) }
    val appUpdateChecker by lazy { AppUpdateChecker() }
    val incomingCallLookupCoordinator by lazy {
        IncomingCallLookupCoordinator(this, phoneLookupRepository)
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            callerPopupWakeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        IncomingCallAlert(this).createChannel()
    }
}
