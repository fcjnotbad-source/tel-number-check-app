package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult

/** Formats only text returned by searches; it never infers a location from a phone number. */
object SearchResultPresentation {
    private val punctuation = Regex("[\\p{P}\\p{Z}\\s]+")
    private val genericTitle = Regex("(号码|手机|电话|电话号码|手机号码|归属地|查询|结果|信息|搜索|大全|在线|免费|工具)+")
    private val unavailable = Regex(
        "没有找到.{0,8}(相关|匹配|搜索)?结果|未找到.{0,8}(相关|匹配|搜索)?结果|" +
            "(未查询到|未查到|未搜索到|查询不到).{0,12}(结果|信息|记录|数据)|" +
            "暂无相关信息|暂无结果|暂无记录|暂无标记|搜索结果没有摘要|暂无摘要|无摘要|" +
            "没有可显示的摘要|搜索超时|查询超时|未返回结果|no\\s+results?\\s+found",
        RegexOption.IGNORE_CASE,
    )
    private val blocked = Regex("安全验证|访问验证|人机验证|请输入验证码|访问过于频繁|captcha", RegexOption.IGNORE_CASE)
    private val factStart = Regex(
        "(?:中国)?(?:移动|联通|电信|广电)|疑似|涉嫌|可能|未被|未标记|不是|非骚扰|" +
        "曾被|被.{0,12}标记|(?:已有|有)?\\s*\\d+\\s*(?:位|个)?(?:人|用户).{0,6}(?:标记|举报)|" +
            "广告|推销|营销|骚扰|诈骗|催收|快递|外卖|客服|正常",
    )
    private val business = Regex("公司|集团|有限|企业|书店|花店|商店|门店|银行|保险|酒店|科技|商行|营业|工作室|服务中心")
    private val locationBoundary = Regex("^[\\p{P}\\p{Z}\\s]")
    private val addressContinuation = Regex("^(?:[\\p{IsHan}]{0,10}(?:区|县|镇|乡|街|路|村|巷)|\\d+号)")
    private val qualifiers = Regex("无法确认|不一定|不确定|未被|未曾|尚未|未|没有|不是|非|否认|不属于|" +
        "已经|已被|已|曾经|曾被|曾|当前|目前|疑似|涉嫌|可能|或许|正常|安全|无|不")
    private val edgeCharacters = "，,。;；:：·|、-—"

    fun summarize(phoneNumber: String, results: List<BaiduSearchResult>): String {
        val blocks = linkedMapOf<String, Block>()
        results.forEach { raw ->
            // Also clean stored structured results, which can reach the UI without aggregation.
            val visible = SearchResultSanitizer.sanitize(raw, phoneNumber)
            var snippet = cleanText(phoneNumber, visible.snippet)
            val title = meaningfulTitle(cleanText(phoneNumber, visible.title))
            if (blocked.containsMatchIn(snippet) || blocked.containsMatchIn(title)) return@forEach
            if (snippet.isBlank() && title.isBlank()) return@forEach

            // Business titles give their snippets a scope: identical hours must not erase identities.
            val scoped = title.isNotBlank() && parseLocation(title) == null &&
                canonical(title) != canonical(snippet)
            if (scoped) {
                val block = blocks.getOrPut("title:${canonical(title)}") { Block(title, scoped = true) }
                val detail = snippet.removePrefix(title).trim { it.isWhitespace() || it in edgeCharacters }
                if (detail.isNotBlank() && canonical(detail) != canonical(title)) block.add(detail)
                return@forEach
            }
            if (snippet.isBlank()) snippet = title
            val location = parseLocation(snippet, numberService = visible.isNumberService)
            if (location != null) {
                val block = blocks.getOrPut("place:${location.key}") { Block(location.display) }
                if (location.remaining.isNotBlank()) block.add(location.remaining)
            } else {
                blocks.getOrPut("plain") { Block("") }.add(snippet)
            }
        }

        // A separate location-only hit is redundant when that exact location already heads a block.
        val plain = blocks["plain"]
        if (plain != null) {
            val knownLocations = blocks.filterKeys { it.startsWith("place:") }.keys.map { it.removePrefix("place:") }.toSet()
            val locatedBlocks = blocks.filterKeys { it.startsWith("place:") }.values
            val knownFacts = blocks.filterKeys { it != "plain" }.values.flatMap { it.facts + it.heading }
            plain.facts.removeAll { fact -> locationKey(fact) in knownLocations || knownFacts.any { safeContains(it, fact) } }
            if (locatedBlocks.size == 1) {
                plain.facts.forEach(locatedBlocks.single()::add)
                blocks.remove("plain")
            }
        }
        val placeBlocks = blocks.filterKeys { it.startsWith("place:") }
        val uniquePlace = placeBlocks.values.singleOrNull()?.heading
        blocks.forEach { (key, block) ->
            val heading = when {
                key.startsWith("place:") -> block.heading
                block.scoped -> uniquePlace
                else -> null
            } ?: return@forEach
            val facts = block.facts.toList()
            block.facts.clear()
            facts.map { RepeatedLocationText.clean(it, heading) }
                .filter(String::isNotBlank)
                .forEach(block::add)
        }
        val rendered = blocks.values.mapNotNull(Block::render)
        return if (rendered.size <= 1) rendered.firstOrNull().orEmpty() else rendered.joinToString("\n")
    }

