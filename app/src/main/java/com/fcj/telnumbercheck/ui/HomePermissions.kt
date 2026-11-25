package com.fcj.telnumbercheck.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fcj.telnumbercheck.call.CallAlertPermissions

internal data class HomePermissions(
    val roleEnabled: Boolean,
    val roleAvailable: Boolean,
    val notificationsAllowed: Boolean,
    val fullScreenAllowed: Boolean,
    val overlayAllowed: Boolean,
    val enableRole: () -> Unit,
    val enableNotifications: () -> Unit,
    val enableFullScreen: () -> Unit,
    val enableOverlay: () -> Unit,
)

@Composable
internal fun rememberHomePermissions(activity: ComponentActivity, promptAllowed: Boolean): HomePermissions {
    val manager = remember(activity) { activity.getSystemService(RoleManager::class.java) }
    var role by remember { mutableStateOf(manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) }
    var notifications by remember { mutableStateOf(CallAlertPermissions.canPostNotifications(activity)) }
    var fullScreen by remember { mutableStateOf(CallAlertPermissions.canUseFullScreenIntent(activity)) }
    var overlay by remember { mutableStateOf(CallAlertPermissions.canDrawOverlays(activity)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onboarding = remember(activity) { activity.getSharedPreferences("onboarding", Context.MODE_PRIVATE) }
    val available = manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = CallAlertPermissions.canPostNotifications(activity)
        Toast.makeText(activity, if (notifications) "来电提醒通知已开启" else "未开启来电提醒通知", Toast.LENGTH_SHORT).show()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        role = manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        Toast.makeText(activity, if (role) "来电识别已启用" else "未启用来电识别", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                role = manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                notifications = CallAlertPermissions.canPostNotifications(activity)
                fullScreen = CallAlertPermissions.canUseFullScreenIntent(activity)
                overlay = CallAlertPermissions.canDrawOverlays(activity)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(role, available, promptAllowed) {
        if (promptAllowed && !role && available && !onboarding.getBoolean("role_prompted", false)) {
            onboarding.edit().putBoolean("role_prompted", true).apply()
            roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }
    LaunchedEffect(role, notifications, promptAllowed) {
        if (promptAllowed && role && !notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !onboarding.getBoolean("notification_prompted", false)
        ) {
            onboarding.edit().putBoolean("notification_prompted", true).apply()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    return HomePermissions(
        roleEnabled = role,
        roleAvailable = available,
        notificationsAllowed = notifications,
        fullScreenAllowed = fullScreen,
        overlayAllowed = overlay,
        enableRole = {
            if (available) roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        },
        enableNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else activity.startActivity(CallAlertPermissions.notificationSettingsIntent(activity))
        },
        enableFullScreen = {
            runCatching {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    CallAlertPermissions.fullScreenIntentSettings(activity)
                } else CallAlertPermissions.notificationSettingsIntent(activity)
                activity.startActivity(intent)
            }
                .onFailure { Toast.makeText(activity, "无法打开全屏提醒设置", Toast.LENGTH_SHORT).show() }
        },
        enableOverlay = {
            runCatching { activity.startActivity(CallAlertPermissions.overlaySettingsIntent(activity)) }
                .onFailure { Toast.makeText(activity, "无法打开悬浮窗口设置", Toast.LENGTH_SHORT).show() }
        },
    )
}
