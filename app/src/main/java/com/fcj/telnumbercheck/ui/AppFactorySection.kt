package com.fcj.telnumbercheck.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.fcj.telnumbercheck.BuildConfig
import com.fcj.telnumbercheck.TelNumberCheckApp
import com.fcj.telnumbercheck.factory.AppFactoryLinks
import com.fcj.telnumbercheck.factory.UpdateCheckOutcome
import kotlinx.coroutines.launch

@Composable
internal fun AutomaticAppUpdateCheck() {
    val context = LocalContext.current
    val checker = remember { (context.applicationContext as TelNumberCheckApp).appUpdateChecker }
    LaunchedEffect(checker) {
        val outcome = checker.check()
        if (outcome is UpdateCheckOutcome.Available) {
            Toast.makeText(context, "发现新版本 ${outcome.release.version}，可在设置中下载", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun AppFactorySection() {
    val context = LocalContext.current
    val checker = remember { (context.applicationContext as TelNumberCheckApp).appUpdateChecker }
    val state by checker.state.collectAsState()
    val scope = rememberCoroutineScope()
    val openDownloadPage = {
        AppFactoryLinks.openDownloadPage(
            context,
            state.availableRelease?.pageUrl ?: AppFactoryLinks.DOWNLOAD_PAGE_URL,
        )
    }
    AppFactoryFooter(
        versionName = BuildConfig.VERSION_NAME,
        checking = state.checking,
        statusText = state.message,
        availableVersion = state.availableRelease?.version,
        onCheckUpdates = {
            scope.launch {
                val message = when (val outcome = checker.check(manual = true)) {
                    is UpdateCheckOutcome.Available -> "发现新版本 ${outcome.release.version}"
                    UpdateCheckOutcome.Current -> "当前已是最新版本"
                    UpdateCheckOutcome.Unavailable -> "更新服务暂不可用，可继续正常使用"
                    null -> null
                }
                message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            }
        },
        onInstallUpdate = openDownloadPage,
        onOpenDownloadPage = openDownloadPage,
    )
}
