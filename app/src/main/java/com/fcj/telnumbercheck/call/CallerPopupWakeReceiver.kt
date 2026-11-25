package com.fcj.telnumbercheck.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.fcj.telnumbercheck.ui.CallerPopupActivity

class CallerPopupWakeReceiver : BroadcastReceiver() {
    private var lastAttemptElapsedMillis = 0L

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON && intent.action != Intent.ACTION_USER_PRESENT) {
            return
        }
        val elapsedMillis = SystemClock.elapsedRealtime()
        if (elapsedMillis - lastAttemptElapsedMillis < RETRY_THROTTLE_MILLIS) return

        val pending = PendingCallerPopupStore(context).fresh() ?: return
        lastAttemptElapsedMillis = elapsedMillis

        IncomingCallAlert(context).show(pending.number)
        runCatching {
            context.startActivity(
                CallerPopupActivity.intent(
                    context = context,
                    rawNumber = pending.number,
                    fromIncomingCall = true,
                ),
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to relaunch caller popup after screen wake", error)
        }
    }

    companion object {
        private const val TAG = "CallerPopupWake"
        private const val RETRY_THROTTLE_MILLIS = 1_500L
    }
}
