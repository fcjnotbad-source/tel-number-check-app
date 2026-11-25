package com.fcj.telnumbercheck.ui

import android.content.Context
import android.app.AlertDialog
import com.fcj.telnumbercheck.domain.SearchEngine
import com.fcj.telnumbercheck.search.SearchEnginePages

object BaiduSearch {
    fun open(context: Context, number: String) {
        val engines = SearchEngine.entries
        AlertDialog.Builder(context)
            .setTitle("选择搜索来源")
            .setItems(engines.map { it.displayName }.toTypedArray()) { _, index ->
                context.startActivity(
                    BaiduBrowserActivity.intentForUrl(
                        context,
                        SearchEnginePages.url(engines[index], number),
                    ),
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun openResult(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        context.startActivity(BaiduBrowserActivity.intentForUrl(context, url))
    }

    fun searchUrl(number: String): String =
        SearchEnginePages.url(SearchEngine.BAIDU, number)
}
