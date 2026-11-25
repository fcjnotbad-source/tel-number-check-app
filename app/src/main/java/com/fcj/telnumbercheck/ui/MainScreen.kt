package com.fcj.telnumbercheck.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fcj.telnumbercheck.call.PendingCallerPopupStore
import com.fcj.telnumbercheck.data.CallEventStore
import com.fcj.telnumbercheck.data.PhoneLookupRepository
import com.fcj.telnumbercheck.data.PhoneNumberNormalizer
import com.fcj.telnumbercheck.domain.MultiSearchLookupState

@Composable
internal fun MainScreen(
    repository: PhoneLookupRepository,
    popupVisible: Boolean,
    lookupHost: @Composable (String, Long, (MultiSearchLookupState) -> Unit) -> Unit = { number, id, onState ->
        MultiSearchWebLookup(number, id, onState, Modifier.fillMaxWidth().height(1.dp).alpha(0.01f))
    },
) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val permissions = rememberHomePermissions(activity, promptAllowed = !popupVisible)
    val events = remember(activity) { CallEventStore(activity) }
    var latest by remember { mutableStateOf(events.latest()) }
    var input by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf(HomeTab.CALLS.name) }
    val selected = HomeTab.entries.firstOrNull { it.name == selectedName } ?: HomeTab.CALLS
    val pageStates = rememberSaveableStateHolder()
    val session = remember { HomeLookupSession() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    BackHandler(enabled = !popupVisible && selected != HomeTab.CALLS) {
        selectedName = HomeTab.CALLS.name
    }

    AutomaticAppUpdateCheck()
    DisposableEffect(events, lifecycle) {
        val listener = events.registerListener { activity.runOnUiThread { latest = events.latest() } }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latest = events.latest()
        }
        lifecycle.addObserver(observer)
        onDispose {
            events.unregisterListener(listener)
            lifecycle.removeObserver(observer)
        }
    }

    fun message(text: String) { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }

    fun startLookup(raw: String, refresh: Boolean = false, retryLatest: Boolean = false) {
        val number = PhoneNumberNormalizer.normalize(raw)
        if (!number.any(Char::isDigit)) {
            message("请输入要查询的号码")
            return
        }
        if (retryLatest) {
            PendingCallerPopupStore(activity).clear(number)
            input = number
            events.recordLookupRetry(number)
        }
        val cached = if (refresh || retryLatest) null else repository.cached(number)
        if (cached != null) {
            session.useCached(cached)
            message("已使用本机缓存")
        } else session.start(repository.pending(number), retryLatest)
    }

    Box(Modifier.fillMaxSize()) {
        // This host is intentionally outside every tab and the popup visibility branch.
        // Navigating or covering the home must not dispose an in-flight lookup.
        if (session.activeNumber.isNotBlank()) {
            val number = session.activeNumber
            val id = session.requestId
            lookupHost(number, id, callback@{ state ->
                if (!session.matches(number, id)) return@callback
                val retry = session.retryingLatestCall
                when (state) {
                    MultiSearchLookupState.Loading -> Unit
                    is MultiSearchLookupState.Partial -> {
                        val info = repository.withResults(number, state.results, cacheResult = false)
                        session.receive(number, id, info, complete = false)
                        if (retry && info.baiduSnippet != null) events.recordLookupSuccess(info)
                    }
                    is MultiSearchLookupState.Success -> {
                        val info = repository.withResults(number, state.results)
                        session.receive(number, id, info, complete = true)
                        if (retry) {
                            if (info.baiduSnippet != null) events.recordLookupSuccess(info)
                            else events.recordLookupFailure(info.detail, info.requiresVerification, number)
                        }
                    }
                    is MultiSearchLookupState.Failure -> {
                        val info = repository.withError(number, state.message, state.requiresVerification)
                        session.receive(number, id, info, complete = true)
                        if (retry) events.recordLookupFailure(state.message, state.requiresVerification, number)
                    }
                }
            })
        }

        if (popupVisible) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        } else {
            HomeTabsScaffold(selected, onSelect = { selectedName = it.name }) { padding ->
                pageStates.SaveableStateProvider(selected.name) {
                    val pageModifier = Modifier.fillMaxSize().padding(padding)
                    when (selected) {
                        HomeTab.CALLS -> CallsTab(
                            roleEnabled = permissions.roleEnabled,
                            roleAvailable = permissions.roleAvailable,
                            latestEvent = latest,
                            retryInProgress = session.retryingLatestCall && session.loading,
                            onEnableRole = permissions.enableRole,
                            onRetryLatest = { startLookup(it, refresh = true, retryLatest = true) },
                            onOpenSettings = { selectedName = HomeTab.SETTINGS.name },
                            onLookupNumber = {
                                input = PhoneNumberNormalizer.normalize(it)
                                selectedName = HomeTab.LOOKUP.name
                                startLookup(input)
                            },
                            modifier = pageModifier,
                        )
                        HomeTab.LOOKUP -> LookupTab(
                            number = input,
                            loading = session.loading,
                            result = session.result,
                            roleEnabled = permissions.roleEnabled,
                            onNumberChanged = { input = it },
                            onSearch = { startLookup(input) },
                            onRefresh = { session.result?.let { startLookup(it.normalizedNumber, refresh = true) } },
                            onOpenSearch = {
                                session.result?.let { info ->
                                    if (info.baiduUrl != null && !info.requiresVerification) BaiduSearch.openResult(activity, info.baiduUrl)
                                    else BaiduSearch.open(activity, info.normalizedNumber)
                                }
                            },
                            onPreview = {
                                session.result?.let { activity.startActivity(CallerPopupActivity.intent(activity, it.normalizedNumber)) }
                            },
                            onToggleLabel = {
                                session.result?.let { info ->
                                    if (info.isLocallyMarked) {
                                        repository.clearLocalLabel(info.normalizedNumber)
                                        message("已清除本机标记")
                                    } else {
                                        repository.markAsSpam(info.normalizedNumber)
                                        message("已标记为疑似骚扰")
                                    }
                                    session.refreshLabel(repository.refreshLocalLabel(info))
                                }
                            },
                            onOpenCalls = { selectedName = HomeTab.CALLS.name },
                            modifier = pageModifier,
                        )
                        HomeTab.SETTINGS -> SettingsTab(
                            notificationsAllowed = permissions.notificationsAllowed,
                            fullScreenAllowed = permissions.fullScreenAllowed,
                            overlayAllowed = permissions.overlayAllowed,
                            onEnableNotifications = permissions.enableNotifications,
                            onEnableFullScreen = permissions.enableFullScreen,
                            onEnableOverlay = permissions.enableOverlay,
                            modifier = pageModifier,
                        )
                    }
                }
            }
        }
    }
}
