package com.fcj.telnumbercheck.search

import android.content.Context
import android.widget.FrameLayout
import com.fcj.telnumbercheck.data.PhoneNumberNormalizer
import com.fcj.telnumbercheck.data.SearchResultAggregator
import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.MultiSearchLookupState
import com.fcj.telnumbercheck.domain.SearchEngine

class MultiEngineLookupView(context: Context) : FrameLayout(context) {
    var onState: (MultiSearchLookupState) -> Unit = {}
    private val views = mutableListOf<SearchEngineWebView>()
    private val results = mutableMapOf<SearchEngine, List<BaiduSearchResult>>()
    private val finished = mutableSetOf<SearchEngine>()
    private var generation = 0L
    private var activeRequest = ""
    private var activeNumber = ""
    private var completed = false
    private var verificationRequired = false
    private val deadline = Runnable { complete() }

    fun search(phoneNumber: String, requestId: Long) {
        val number = PhoneNumberNormalizer.normalize(phoneNumber)
        val request = "$requestId:$number"
        if (request == activeRequest) return
        dispose()
        activeRequest = request
        activeNumber = number
        completed = false
        verificationRequired = false
        results.clear()
        finished.clear()
        val token = generation
        post {
            if (token != generation || completed) return@post
            onState(MultiSearchLookupState.Loading)
            postDelayed(deadline, TOTAL_TIMEOUT_MILLIS)
            SearchEngine.entries.forEach { engine ->
                runCatching {
                    val view = SearchEngineWebView(context, engine, number) { values, verification ->
                        if (token != generation || completed || engine in finished) return@SearchEngineWebView
                        verificationRequired = verificationRequired || verification
                        finished.add(engine)
                        results[engine] = values
                        val merged = mergedResults()
                        if (finished.size == SearchEngine.entries.size) {
                            complete()
                        } else if (merged.isNotEmpty()) {
                            onState(MultiSearchLookupState.Partial(merged))
                        }
                    }
                    views.add(view)
                    addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                    view.start()
                }.onFailure {
                    finished.add(engine)
                    if (finished.size == SearchEngine.entries.size) complete()
                }
            }
        }
    }

    private fun mergedResults(): List<BaiduSearchResult> = SearchResultAggregator.merge(
        activeNumber,
        SearchEngine.entries.flatMap { results[it].orEmpty() },
    )

    private fun complete() {
        if (completed || activeRequest.isBlank()) return
        completed = true
        removeCallbacks(deadline)
        val merged = mergedResults()
        val state = if (merged.isNotEmpty()) {
            MultiSearchLookupState.Success(merged)
        } else {
            MultiSearchLookupState.Failure(
                message = "暂未找到号码查询服务结果，可重新检测或打开搜索页",
                requiresVerification = verificationRequired,
            )
        }
        onState(state)
    }

    fun dispose() {
        generation += 1
        completed = true
        activeRequest = ""
        removeCallbacks(deadline)
        val previousViews = views.toList()
        views.clear()
        removeAllViews()
        previousViews.forEach { it.dispose() }
    }

    companion object {
        private const val TOTAL_TIMEOUT_MILLIS = 12_000L
    }
}
