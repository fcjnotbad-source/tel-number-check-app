package com.fcj.telnumbercheck.ui

import android.content.Context
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.fcj.telnumbercheck.TelNumberCheckApp
import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.domain.MultiSearchLookupState
import com.fcj.telnumbercheck.ui.theme.TelNumberCheckTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises navigation with a fake host: no test number ever enters a WebView or a network query. */
class HomeTabsStateTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TelNumberCheckApp
    private val popupVisible = mutableStateOf(false)
    private val started = AtomicInteger()
    private val disposed = AtomicInteger()
    private val activeNumber = AtomicReference<String>()
    private val callback = AtomicReference<(MultiSearchLookupState) -> Unit>()
    private val previousCache = mutableMapOf<String, String?>()
    private val previousOnboarding = mutableMapOf<String, Boolean?>()

    @Before
    fun prepareOnlyOwnedTestKeys() {
        val cache = app.getSharedPreferences("search_result_cache", Context.MODE_PRIVATE)
        TEST_NUMBERS.forEach { number -> previousCache[number] = cache.getString(number, null) }
        cache.edit().also { editor -> TEST_NUMBERS.forEach(editor::remove) }.commit()
        val onboarding = app.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        ONBOARDING_KEYS.forEach { key ->
            previousOnboarding[key] = if (onboarding.contains(key)) onboarding.getBoolean(key, false) else null
        }
        onboarding.edit().putBoolean("role_prompted", true).putBoolean("notification_prompted", true).commit()
    }

    @After
    fun restoreOnlyOwnedTestKeys() {
        app.getSharedPreferences("search_result_cache", Context.MODE_PRIVATE).edit().also { editor ->
            previousCache.forEach { (number, value) ->
                if (value == null) editor.remove(number) else editor.putString(number, value)
            }
        }.commit()
        app.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit().also { editor ->
            previousOnboarding.forEach { (key, value) ->
                if (value == null) editor.remove(key) else editor.putBoolean(key, value)
            }
        }.commit()
    }

    @Test
    fun switchingTabsKeepsOneLookupAndPreservesInputAndResult() {
        renderHome()
        startQuery(TEST_NUMBERS[0])

        selectTab("settings")
        selectTab("calls")
        selectTab("lookup")
        compose.onNode(hasSetTextAction()).assertTextContains(TEST_NUMBERS[0])
        compose.runOnIdle {
            assertEquals(1, started.get())
            assertEquals(0, disposed.get())
            assertEquals(TEST_NUMBERS[0], activeNumber.get())
            callback.get().invoke(
                MultiSearchLookupState.Success(listOf(BaiduSearchResult(
                    title = "${TEST_NUMBERS[0]}号码查询",
                    snippet = "测试号码服务",
                    targetUrl = null,
                    isNumberService = true,
                ))),
            )
        }
        compose.onNodeWithText("测试号码服务").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, disposed.get()) }

        selectTab("settings")
        selectTab("calls")
        selectTab("lookup")
        compose.onNode(hasSetTextAction()).assertTextContains(TEST_NUMBERS[0])
        compose.onNodeWithText("测试号码服务").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, started.get()) }
    }

    @Test
    fun popupBlankingHidesNavigationWithoutDisposingLookupAndRestoresQuery() {
        renderHome()
        startQuery(TEST_NUMBERS[1])
        compose.runOnIdle { popupVisible.value = true }

        listOf("calls", "lookup", "settings").forEach {
            compose.onNodeWithTag("home_tab_$it").assertDoesNotExist()
        }
        compose.runOnIdle {
            assertEquals(1, started.get())
            assertEquals(0, disposed.get())
            popupVisible.value = false
        }
        compose.onNodeWithTag("home_tab_lookup").assertIsSelected()
        compose.onNode(hasSetTextAction()).assertTextContains(TEST_NUMBERS[1])
        compose.runOnIdle {
            assertEquals(1, started.get())
            assertEquals(0, disposed.get())
            callback.get().invoke(MultiSearchLookupState.Failure("测试查询失败，可重试", requiresVerification = false))
        }
        compose.onNodeWithText("测试查询失败，可重试").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("重新查询").assertIsEnabled()
        compose.runOnIdle { assertEquals(1, disposed.get()) }
    }

    private fun renderHome() {
        val repository = app.phoneLookupRepository
        compose.setContent {
            TelNumberCheckTheme {
                MainScreen(repository, popupVisible.value) { number, id, onState ->
                    SideEffect {
                        activeNumber.set(number)
                        callback.set(onState)
                    }
                    DisposableEffect(number, id) {
                        started.incrementAndGet()
                        onDispose { disposed.incrementAndGet() }
                    }
                }
            }
        }
    }

    private fun startQuery(number: String) {
        selectTab("lookup")
        compose.onNode(hasSetTextAction()).performTextInput(number)
        compose.onNodeWithText("查询号码").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, started.get())
            assertEquals(0, disposed.get())
        }
    }

    private fun selectTab(tab: String) {
        compose.onNodeWithTag("home_tab_$tab").performClick()
    }

    private companion object {
        val TEST_NUMBERS = listOf("10000000004", "10000000005")
        val ONBOARDING_KEYS = listOf("role_prompted", "notification_prompted")
    }
}
