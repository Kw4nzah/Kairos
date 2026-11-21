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
import androidx.compose.material3.Switch
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

@OptIn(ExperimentalLayoutApi::class)
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
        Text("Ayarlar", style = MaterialTheme.typography.headlineSmall)

        SettingsCard {
            Text("Profil", style = MaterialTheme.typography.titleMedium)
            Text(
                "${settings.name}, ${settings.age} yaş",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Bilgiler yalnızca bu cihazda saklanır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { editProfile = true }) { Text("Ad ve yaşı değiştir") }
        }

        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hardcore modu", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tüm hatırlatmalar telefonun takvimine de yazılır (10 dk önce ve tam zamanında alarm).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

        SettingsCard {
            Text("Varsayılan ses", style = MaterialTheme.typography.titleMedium)
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

        SettingsCard {
            Text("İzinler", style = MaterialTheme.typography.titleMedium)
            PermRow("Bildirimler", perms.notifications) { openPermissionSettings(ctx, PermKind.NOTIFICATIONS) }
            PermRow("Tam zamanlı alarm", perms.exactAlarm) { openPermissionSettings(ctx, PermKind.EXACT_ALARM) }
            PermRow("Tam ekran bildirim", perms.fullScreen) { openPermissionSettings(ctx, PermKind.FULL_SCREEN) }
        }
    }

    if (editProfile) {
        var name by remember { mutableStateOf(settings.name) }
        var age by remember { mutableStateOf(settings.age.toString()) }
        val ageValue = age.toIntOrNull()
        AlertDialog(
            onDismissRequest = { editProfile = false },
            title = { Text("Ad ve yaş") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(30) },
                        label = { Text("Ad") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = age, onValueChange = { v -> age = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Yaş") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && ageValue != null && ageValue in 5..120,
                    onClick = {
                        vm.saveProfile(name.trim(), ageValue!!)
                        editProfile = false
                        // The tone is derived from the age, so it switches as soon as DataStore emits.
                        say(com.kairos.app.logic.Tone.forAge(ageValue).snackProfileSaved)
                    }
                ) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { editProfile = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
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
