package com.fcj.telnumbercheck.factory

import com.fcj.telnumbercheck.BuildConfig
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FactoryMetadataSourceTest {
    @Test
    fun `latest metadata is public and requests carry no credentials or phone number`() = runBlocking {
        val body = """{"ok":true,"app":{"enabled":true,"platform":"android","slug":"${AppFactoryConfig.APP_SLUG}","version":"0.3.10","page_url":"${AppFactoryConfig.DOWNLOAD_PAGE_URL}"}}"""
        FakeMetadataServer(response(body)).use { server ->
            assertEquals(
                FactoryRelease("0.3.10", AppFactoryConfig.DOWNLOAD_PAGE_URL),
                HttpFactoryMetadataSource(server.endpoint).latest(),
            )
            val headers = server.requestLines.drop(1).associate { line ->
                line.substringBefore(':').lowercase() to line.substringAfter(':').trim()
            }
            assertEquals("application/json", headers["accept"])
            assertEquals("TelNumberCheck/${BuildConfig.VERSION_NAME}", headers["user-agent"])
            assertFalse(headers.containsKey("authorization"))
            assertFalse(headers.containsKey("cookie"))
        }

        val uri = URI(AppFactoryConfig.LATEST_VERSION_URL)
        assertEquals("https", uri.scheme)
        assertEquals(AppFactoryConfig.HOST, uri.host)
        assertEquals("/api/v1/apps/latest", uri.path)
        assertEquals(
            setOf("platform=android", "app_key=${AppFactoryConfig.APP_KEY}"),
            uri.rawQuery.split('&').toSet(),
        )
    }

    @Test
    fun `service errors HTML and oversized payloads do not create a release`() = runBlocking {
        listOf(
            response("unavailable", status = "503 Service Unavailable"),
            response("<html>Verification required</html>"),
            "HTTP/1.1 200 OK\r\nContent-Length: 65537\r\nConnection: close\r\n\r\n",
        ).forEach { response ->
            FakeMetadataServer(response).use { server ->
                assertNull(HttpFactoryMetadataSource(server.endpoint).latest())
            }
        }
    }

    @Test
    fun `a hung HTTP response cannot hold the checker past its own timeout`() = runBlocking {
        FakeMetadataServer(null).use { server ->
            val checker = AppUpdateChecker(
                HttpFactoryMetadataSource(server.endpoint),
                maxWaitMillis = 150L,
            )
            val startedAt = System.nanoTime()
            val outcome = async(start = CoroutineStart.UNDISPATCHED) { checker.check(manual = true) }
            assertTrue("HTTP request did not reach the local server", server.requestSeen.await(1, TimeUnit.SECONDS))

            assertEquals(UpdateCheckOutcome.Unavailable, outcome.await())
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            assertTrue("UI check took $elapsedMillis ms instead of cancelling the hung request", elapsedMillis < 1_500L)
            assertFalse(checker.state.value.checking)
            assertEquals("更新服务暂不可用，可继续正常使用", checker.state.value.message)
        }
    }

    private fun response(body: String, status: String = "200 OK"): String =
        "HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\nConnection: close\r\n\r\n$body"

    private class FakeMetadataServer(private val response: String?) : AutoCloseable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val acceptedSocket = AtomicReference<Socket?>()
        private val release = CountDownLatch(1)
        private val finished = CountDownLatch(1)
        val requestSeen = CountDownLatch(1)
        val endpoint = "http://127.0.0.1:${listener.localPort}/latest"
        @Volatile var requestLines: List<String> = emptyList()

        init {
            thread(name = "fake-factory-metadata", isDaemon = true) {
                try {
                    listener.accept().use { socket ->
                        acceptedSocket.set(socket)
                        socket.soTimeout = 2_000
                        val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            lines += line
                        }
                        requestLines = lines
                        requestSeen.countDown()
                        if (response == null) release.await(10, TimeUnit.SECONDS)
                        else socket.getOutputStream().apply {
                            write(response.toByteArray(Charsets.UTF_8))
                            flush()
                        }
                    }
                } catch (_: Exception) {
                    // Closing the fake server also unblocks intentionally stalled requests.
                } finally {
                    finished.countDown()
                }
            }
        }

        override fun close() {
            release.countDown()
            acceptedSocket.get()?.close()
            listener.close()
            assertTrue("Local metadata server did not stop", finished.await(2, TimeUnit.SECONDS))
        }
    }
}
