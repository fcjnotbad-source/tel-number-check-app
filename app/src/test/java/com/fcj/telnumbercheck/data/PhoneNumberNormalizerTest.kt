package com.fcj.telnumbercheck.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberNormalizerTest {
    @Test
    fun removesMainlandChinaCountryCode() {
        assertEquals("10000000002", PhoneNumberNormalizer.normalize("+86 100-0000-0002"))
        assertEquals("10000000002", PhoneNumberNormalizer.normalize("008610000000002"))
        assertEquals("10000000002", PhoneNumberNormalizer.normalize("8610000000002"))
    }

    @Test
    fun keepsLandlineDigits() {
        assertEquals("01000000001", PhoneNumberNormalizer.normalize("tel:010-00000001"))
    }

    @Test
    fun formatsMainlandMobileNumber() {
        assertEquals("100 0000 0002", PhoneNumberNormalizer.display("+8610000000002"))
    }
}
