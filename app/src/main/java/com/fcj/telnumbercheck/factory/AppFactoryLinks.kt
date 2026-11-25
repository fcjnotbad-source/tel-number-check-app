package com.fcj.telnumbercheck.factory

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object AppFactoryLinks {
    const val DOWNLOAD_PAGE_URL = AppFactoryConfig.DOWNLOAD_PAGE_URL

    fun openDownloadPage(context: Context, pageUrl: String = DOWNLOAD_PAGE_URL) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                },
            )
        }.onFailure {
            Toast.makeText(context, "暂时无法打开下载页面，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }
}
