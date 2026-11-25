package com.fcj.telnumbercheck.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

internal fun highlightedSearchResult(
    text: String,
    phoneNumber: String,
    resultColor: Color,
    phoneColor: Color,
): AnnotatedString {
    val digits = phoneNumber.filter(Char::isDigit)
    if (digits.isBlank()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = resultColor)) { append(text) }
        }
    }

    val separator = "[\\s\\-()]*"
    val phonePattern = digits
        .map { Regex.escape(it.toString()) }
        .joinToString(separator = separator)
    val phoneRegex = Regex("(?<!\\d)$phonePattern(?!\\d)")

    return buildAnnotatedString {
        var cursor = 0
        phoneRegex.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                withStyle(SpanStyle(color = resultColor)) {
                    append(text.substring(cursor, match.range.first))
                }
            }
            withStyle(SpanStyle(color = phoneColor)) { append(match.value) }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            withStyle(SpanStyle(color = resultColor)) { append(text.substring(cursor)) }
        }
    }
}
