package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult

/** Keeps useful answers while retaining conflicting claims from different search engines. */
object SearchResultAggregator {
    private val separators = Regex("[\\p{P}\\p{Z}\\s]+")
    private val blockedPage = Regex(
        "安全验证|访问验证|人机验证|验证您是真人|请输入验证码|完成验证后|访问过于频繁|" +
            "异常流量|访问受限|请求被拦截|access\\s*denied|captcha|verify\\s*(you|that)",
        RegexOption.IGNORE_CASE,
    )
    private val noAnswer = Regex(
        "没有找到.{0,8}(相关|匹配|搜索)?结果|未找到.{0,8}(相关|匹配|搜索)?结果|" +
            "没有.{0,4}(搜索结果|相关信息)|暂无.{0,4}(结果|相关信息|记录|标记)|" +
            "(未查询到|未查到|未搜索到|查询不到).{0,12}(结果|信息|记录|数据)|" +
            "搜索结果没有摘要|暂无摘要|无摘要|没有可显示的摘要|" +
            "搜索超时|查询超时|未返回结果|no\\s+results?\\s+found",
        RegexOption.IGNORE_CASE,
    )
    private val queryTemplate = Regex(
        "请输入.{0,10}(手机|电话|号码)|输入.{0,8}号码.{0,8}(查询|搜索)|" +
            "支持.{0,12}(号码归属地|手机号码)查询|查询.{0,8}(是否为|是不是).{0,6}骚扰|" +
            "免费.{0,5}(手机|电话)?号码.{0,5}查询工具|" +
            "(如何|怎么|怎样).{0,8}(查询|识别|辨别).{0,8}(号码|电话|骚扰)",
    )
    private val answerSignals = Regex(
        "中国移动|中国联通|中国电信|中国广电|虚拟运营商|" +
            "北京|天津|上海|重庆|河北|山西|辽宁|吉林|黑龙江|江苏|浙江|安徽|福建|江西|" +
            "山东|河南|湖北|湖南|广东|海南|四川|贵州|云南|陕西|甘肃|青海|台湾|" +
            "内蒙古|广西|西藏|宁夏|新疆|香港|澳门|" +
            "骚扰|诈骗|推销|营销|催收|中介|贷款|快递|外卖|客服|服务热线|举报|标记",
    )
    private val classificationSignals = Regex(
        "骚扰|诈骗|推销|营销|催收|中介|贷款|快递|外卖|客服|正常|安全|" +
            "无标记|未标记|未被标记|不是|非骚扰|移动|联通|电信|广电",
    )
    private val genericTitle = Regex(
        "(号码|手机|电话|电话号码|手机号码|归属地|查询|结果|信息|搜索|大全|在线|免费|工具)+",
    )

    fun merge(phoneNumber: String, results: List<BaiduSearchResult>): List<BaiduSearchResult> {
        val merged = mutableListOf<BaiduSearchResult>()
        results.forEach { rawResult ->
            val sanitized = SearchResultSanitizer.sanitize(rawResult, phoneNumber)
            val result = sanitized.copy(
                snippet = noAnswer.replace(sanitized.snippet, "")
                    .trim { it.isWhitespace() || it in "，,。;；:：·|" },
            )
            if (!isUseful(phoneNumber, rawResult, result)) return@forEach

            val duplicateIndex = merged.indexOfFirst { equivalent(it, result) }
            if (duplicateIndex < 0) {
                merged += result
            } else {
                val previous = merged[duplicateIndex]
                val richer = if (answerKey(result).length > answerKey(previous).length) result else previous
                merged[duplicateIndex] = richer.copy(sources = previous.sources + result.sources)
            }
        }
        return merged
    }

    private fun isUseful(
        phoneNumber: String,
        raw: BaiduSearchResult,
        clean: BaiduSearchResult,
    ): Boolean {
        val originalText = "${raw.title} ${raw.snippet}"
        if (blockedPage.containsMatchIn(originalText)) return false

        val title = SearchResultSanitizer.removeQueriedNumber(clean.title, phoneNumber).trim()
        val snippet = clean.snippet.takeUnless { noAnswer.containsMatchIn(it) }.orEmpty()
        val content = "$title $snippet".trim()
        if (content.isBlank() || queryTemplate.containsMatchIn(content)) return false
        if (noAnswer.containsMatchIn(content)) return false

        val answer = normalized(snippet)
        // Verified service cards may identify a short business name without telecom keywords.
        // Do not extend this exception to ordinary web snippets or legacy untyped results.
        if (raw.isNumberService && answer.length >= 2 && !genericTitle.matches(answer)) return true
        if (answerSignals.containsMatchIn(content) && (answer.length >= 4 || normalized(title).length >= 4)) {
            return true
        }

        // A business/address result may have no telecom keywords, but must identify this number.
        val withoutNumber = SearchResultSanitizer.removeQueriedNumber(originalText, phoneNumber)
        val hasQueriedNumber = normalized(withoutNumber) != normalized(originalText)
        val meaningfulTitle = normalized(title).takeUnless { genericTitle.matches(it) }.orEmpty()
        return hasQueriedNumber && (answer.length >= 8 || meaningfulTitle.length >= 4)
    }

    private fun equivalent(first: BaiduSearchResult, second: BaiduSearchResult): Boolean {
        val firstTitle = specificTitleKey(first.title)
        val secondTitle = specificTitleKey(second.title)
        if (firstTitle.isNotBlank() && secondTitle.isNotBlank() &&
            !firstTitle.contains(secondTitle) && !secondTitle.contains(firstTitle)
        ) return false
        val firstKey = answerKey(first)
        val secondKey = answerKey(second)
        if (firstKey == secondKey) return true
        val shorter = if (firstKey.length <= secondKey.length) firstKey else secondKey
        val longer = if (firstKey.length <= secondKey.length) secondKey else firstKey
        if (shorter.length < 6 || !longer.contains(shorter)) return false

        val firstClaims = classificationSignals.findAll(firstKey).map { it.value }.toSet()
        val secondClaims = classificationSignals.findAll(secondKey).map { it.value }.toSet()
        return firstClaims == secondClaims
    }

    private fun answerKey(result: BaiduSearchResult): String = normalized(
        result.snippet.takeUnless { noAnswer.containsMatchIn(it) }.orEmpty().ifBlank { result.title },
    )

    private fun specificTitleKey(title: String): String = normalized(title)
        .replace(Regex("\\d+"), "")
        .takeUnless { it.isBlank() || genericTitle.matches(it) }
        .orEmpty()

    private fun normalized(text: String): String = separators.replace(text.lowercase(), "")
        .replace("中国移动", "移动")
        .replace("中国联通", "联通")
        .replace("中国电信", "电信")
        .replace("中国广电", "广电")
}
