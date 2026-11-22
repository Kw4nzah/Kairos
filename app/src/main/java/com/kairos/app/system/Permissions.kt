package com.kairos.app.system

import android.app.AppOpsManager
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
    val screenOn: Boolean,
    val batteryFree: Boolean,
    val calendar: Boolean
) {
    val reminderIssues: Boolean get() = !notifications || !exactAlarm || !fullScreen || !overlay
}

enum class PermKind { NOTIFICATIONS, EXACT_ALARM, FULL_SCREEN, OVERLAY, SCREEN_ON, BATTERY }

// Neither constant is in the public SDK surface for every API level we compile for, hence the raw values.
private const val OP_TURN_SCREEN_ON = "android:turn_screen_on"
private const val ACTION_TURN_SCREEN_ON_SETTINGS = "android.settings.TURN_SCREEN_ON_SETTINGS"

/**
 * "Turn screen on" special access. Where the system enforces it, an alarm without a popup rings on
 * a black screen unless the user has allowed it. There is nothing to grant before Android 14.
 */
private fun canTurnScreenOn(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return runCatching {
        ops.unsafeCheckOpNoThrow(OP_TURN_SCREEN_ON, ctx.applicationInfo.uid, ctx.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(true)
}

fun readPerms(ctx: Context): PermState {
    val fullScreen = if (Build.VERSION.SDK_INT >= 34) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
    } else true
    return PermState(
        notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
        exactAlarm = Scheduler.canExact(ctx),
        fullScreen = fullScreen,
        overlay = Settings.canDrawOverlays(ctx),
        screenOn = canTurnScreenOn(ctx),
        batteryFree = (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName),
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
        // No package URI: the per-app variant crashes Settings on Android 15 ("Invalid fragment for this activity").
        PermKind.SCREEN_ON -> Intent(ACTION_TURN_SCREEN_ON_SETTINGS)
        PermKind.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
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

fun isXiaomi(): Boolean = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

/**
 * Xiaomi (MIUI / HyperOS) hides "pop-ups while running in the background" and "show on lock screen"
 * in its own permission editor; without them an alarm cannot wake the screen or open the popup.
 */
fun openXiaomiPermissions(ctx: Context) {
    val pkg = ctx.packageName
    val intents = listOf(
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", pkg),
        Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    )
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: Exception) { }
    }
}
