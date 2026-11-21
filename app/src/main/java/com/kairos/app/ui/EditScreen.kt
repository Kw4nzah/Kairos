package com.kairos.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kairos.app.data.Reminder
import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import com.kairos.app.system.Sounds
import com.kairos.app.system.SoundPreview
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private val repeatOptions = listOf(
    RepeatType.NONE to "Yok",
    RepeatType.DAILY to "Her gün",
    RepeatType.WEEKLY to "Haftalık",
    RepeatType.MONTHLY to "Aylık"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    existing: Reminder?,
    defaultSound: String,
    onBack: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (Int) -> Unit
) {
    val ctx = LocalContext.current
    val initial = remember(existing?.id) {
        existing?.let { Fmt.local(it.dueAt) }
            ?: LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
    }
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes ?: "") }
    var date by remember(existing?.id) { mutableStateOf(initial.toLocalDate()) }
    var time by remember(existing?.id) { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0)) }
    var repeat by remember(existing?.id) {
        mutableStateOf(existing?.let { RepeatType.parse(it.repeatType) } ?: RepeatType.NONE)
    }
    var mask by remember(existing?.id) { mutableStateOf(existing?.weekdays ?: 0) }
    var sound by remember(existing?.id) { mutableStateOf(existing?.sound ?: defaultSound) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { SoundPreview.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Yeni görev" else "Görevi düzenle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Sil")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Başlık") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notlar") }, minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Zaman", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                    Text(Fmt.date(date), maxLines = 1)
                }
                OutlinedButton(onClick = { showTime = true }) {
                    Text(Fmt.time(LocalDateTime.of(date, time)))
                }
            }

            Text("Tekrar", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((type, label) in repeatOptions) {
                    FilterChip(
                        selected = repeat == type,
                        onClick = { repeat = type },
                        label = { Text(label) }
                    )
                }
            }
            if (repeat == RepeatType.WEEKLY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (day in DayOfWeek.entries) {
                        FilterChip(
                            selected = Recurrence.has(mask, day),
                            onClick = { mask = mask xor Recurrence.bit(day) },
                            label = { Text(Fmt.dayShort.getValue(day)) }
                        )
                    }
                }
            }

            Text("Ses", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in Sounds.all) {
                    FilterChip(
                        selected = sound == s.key,
                        onClick = { sound = s.key },
                        label = { Text(s.label) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { SoundPreview.play(ctx, Sounds.byKey(sound)) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Sesi dinle")
                }
                Text("Önizleme: ${Sounds.byKey(sound).label}", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val due = Fmt.toMillis(LocalDateTime.of(date, time))
                    val weekly = if (repeat == RepeatType.WEEKLY) mask and 0x7F else 0
                    val base = existing ?: Reminder(title = "", dueAt = due)
                    onSave(
                        base.copy(
                            title = title.trim(),
                            notes = notes.trim(),
                            dueAt = due,
                            repeatType = repeat.name,
                            weekdays = weekly,
                            sound = sound,
                            // Moving a finished one-off to a new time reopens it.
                            done = base.done && due == base.dueAt,
                            doneAt = if (base.done && due == base.dueAt) base.doneAt else null
                        )
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kaydet") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDate = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Vazgeç") } }
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    showTime = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Vazgeç") } },
            text = { TimePicker(state = state) }
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Silinsin mi?") },
            text = { Text(existing.title) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(existing.id) }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }
}
