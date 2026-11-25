package com.fcj.telnumbercheck.search

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.SearchEngine
import com.fcj.telnumbercheck.data.NumberServiceResultDecoder
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
internal class SearchEngineWebView(
    context: Context,
    private val engine: SearchEngine,
    private val number: String,
    private val onComplete: (List<BaiduSearchResult>, Boolean) -> Unit,
) : WebView(context) {
    private var resolved = false
    private var extracting = false
    private var disposed = false
    private var rendererGone = false
    private val script = context.assets.open("extract_search_results.js")
        .bufferedReader().use { it.readText() }
        .replace("__ENGINE__", JSONObject.quote(engine.id))
        .replace("__NUMBER__", JSONObject.quote(number))
    private val poll = object : Runnable {
        override fun run() {
            if (resolved) return
            extract()
            if (!resolved) postDelayed(this, POLL_INTERVAL_MILLIS)
        }
    }
    private val deadline = Runnable { complete(emptyList()) }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@SearchEngineWebView, true)
        }
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (!resolved) extract()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) complete(emptyList())
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone = true
                complete(emptyList())
                return true
            }
        }
    }

    fun start() {
        postDelayed(poll, 400L)
        postDelayed(deadline, SOURCE_TIMEOUT_MILLIS)
        loadUrl(SearchEnginePages.url(engine, number))
    }

    private fun extract() {
        if (resolved || extracting) return
        extracting = true
        runCatching { evaluateJavascript(script) { raw ->
            extracting = false
            if (resolved) return@evaluateJavascript
            val decoded = NumberServiceResultDecoder.decode(raw, engine) ?: return@evaluateJavascript
            when (decoded.status) {
                "verify" -> complete(emptyList(), verification = true)
                "empty" -> complete(emptyList())
                "ok" -> {
                    if (decoded.results.isNotEmpty()) complete(decoded.results)
                }
            }
        } }.onFailure { complete(emptyList()) }
    }

    private fun complete(results: List<BaiduSearchResult>, verification: Boolean = false) {
        if (resolved) return
        resolved = true
        removeCallbacks(poll)
        removeCallbacks(deadline)
        if (!rendererGone) stopLoading()
        onComplete(results, verification)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        resolved = true
        removeCallbacks(poll)
        removeCallbacks(deadline)
        if (!rendererGone) stopLoading()
        destroy()
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 650L
        private const val SOURCE_TIMEOUT_MILLIS = 10_000L
    }
}
