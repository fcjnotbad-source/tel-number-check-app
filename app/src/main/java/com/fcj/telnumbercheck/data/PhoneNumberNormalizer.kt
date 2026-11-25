package com.fcj.telnumbercheck.data

object PhoneNumberNormalizer {
    fun normalize(raw: String): String {
        val stripped = raw
            .removePrefix("tel:")
            .filter { it.isDigit() || it == '+' }

        return when {
            stripped.startsWith("+86") -> stripped.drop(3)
            stripped.startsWith("0086") -> stripped.drop(4)
            stripped.startsWith("86") && stripped.length == 13 -> stripped.drop(2)
            else -> stripped.removePrefix("+")
        }
    }

    fun display(raw: String): String {
        val number = normalize(raw)
        return if (number.length == 11 && number.startsWith('1')) {
            "${number.take(3)} ${number.substring(3, 7)} ${number.takeLast(4)}"
        } else {
            number
        }
    }
}
