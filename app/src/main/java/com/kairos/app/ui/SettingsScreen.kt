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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kairos.app.data.Settings
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
    var editProfile by remember { mutableStateOf(false) }

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
        Text("Ayarlar", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fadeIn(0))

        SettingsSection("Görünüm", 1) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(
                    ThemeMode.SYSTEM to "Sistem",
                    ThemeMode.LIGHT to "Açık",
                    ThemeMode.DARK to "Koyu"
                )
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size)
                    ) { Text(label) }
                }
            }
        }

        SettingsSection("Profil", 2) {
            Text(settings.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Ad yalnızca bu cihazda saklanır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { editProfile = true }) { Text("Adı değiştir") }
        }

        SettingsSection("Hardcore modu", 3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tüm hatırlatmalar telefonun takvimine de yazılır (10 dk önce ve tam zamanında alarm).",
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

        SettingsSection("Sesler", 4) {
            Text(
                "Varsayılan hatırlatma sesi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in Sounds.all) {
                    FilterChip(
                        selected = settings.defaultSound == s.key,
                        onClick = { vm.setDefaultSound(s.key) },
                        label = { Text(s.label) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { SoundPreview.play(ctx, Sounds.byKey(settings.defaultSound)) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Sesi dinle")
                }
                Text("Önizleme: ${Sounds.byKey(settings.defaultSound).label}")
            }
        }

        SettingsSection("İzinler", 5) {
            PermRow("Bildirimler", perms.notifications) { openPermissionSettings(ctx, PermKind.NOTIFICATIONS) }
            PermRow("Tam zamanlı alarm", perms.exactAlarm) { openPermissionSettings(ctx, PermKind.EXACT_ALARM) }
            PermRow("Tam ekran bildirim", perms.fullScreen) { openPermissionSettings(ctx, PermKind.FULL_SCREEN) }
        }
    }

    if (editProfile) {
        var name by remember { mutableStateOf(settings.name) }
        AlertDialog(
            onDismissRequest = { editProfile = false },
            title = { Text("Ad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(30) },
                        label = { Text("Ad") }, singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.saveProfile(name.trim())
                        editProfile = false
                        say(com.kairos.app.logic.Tone.set.snackProfileSaved)
                    }
                ) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { editProfile = false }) { Text("Vazgeç") } }
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
                if (granted) "Açık" else "Kapalı",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        TextButton(onClick = onFix) { Text(if (granted) "Ayarlar" else "İzin ver") }
    }
}
