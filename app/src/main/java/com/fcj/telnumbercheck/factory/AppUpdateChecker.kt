package com.fcj.telnumbercheck.factory

import com.fcj.telnumbercheck.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

data class UpdateCheckState(
    val checking: Boolean = false,
    val availableRelease: FactoryRelease? = null,
    val message: String? = null,
)

sealed interface UpdateCheckOutcome {
    data class Available(val release: FactoryRelease) : UpdateCheckOutcome
    data object Current : UpdateCheckOutcome
    data object Unavailable : UpdateCheckOutcome
}

/** Used only by the main page; call detection does not depend on this service. */
class AppUpdateChecker(
    private val source: FactoryMetadataSource = HttpFactoryMetadataSource(),
    private val installedVersion: String = BuildConfig.VERSION_NAME,
    private val maxWaitMillis: Long = 5_000L,
) {
    private val mutableState = MutableStateFlow(UpdateCheckState())
    val state = mutableState.asStateFlow()
    private var attemptedAutomaticCheck = false

    suspend fun check(manual: Boolean = false): UpdateCheckOutcome? {
        if (mutableState.value.checking || (!manual && attemptedAutomaticCheck)) return null
        attemptedAutomaticCheck = true
        mutableState.value = mutableState.value.copy(checking = true, message = null)
        try {
            val release = try {
                withTimeout(maxWaitMillis) { source.latest() }
            } catch (_: TimeoutCancellationException) {
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val outcome = when {
                release == null -> UpdateCheckOutcome.Unavailable
                AppVersion.isNewer(release.version, installedVersion) -> UpdateCheckOutcome.Available(release)
                else -> UpdateCheckOutcome.Current
            }
            mutableState.value = UpdateCheckState(
                availableRelease = (outcome as? UpdateCheckOutcome.Available)?.release,
                message = if (manual) {
                    when (outcome) {
                        is UpdateCheckOutcome.Available -> null
                        UpdateCheckOutcome.Current -> "当前已是最新版本"
                        UpdateCheckOutcome.Unavailable -> "更新服务暂不可用，可继续正常使用"
                    }
                } else null,
            )
            return outcome
        } finally {
            mutableState.value = mutableState.value.copy(checking = false)
        }
    }
}