    fun summarizeText(phoneNumber: String, text: String): String = summarize(
        phoneNumber,
        // Legacy diagnostics contain one flattened summary: a cutoff removes the entire tail,
        // not just its first line, otherwise later paragraphs of the explanation reappear.
        SearchResultSanitizer.truncate(text).lineSequence().filter(String::isNotBlank).map {
            BaiduSearchResult("号码查询", it, null)
        }.toList(),
    )

    private fun cleanText(phoneNumber: String, text: String): String {
        val number = PhoneNumberNormalizer.normalize(phoneNumber)
        val withoutNumber = SearchResultSanitizer.removeQueriedNumber(
            SearchResultSanitizer.truncate(text), number,
        )
        return unavailable.replace(withoutNumber, "")
            .replace(Regex("\\s+"), " ")
            .trim { it.isWhitespace() || it in edgeCharacters }
    }

    private fun meaningfulTitle(title: String): String {
        val compact = canonical(title)
        if (compact.isBlank() || genericTitle.matches(compact)) return ""
        return title.replace(Regex("\\s*[-—|｜:：]\\s*(?:手机|电话|号码|归属地|信息|在线|免费)*(?:查询|查询结果)$"), "")
            .trim()
    }

    private data class Location(val key: String, val display: String, val remaining: String)

    private fun parseLocation(text: String, numberService: Boolean = false): Location? {
        val value = text.removePrefix("归属地：").removePrefix("归属地:").trim()
        val match = AdministrativeDivisionIndex.findPrefix(value) ?: return null
        val suffix = value.substring(match.endExclusive)
        val remaining = suffix.trim { it.isWhitespace() || it in edgeCharacters }
        // Curated service fields can put a business label after the separate location field.
        // Untyped text still must not turn a company/address prefix into inferred metadata.
        if (business.containsMatchIn(remaining) &&
            (!numberService || !locationBoundary.containsMatchIn(suffix))
        ) return null
        if (addressContinuation.containsMatchIn(remaining)) return null
        if (suffix.isNotEmpty() && !locationBoundary.containsMatchIn(suffix) &&
            factStart.find(suffix)?.range?.first != 0
        ) return null
        return Location(match.place.key, match.place.displayName, normalizeCarrier(remaining))
    }

    private fun locationKey(text: String): String = AdministrativeDivisionIndex.resolveExact(text)?.key
        ?: canonical(text)

    private fun normalizeCarrier(text: String): String = text.replace(
        Regex("^(?:中国)?(移动|联通|电信|广电)(?=$|\\s|[，,。;；:：·|、]|客服)"),
    ) { "中国${it.groupValues[1]}" }

    private fun canonical(text: String): String = punctuation.replace(text.lowercase(), "")
        .replace("中国移动", "移动").replace("中国联通", "联通")
        .replace("中国电信", "电信").replace("中国广电", "广电")

    private fun safeContains(longer: String, shorter: String): Boolean {
        val longKey = canonical(longer)
        val shortKey = canonical(shorter)
        if (longKey == shortKey) return true
        if (shortKey.length < 2 || !longKey.contains(shortKey)) return false
        val shortCarrier = Regex("^(?:中国)?(移动|联通|电信|广电)$").matchEntire(shortKey)
        if (shortCarrier != null) {
            val brand = shortCarrier.groupValues[1]
            // “电信诈骗” describes a fraud category, not the 中国电信 carrier.
            val brandDescription = Regex("^(?:中国)?$brand(?:客服|用户|号码|运营商|营业厅|业务|信号).*")
            if (!brandDescription.matches(punctuation.replace(longer, ""))) return false
        }
        if (qualifiers.findAll(longKey).map { it.value }.toSet() != qualifiers.findAll(shortKey).map { it.value }.toSet()) return false
        val counts = Regex("\\d+|[零〇一二两三四五六七八九十百千万]+(?=[人位个次])")
        val longNumbers = counts.findAll(longKey).map { it.value }.toSet()
        val shortNumbers = counts.findAll(shortKey).map { it.value }.toSet()
        return longNumbers.isEmpty() || shortNumbers.isEmpty() || longNumbers == shortNumbers
    }

    private data class Block(val heading: String, val scoped: Boolean = false, val facts: MutableList<String> = mutableListOf()) {
        fun add(value: String) {
            if (facts.any { safeContains(it, value) }) return
            val firstReplaced = facts.indexOfFirst { safeContains(value, it) }
            facts.removeAll { safeContains(value, it) }
            if (firstReplaced < 0) facts += value else facts.add(firstReplaced.coerceAtMost(facts.size), value)
        }

        fun render(): String? {
            val cleaned = OrderedWhitespaceFacts.clean(listOf(heading) + facts)
            val visibleHeading = cleaned.first()
            val visibleFacts = cleaned.drop(1).filter(String::isNotBlank)
            if (visibleHeading.isBlank()) return visibleFacts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
            if (visibleFacts.isEmpty()) return visibleHeading
            return if (scoped) "$visibleHeading：${visibleFacts.joinToString(" · ")}" else (listOf(visibleHeading) + visibleFacts).joinToString(" · ")
        }
    }
}
