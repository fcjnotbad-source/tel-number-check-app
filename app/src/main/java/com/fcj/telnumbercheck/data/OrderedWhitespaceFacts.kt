package com.fcj.telnumbercheck.data

/** Ordered whitespace deduplication within one geographic/business context. */
internal object OrderedWhitespaceFacts {
    private val whitespace = Regex("[\\s\\p{Z}]+")
    private val factSeparator = Regex("[\\s\\p{Z}]*·[\\s\\p{Z}]*")
    private val edges = "，,。;；:：|、·"
    private val standaloneQualifiers = setOf(
        "未", "无", "不", "非", "不是", "并非", "不在", "没有", "不属于", "否认", "无法确认",
        "不一定", "不确定", "未被", "未曾", "尚未", "已经", "已被", "已", "曾经", "曾被", "曾",
        "当前", "目前", "疑似", "涉嫌", "可能", "或许", "正常", "安全",
    )
    private val qualifiedWord = Regex(
        "^(无法确认|不一定|不确定|未被|未曾|尚未|未标记|未发现|没有|不是|并非|否认|不属于|" +
            "已经|已被|曾经|曾被|疑似|涉嫌|可能|或许|非骚扰|非诈骗|无骚扰|无标记|无记录|不在|不骚扰)",
    )
    private val address = Regex("地址|住址|位于|坐落")

    fun clean(parts: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        val completeStatements = mutableSetOf<String>()
        return parts.map { part ->
            factSeparator.split(part).mapNotNull { fact ->
                val words = whitespace.split(fact.trim()).filter(String::isNotBlank)
                if (words.isEmpty()) return@mapNotNull null
                val normalized = words.joinToString(" ")
                val atomic = address.containsMatchIn(normalized) || words.any { word ->
                    val key = word.trim { it in edges }
                    key in standaloneQualifiers || qualifiedWord.containsMatchIn(key)
                }
                val kept = if (atomic) {
                    // A repeated noun must not turn “不是 骚扰电话” into a bare “不是”.
                    if (!completeStatements.add(normalized)) return@mapNotNull null
                    words
                } else words.filter { word -> key(word).let { it.isNotBlank() && seen.add(it) } }
                kept.takeIf(List<String>::isNotEmpty)?.joinToString(" ")
            }.joinToString(" · ")
        }
    }

    private fun key(word: String): String = word.trim { it in edges }.lowercase()
}
