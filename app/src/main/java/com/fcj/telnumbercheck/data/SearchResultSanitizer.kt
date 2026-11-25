package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult

object SearchResultSanitizer {
    private val cutoffMarker = Regex("(?<!\\d)12321(?!\\d)")
    private val explanationMarker = Regex(
        "(?:这(?:个|一)?|该|本)?(?:电话号码段|电话号码|号码段|号段|号码)\\s*" +
            "(?:到底|究竟)?\\s*是\\s*(?:啥|什么)|(?:到底|究竟)?\\s*是\\s*什么",
    )
    private val trailingSeparators = setOf('，', ',', '。', '；', ';', '、', '|', '·', '-', '—', ':', '：')

    fun sanitize(result: BaiduSearchResult, phoneNumber: String): BaiduSearchResult {
        // A question title scopes its whole snippet to the excluded explanation, even when
        // that snippet starts directly with carrier/customer-service keywords.
        val snippet = if (explanationMarker.containsMatchIn(result.title)) "" else truncate(result.snippet)
        return result.copy(
            title = truncate(result.title).ifBlank { "号码查询结果" },
            snippet = removeQueriedNumber(snippet, phoneNumber).ifBlank { "没有可显示的摘要" },
        )
    }

    fun truncate(text: String): String {
        val cutoff = listOfNotNull(cutoffMarker.find(text), explanationMarker.find(text))
            .minOfOrNull { it.range.first }
        val visibleText = if (cutoff == null) text else text.substring(0, cutoff)
        return visibleText.trim().trimEnd { it.isWhitespace() || it in trailingSeparators }
    }

    fun removeQueriedNumber(text: String, phoneNumber: String): String {
        val digits = phoneNumber.filter(Char::isDigit)
        if (digits.isBlank()) return text

        val separator = "[\\s\\-()（）]*"
        val phonePattern = digits
            .map { Regex.escape(it.toString()) }
            .joinToString(separator = separator)
        val phoneRegex = Regex("(?<!\\d)(?:(?:\\+?86|0086)$separator)?$phonePattern(?!\\d)")
        return phoneRegex.replace(text, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd { it.isWhitespace() || it in trailingSeparators }
    }
}
