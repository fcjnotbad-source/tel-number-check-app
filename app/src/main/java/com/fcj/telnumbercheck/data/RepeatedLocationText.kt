package com.fcj.telnumbercheck.data

/** Removes only a dictionary-validated location already shown in this result's heading. */
internal object RepeatedLocationText {
    private val wordBoundary = Regex("[\\p{P}\\p{Z}\\s]")
    private val spaces = Regex("\\s+")
    private val edges = "，,。;；:：·|、-—()（）[]【】"
    private val company = Regex("公司|集团|企业|银行|书店|花店|商店|酒店|营业厅|服务中心|工作室")
    private val address = Regex("区|县|镇|乡|街|路|村|园|巷|弄|大厦|广场|\\d+\\s*号")
    private val addressContext = Regex("地址|住址|位于|坐落|所在地|办公|门店|分店|注册")
    private val qualification = Regex("并非|不是|不在|不属于|没有|未|无|非|不|疑似|可能|或许|不确定|无法确认")
    private val qualifiedTemplate = Regex("^(可能|或许|并非|不是|不|无|未|没有|有误|仅供|是否|存在)")
    private val repeatedSeparators = Regex("[,，;；:：·|、—-]+(?:\\s*[,，;；:：·|、—-])+")
    private const val country = "(?:中华人民共和国|中国)"

    fun clean(text: String, heading: String): String {
        val place = AdministrativeDivisionIndex.resolveExact(heading) ?: return text
        // Service cards can append/prepend the country without a separator, e.g.
        // 广东深圳中国. Consume only a complete country field, never 中国联通/中国银行.
        val location = Regex(
            "(?:$country${AdministrativeDivisionIndex.SEPARATORS})?${place.pattern.pattern}" +
                "(?:${AdministrativeDivisionIndex.SEPARATORS}$country(?=$|[\\p{P}\\p{Z}\\s]))?",
        )

        val withoutBoilerplate = removeBoilerplate(text)
        val cleaned = location.replace(withoutBoilerplate) { match ->
            val before = withoutBoilerplate.substring(0, match.range.first)
            val after = withoutBoilerplate.substring(match.range.last + 1)
            val leftBoundary = before.isEmpty() || isBoundary(before.last())
            val rightBoundary = after.isEmpty() || isBoundary(after.first())
            // Keep place words inside names, full addresses, or qualified/negative claims intact.
            val context = before.takeLast(32).substringAfterLast('，').substringAfterLast('。')
            val suffix = after.take(40)
            if (!leftBoundary || !rightBoundary || qualification.containsMatchIn(context) ||
                company.containsMatchIn(context + suffix) || addressContext.containsMatchIn(context) ||
                address.containsMatchIn(suffix)
            ) match.value else " "
        }
        return tidy(cleaned)
    }

    private fun removeBoilerplate(text: String): String {
        if (company.containsMatchIn(text)) return text
        return Regex("网络收录数据").replace(text) { match ->
            val before = text.substring(0, match.range.first)
            val after = text.substring(match.range.last + 1)
            val independent = (before.isEmpty() || isBoundary(before.last())) &&
                (after.isEmpty() || isBoundary(after.first()))
            if (!independent || qualification.containsMatchIn(before.takeLast(16)) ||
                qualifiedTemplate.containsMatchIn(after.trimStart())
            ) match.value else " "
        }
    }

    private fun isBoundary(character: Char): Boolean = wordBoundary.matches(character.toString())
    private fun tidy(text: String): String = repeatedSeparators.replace(spaces.replace(text, " "), " · ")
        .trim { it.isWhitespace() || it in edges }
}
