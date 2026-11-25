package com.fcj.telnumbercheck.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCallerPopupPolicyTest {
    @Test
    fun `pending popup remains fresh for sixty seconds`() {
        assertTrue(PendingCallerPopupPolicy.isFresh(1_000L, 61_000L, 60_000L))
    }

    @Test
    fun `expired popup is rejected`() {
        assertFalse(PendingCallerPopupPolicy.isFresh(1_000L, 61_001L, 60_000L))
    }

    @Test
    fun `invalid or future timestamps are rejected`() {
        assertFalse(PendingCallerPopupPolicy.isFresh(0L, 10_000L, 60_000L))
        assertFalse(PendingCallerPopupPolicy.isFresh(11_000L, 10_000L, 60_000L))
    }
}
