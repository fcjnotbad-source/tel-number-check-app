package com.fcj.telnumbercheck.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.http.SslError
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fcj.telnumbercheck.data.NumberServiceResultDecoder
import com.fcj.telnumbercheck.data.PhoneNumberNormalizer
import com.fcj.telnumbercheck.domain.BaiduLookupState
import com.fcj.telnumbercheck.domain.SearchEngine
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
class BaiduLookupWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {
    var onState: (BaiduLookupState) -> Unit = {}

    private var activeRequest = ""
    private var activeNumber = ""
    private var resolved = false
    private val extractor = context.assets.open("extract_search_results.js")
        .bufferedReader().use { it.readText() }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@BaiduLookupWebView, true)
        }

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                val request = activeRequest
                if (request.isNotBlank() && !resolved) {
                    extractResult(request, attempt = 0)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && !resolved) {
                    resolved = true
                    onState(BaiduLookupState.Failure("搜索页面加载失败：${error.description}"))
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (!resolved) {
                    resolved = true
                    onState(BaiduLookupState.Failure("搜索引擎连接的安全证书校验失败"))
                }
            }
        }
    }

    fun search(phoneNumber: String, requestId: Long) {
        val number = PhoneNumberNormalizer.normalize(phoneNumber)
        val request = "$requestId:$number"
        if (request == activeRequest) return

        activeRequest = request
        activeNumber = number
        resolved = false
        post {
            if (request == activeRequest && !resolved) onState(BaiduLookupState.Loading)
        }
        stopLoading()
        loadUrl(BaiduSearch.searchUrl(number))
    }

    private fun extractResult(request: String, attempt: Int) {
        if (resolved || request != activeRequest) return

        postDelayed({
            if (resolved || request != activeRequest) return@postDelayed
            val script = extractor.replace("__ENGINE__", JSONObject.quote(SearchEngine.BAIDU.id))
                .replace("__NUMBER__", JSONObject.quote(activeNumber))
            evaluateJavascript(script) { rawValue ->
                if (resolved || request != activeRequest) return@evaluateJavascript
                when (val decoded = NumberServiceResultDecoder.decode(rawValue, SearchEngine.BAIDU)) {
                    null -> retryOrFail(request, attempt)
                    else -> when (decoded.status) {
                        "ok" -> {
                            val result = decoded.results.firstOrNull()
                            if (result == null) {
                                retryOrFail(request, attempt)
                            } else {
                                resolved = true
                                CookieManager.getInstance().flush()
                                onState(BaiduLookupState.Success(result))
                            }
                        }
                        "verify" -> {
                            resolved = true
                            onState(
                                BaiduLookupState.Failure(
                                    message = "搜索引擎需要安全验证，请点“完成验证”后再查询",
                                    requiresVerification = true,
                                ),
                            )
                        }
                        else -> retryOrFail(request, attempt)
                    }
                }
            }
        }, EXTRACTION_DELAYS[attempt])
    }

    private fun retryOrFail(request: String, attempt: Int) {
        if (attempt < EXTRACTION_DELAYS.lastIndex) {
            extractResult(request, attempt + 1)
        } else if (!resolved && request == activeRequest) {
            resolved = true
            onState(BaiduLookupState.Failure("搜索页面已打开，但暂时没有识别到结果"))
        }
    }

    companion object {
        private val EXTRACTION_DELAYS = longArrayOf(250L, 900L, 1_800L, 3_000L)
    }
}
