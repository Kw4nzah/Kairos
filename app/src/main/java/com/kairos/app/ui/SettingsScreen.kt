package com.kairos.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kairos.app.data.AppLanguage
import com.kairos.app.data.ThemeMode
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kairos.app.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kairos.app.data.Settings
import com.kairos.app.system.UpdateInfo
import com.kairos.app.system.Updater
import com.kairos.app.system.isXiaomi
import com.kairos.app.system.openXiaomiPermissions
import androidx.compose.material3.Button
import kotlinx.coroutines.launch
import com.kairos.app.logic.ToneSet
import com.kairos.app.system.CalendarSync
import com.kairos.app.system.PermKind
import com.kairos.app.system.SoundPreview
import com.kairos.app.system.Sounds
import com.kairos.app.system.openPermissionSettings
import com.kairos.app.system.rememberPerms

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    tone: ToneSet,
    padding: PaddingValues,
    vm: MainViewModel,
    say: (String) -> Unit
) {
    val ctx = LocalContext.current
    val perms = rememberPerms()
    var editProfile by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { SoundPreview.stop() } }

    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            vm.setHardcore(true)
            say(tone.snackHardcoreOn)
        } else {
            say(tone.snackCalendarDenied)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fadeIn(0))

        SettingsSection(stringResource(R.string.sec_appearance), 1) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark
                )
                modes.forEachIndexed { i, (mode, labelRes) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size)
                    ) { Text(stringResource(labelRes)) }
                }
            }
        }

        SettingsSection(stringResource(R.string.sec_language), 2) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val langs = listOf(
                    AppLanguage.SYSTEM to R.string.lang_system,
                    AppLanguage.ENGLISH to R.string.lang_en,
                    AppLanguage.TURKISH to R.string.lang_tr,
                    AppLanguage.RUSSIAN to R.string.lang_ru
                )
                langs.forEachIndexed { i, (lang, labelRes) ->
                    SegmentedButton(
                        selected = settings.language == lang,
                        onClick = { vm.setLanguage(lang) },
                        shape = SegmentedButtonDefaults.itemShape(i, langs.size)
                    ) { Text(stringResource(labelRes), maxLines = 1) }
                }
            }
        }

        SettingsSection(stringResource(R.string.sec_profile), 3) {
            Text(settings.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.profile_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { editProfile = true }) { Text(stringResource(R.string.change_name)) }
        }

        SettingsSection(stringResource(R.string.sec_hardcore), 4) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.hardcore_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
                Switch(
                    checked = settings.hardcore,
                    onCheckedChange = { on ->
                        if (!on) {
                            vm.setHardcore(false)
                            say(tone.snackHardcoreOff)
                        } else if (CalendarSync.hasPermission(ctx)) {
                            vm.setHardcore(true)
                            say(tone.snackHardcoreOn)
                        } else {
                            calendarLauncher.launch(CalendarSync.PERMISSIONS)
                        }
                    }
                )
            }
        }

        SettingsSection(stringResource(R.string.sec_sounds), 5) {
            Text(
                stringResource(R.string.default_sound),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in Sounds.all) {
                    FilterChip(
                        selected = settings.defaultSound == s.key,
                        onClick = { vm.setDefaultSound(s.key) },
                        label = { Text(stringResource(s.label)) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { SoundPreview.play(ctx, Sounds.byKey(settings.defaultSound)) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_play_sound))
                }
                Text(stringResource(R.string.sound_preview, stringResource(Sounds.byKey(settings.defaultSound).label)))
            }
        }

        SettingsSection(stringResource(R.string.sec_permissions), 6) {
            PermRow(stringResource(R.string.perm_notifications), perms.notifications) { openPermissionSettings(ctx, PermKind.NOTIFICATIONS) }
            PermRow(stringResource(R.string.perm_exact_alarm), perms.exactAlarm) { openPermissionSettings(ctx, PermKind.EXACT_ALARM) }
            PermRow(stringResource(R.string.perm_full_screen), perms.fullScreen) { openPermissionSettings(ctx, PermKind.FULL_SCREEN) }
            PermRow(stringResource(R.string.perm_overlay), perms.overlay) { openPermissionSettings(ctx, PermKind.OVERLAY) }
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                PermRow(stringResource(R.string.perm_screen_on), perms.screenOn) { openPermissionSettings(ctx, PermKind.SCREEN_ON) }
            }
            PermRow(stringResource(R.string.perm_battery), perms.batteryFree) { openPermissionSettings(ctx, PermKind.BATTERY) }
            if (isXiaomi()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.perm_xiaomi), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { openXiaomiPermissions(ctx) }) { Text(stringResource(R.string.perm_grant)) }
                }
            }
            OutlinedButton(
                onClick = {
                    vm.startTest(ctx.getString(R.string.test_alarm_title))
                    say(ctx.getString(R.string.test_alarm_started))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.test_alarm)) }
        }

        SettingsSection(stringResource(R.string.sec_about), 7) {
            val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
            UpdateRow(version)
        }
    }

    if (editProfile) {
        var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(settings.name) }
        AlertDialog(
            onDismissRequest = { editProfile = false },
            title = { Text(stringResource(R.string.name_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(30) },
                        label = { Text(stringResource(R.string.name_label)) }, singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.saveProfile(name.trim())
                        editProfile = false
                        say(tone.snackProfileSaved)
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editProfile = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** One separate block of settings: its own heading above a card. */
@Composable
private fun SettingsSection(title: String, index: Int, content: @Composable () -> Unit) {
    Column(Modifier.fadeIn(index).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (granted) R.string.perm_on else R.string.perm_off),
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        TextButton(onClick = onFix) { Text(stringResource(if (granted) R.string.perm_settings else R.string.perm_grant)) }
    }
}

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object NeedPermission : UpdateState
    data object Failed : UpdateState
}

@Composable
private fun UpdateRow(currentVersion: String) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var apk by remember { mutableStateOf<java.io.File?>(null) }

    fun startDownload(info: UpdateInfo) {
        scope.launch {
            state = UpdateState.Downloading(0f)
            try {
                val f = Updater.download(ctx, info) { state = UpdateState.Downloading(it) }
                apk = f
                if (Updater.canInstall(ctx)) {
                    Updater.install(ctx, f); state = UpdateState.Idle
                } else {
                    state = UpdateState.NeedPermission
                }
            } catch (_: Exception) {
                state = UpdateState.Failed
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val s = state
        when (s) {
            UpdateState.UpToDate -> Text(stringResource(R.string.update_latest), style = MaterialTheme.typography.bodyMedium)
            UpdateState.Failed -> Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            is UpdateState.Available -> Text(stringResource(R.string.update_available, s.info.version), style = MaterialTheme.typography.bodyMedium)
            is UpdateState.Downloading -> {
                Text(stringResource(R.string.update_downloading, (s.progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }
            UpdateState.NeedPermission -> Text(stringResource(R.string.update_need_permission), style = MaterialTheme.typography.bodyMedium)
            else -> {}
        }
        when (s) {
            is UpdateState.Available -> Button(onClick = { startDownload(s.info) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_install))
            }
            UpdateState.NeedPermission -> Button(
                onClick = { runCatching { Updater.openInstallPermission(ctx) }.onFailure { state = UpdateState.Failed } }, modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.update_allow)) }
            is UpdateState.Downloading, UpdateState.Checking -> {}
            else -> OutlinedButton(onClick = {
                state = UpdateState.Checking
                scope.launch {
                    state = try {
                        Updater.check(currentVersion)?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
                    } catch (_: Exception) {
                        UpdateState.Failed
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.check_updates)) }
        }
        if (s == UpdateState.Checking) Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium)
    }
    // After the user grants "install unknown apps", continue with the already downloaded file.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner, state) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            val f = apk
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && state == UpdateState.NeedPermission && f != null && Updater.canInstall(ctx)) {
                state = if (runCatching { Updater.install(ctx, f) }.isSuccess) UpdateState.Idle else UpdateState.Failed
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}
