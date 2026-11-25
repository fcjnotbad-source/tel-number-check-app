package com.fcj.telnumbercheck.factory

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    private val release = FactoryRelease("0.3.10", AppFactoryConfig.DOWNLOAD_PAGE_URL)

    @Test
    fun `only a newer release produces an update offer`() = runBlocking {
        val newer = AppUpdateChecker(FactoryMetadataSource { release }, installedVersion = "0.3.9")
        assertEquals(UpdateCheckOutcome.Available(release), newer.check())
        assertEquals(release, newer.state.value.availableRelease)
        assertNull(newer.state.value.message)
        assertFalse(newer.state.value.checking)

        for (installedVersion in listOf("0.3.10", "0.4.0")) {
            val current = AppUpdateChecker(FactoryMetadataSource { release }, installedVersion)
            assertEquals(UpdateCheckOutcome.Current, current.check(manual = true))
            assertNull(current.state.value.availableRelease)
            assertEquals("当前已是最新版本", current.state.value.message)
            assertFalse(current.state.value.checking)
        }
    }

    @Test
    fun `network errors finish quietly during an automatic check`() = runBlocking {
        val checker = AppUpdateChecker(FactoryMetadataSource { throw IOException("offline") })

        assertEquals(UpdateCheckOutcome.Unavailable, checker.check())
        assertFalse(checker.state.value.checking)
        assertNull(checker.state.value.availableRelease)
        assertNull(checker.state.value.message)
    }

    @Test
    fun `a source which never responds times out and leaves the app usable`() = runBlocking {
        val checker = AppUpdateChecker(
            FactoryMetadataSource { awaitCancellation() },
            maxWaitMillis = 50L,
        )

        assertEquals(UpdateCheckOutcome.Unavailable, checker.check(manual = true))
        assertFalse(checker.state.value.checking)
        assertNull(checker.state.value.availableRelease)
        assertEquals("更新服务暂不可用，可继续正常使用", checker.state.value.message)
    }

    @Test
    fun `automatic checking happens once but a manual retry can succeed`() = runBlocking {
        var attempts = 0
        val checker = AppUpdateChecker(
            FactoryMetadataSource { if (++attempts == 1) null else release },
            installedVersion = "0.3.9",
        )

        assertEquals(UpdateCheckOutcome.Unavailable, checker.check())
        assertNull(checker.check())
        assertEquals(1, attempts)
        assertEquals(UpdateCheckOutcome.Available(release), checker.check(manual = true))
        assertEquals(2, attempts)
        assertFalse(checker.state.value.checking)
    }

    @Test
    fun `an in-flight check does not launch a second request`() = runBlocking {
        val response = CompletableDeferred<FactoryRelease?>()
        var attempts = 0
        val checker = AppUpdateChecker(
            FactoryMetadataSource { attempts++; response.await() },
            installedVersion = "0.3.9",
        )
        val first = async(start = CoroutineStart.UNDISPATCHED) { checker.check() }

        assertTrue(checker.state.value.checking)
        assertNull(checker.check(manual = true))
        assertEquals(1, attempts)
        response.complete(release)
        assertEquals(UpdateCheckOutcome.Available(release), first.await())
        assertFalse(checker.state.value.checking)
    }

    @Test
    fun `leaving the page cancels a check and resets checking for manual retry`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var attempts = 0
        val checker = AppUpdateChecker(
            FactoryMetadataSource {
                if (++attempts == 1) {
                    started.complete(Unit)
                    awaitCancellation()
                } else release
            },
            installedVersion = "0.3.9",
        )
        val checkingJob = launch { checker.check() }
        started.await()
        assertTrue(checker.state.value.checking)

        checkingJob.cancelAndJoin()

        assertTrue(checkingJob.isCancelled)
        assertFalse(checker.state.value.checking)
        assertNull(checker.state.value.message)
        assertEquals(UpdateCheckOutcome.Available(release), checker.check(manual = true))
    }
}
