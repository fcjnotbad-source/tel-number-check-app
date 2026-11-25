package com.fcj.telnumbercheck.factory

import com.fcj.telnumbercheck.BuildConfig
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

fun interface FactoryMetadataSource {
    suspend fun latest(): FactoryRelease?
}

class HttpFactoryMetadataSource(
    private val endpoint: String = AppFactoryConfig.LATEST_VERSION_URL,
) : FactoryMetadataSource {
    override suspend fun latest(): FactoryRelease? = suspendCancellableCoroutine { continuation ->
        networkExecutor.purge()
        val request = try {
            networkExecutor.submit {
                val release = runCatching { fetch { continuation.isActive } }.getOrNull()
                if (continuation.isActive) continuation.resume(release)
            }
        } catch (_: RejectedExecutionException) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation { request.cancel(true) }
    }

    private fun fetch(active: () -> Boolean): FactoryRelease? {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = SOCKET_TIMEOUT_MILLIS
            connection.readTimeout = SOCKET_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "TelNumberCheck/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            if (connection.contentLengthLong > MAX_RESPONSE_BYTES) return null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4_096)
                while (true) {
                    if (!active() || Thread.currentThread().isInterrupted) return null
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_RESPONSE_BYTES) return null
                    output.write(buffer, 0, count)
                }
            }
            return FactoryReleaseDecoder.decode(output.toString(Charsets.UTF_8.name()))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val SOCKET_TIMEOUT_MILLIS = 2_000
        private const val MAX_RESPONSE_BYTES = 65_536
        // A slow DNS resolver must not keep the UI's five-second check alive or grow a work queue.
        private val networkExecutor = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1),
            { task -> Thread(task, "factory-version-check").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
    }
}
