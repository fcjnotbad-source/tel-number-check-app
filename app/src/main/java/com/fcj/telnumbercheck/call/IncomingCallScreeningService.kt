package com.fcj.telnumbercheck.call

import android.telecom.Call
import android.telecom.CallScreeningService
import com.fcj.telnumbercheck.TelNumberCheckApp
import com.fcj.telnumbercheck.data.CallEventStore
import com.fcj.telnumbercheck.ui.CallerPopupActivity

class IncomingCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build(),
        )

        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
            if (rawNumber.isNotBlank()) {
                showCallerInformation(rawNumber)
            }
        }

    }

    private fun showCallerInformation(rawNumber: String) {
        PendingCallerPopupStore(this).save(rawNumber)
        val eventStore = CallEventStore(this)
        eventStore.recordReceived(rawNumber)
        val notificationPosted = IncomingCallAlert(this).show(rawNumber)
        eventStore.recordNotificationPosted(notificationPosted)
        (application as TelNumberCheckApp).incomingCallLookupCoordinator.start(rawNumber)

        val intent = CallerPopupActivity.intent(this, rawNumber, fromIncomingCall = true)
        runCatching { startActivity(intent) }
    }
}
