package com.fcj.telnumbercheck.domain

enum class SearchEngine(val id: String, val displayName: String) {
    BAIDU("baidu", "搜索引擎"),
    QIHOO("qihoo", "360搜索"),
    SOGOU("sogou", "搜狗搜索"),
    ;

    companion object {
        fun fromId(id: String): SearchEngine? = entries.firstOrNull { it.id == id }
    }
}
