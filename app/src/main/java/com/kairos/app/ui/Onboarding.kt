package com.kairos.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.system.PermKind
import com.kairos.app.system.isXiaomi
import com.kairos.app.system.openPermissionSettings
import com.kairos.app.system.openXiaomiPermissions
import com.kairos.app.system.rememberPerms

private const val STEPS = 3

/**
 * First launch, three steps: what Kairos is, your name, then the permissions that let an alarm
 * reach you. The name is stored only at the end, so quitting halfway starts the tour again.
 */
@Composable
fun OnboardingScreen(onDone: (name: String) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var previous by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }

    fun go(to: Int) { previous = step; step = to }
    BackHandler(enabled = step > 0) { go(step - 1) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Dots(step)
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState >= initialState) 1 else -1
                (fadeIn(tween(DurMed, easing = EaseOut)) +
                    slideInHorizontally(tween(DurMed, easing = EaseOut)) { dir * it / 8 }) togetherWith
                    (fadeOut(tween(DurShort)) + slideOutHorizontally(tween(DurMed, easing = EaseOut)) { -dir * it / 8 })
            },
            label = "onboarding"
        ) { s ->
            when (s) {
                0 -> IntroStep(onNext = { go(1) })
                1 -> NameStep(name, { name = it.take(30) }, onNext = { go(2) }, onBack = { go(0) })
                else -> PermissionsStep(onFinish = { onDone(name.trim()) }, onBack = { go(1) })
            }
        }
    }
}

@Composable
private fun Dots(step: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) { i ->
            val w by animateDpAsState(if (i == step) 24.dp else 8.dp, tween(DurMed, easing = EaseOut), label = "dotW")
            val c by animateColorAsState(
                if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                tween(DurMed), label = "dotC"
            )
            Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
        }
    }
}

@Composable
private fun IntroStep(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fadeIn(0))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fadeIn(1))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fadeIn(2)
        )
        Spacer(Modifier.height(24.dp))
        Feature(stringResource(R.string.onb_f1), 3)
        Feature(stringResource(R.string.onb_f2), 4)
        Feature(stringResource(R.string.onb_f3), 5)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().fadeIn(6)) { Text(stringResource(R.string.onb_next)) }
    }
}

@Composable
private fun Feature(text: String, index: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).fadeIn(index), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.onb_name_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fadeIn(0))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onb_intro), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fadeIn(1)
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = onName,
            label = { Text(stringResource(R.string.onb_name_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth().fadeIn(2)
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onb_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNext, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().fadeIn(3)) {
            Text(stringResource(R.string.onb_next))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onb_back)) }
    }
}

@Composable
private fun PermissionsStep(onFinish: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val perms = rememberPerms()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openPermissionSettings(ctx, PermKind.NOTIFICATIONS)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.onb_perm_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fadeIn(0))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onb_perm_body), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fadeIn(1)
        )
        Spacer(Modifier.height(16.dp))
        var i = 2
        PermItem(R.string.perm_notifications, R.string.onb_perm_notif, perms.notifications, i++) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else openPermissionSettings(ctx, PermKind.NOTIFICATIONS)
        }
        PermItem(R.string.perm_overlay, R.string.onb_perm_overlay, perms.overlay, i++) { openPermissionSettings(ctx, PermKind.OVERLAY) }
        if (Build.VERSION.SDK_INT >= 34) {
            PermItem(R.string.perm_full_screen, R.string.onb_perm_fullscreen, perms.fullScreen, i++) { openPermissionSettings(ctx, PermKind.FULL_SCREEN) }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            PermItem(R.string.perm_screen_on, R.string.onb_perm_screen_on, perms.screenOn, i++) { openPermissionSettings(ctx, PermKind.SCREEN_ON) }
        }
        if (!perms.exactAlarm) {
            PermItem(R.string.perm_exact_alarm, R.string.onb_perm_exact, false, i++) { openPermissionSettings(ctx, PermKind.EXACT_ALARM) }
        }
        PermItem(R.string.perm_battery, R.string.onb_perm_battery, perms.batteryFree, i++) { openPermissionSettings(ctx, PermKind.BATTERY) }
        if (isXiaomi()) {
            PermItem(R.string.perm_xiaomi, R.string.onb_perm_xiaomi, null, i++) { openXiaomiPermissions(ctx) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onb_perm_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().fadeIn(i)) { Text(stringResource(R.string.onb_finish)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onb_back)) }
    }
}

/** One permission: what it is, why it is needed, and a button that opens the right system screen. */
@Composable
private fun PermItem(title: Int, why: Int, granted: Boolean?, index: Int, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).fadeIn(index), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (granted == true) {
            Text(stringResource(R.string.perm_on), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        } else {
            OutlinedButton(onClick = onAllow) { Text(stringResource(R.string.perm_grant)) }
        }
    }
}
