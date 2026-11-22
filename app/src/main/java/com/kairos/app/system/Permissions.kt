package com.kairos.app.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

data class PermState(
    val notifications: Boolean,
    val exactAlarm: Boolean,
    val fullScreen: Boolean,
    val overlay: Boolean,
    val calendar: Boolean
) {
    val reminderIssues: Boolean get() = !notifications || !exactAlarm || !fullScreen || !overlay
}

enum class PermKind { NOTIFICATIONS, EXACT_ALARM, FULL_SCREEN, OVERLAY }

fun readPerms(ctx: Context): PermState {
    val fullScreen = if (Build.VERSION.SDK_INT >= 34) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
    } else true
    return PermState(
        notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
        exactAlarm = Scheduler.canExact(ctx),
        fullScreen = fullScreen,
        overlay = Settings.canDrawOverlays(ctx),
        calendar = CalendarSync.hasPermission(ctx)
    )
}

/** Permission state that refreshes every time the app returns to the foreground. */
@Composable
fun rememberPerms(): PermState {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(readPerms(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = readPerms(ctx)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

/** Opens the system screen where the user can grant the given permission. */
fun openPermissionSettings(ctx: Context, kind: PermKind) {
    val pkgUri = Uri.parse("package:${ctx.packageName}")
    val intent = when (kind) {
        PermKind.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        PermKind.EXACT_ALARM ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        PermKind.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
        PermKind.FULL_SCREEN ->
            if (Build.VERSION.SDK_INT >= 34)
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (_: Exception) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
