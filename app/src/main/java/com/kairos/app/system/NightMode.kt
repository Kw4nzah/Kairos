package com.kairos.app.system

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.kairos.app.data.ThemeMode

/**
 * The system draws the splash before any app code runs, so it can follow the in-app theme only if
 * Android knows about it (API 31+). Older versions always show the splash in the system theme.
 */
object NightMode {
    fun apply(ctx: Context, mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO // per-app AUTO means "follow the system"
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            }
        )
    }
}
