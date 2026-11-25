package com.fcj.telnumbercheck.search

import android.net.Uri
import com.fcj.telnumbercheck.domain.SearchEngine

object SearchEnginePages {
    fun url(engine: SearchEngine, number: String): String {
        val query = Uri.encode(number.trim())
        return when (engine) {
            SearchEngine.BAIDU -> "https://m.baidu.com/s?word=$query"
            SearchEngine.QIHOO -> "https://m.so.com/s?q=$query"
            SearchEngine.SOGOU -> "https://m.sogou.com/web/searchList.jsp?keyword=$query"
        }
    }
}
