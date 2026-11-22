package com.kairos.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.res.stringResource
import com.kairos.app.R
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kairos.app.data.Reminder
import com.kairos.app.logic.Limits
import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import com.kairos.app.system.Sounds
import com.kairos.app.system.SoundPreview
import java.time.DayOfWeek
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private val repeatOptions = listOf(
    RepeatType.NONE to R.string.repeat_none,
    RepeatType.DAILY to R.string.repeat_daily,
    RepeatType.WEEKLY to R.string.repeat_weekly,
    RepeatType.MONTHLY to R.string.repeat_monthly
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
    // Saveable, so rotation, a theme switch or process death does not wipe what was typed.
    // The caller keys this screen by reminder id.
    val initial = rememberSaveable {
        (existing?.let { Fmt.local(it.dueAt) } ?: LocalDateTime.now().plusHours(1)).withSecond(0).withNano(0)
    }
    var title by rememberSaveable { mutableStateOf(Limits.clip(existing?.title ?: "", Limits.TITLE)) }
    var notes by rememberSaveable { mutableStateOf(Limits.clip(existing?.notes ?: "", Limits.NOTES)) }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime()) }
    var repeat by rememberSaveable {
        mutableStateOf(existing?.let { RepeatType.parse(it.repeatType) } ?: RepeatType.NONE)
    }
    var mask by rememberSaveable { mutableStateOf(existing?.weekdays ?: 0) }
    var sound by rememberSaveable { mutableStateOf(existing?.sound ?: defaultSound) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // The screen stays tappable while it animates out; only the first Save or Delete counts.
    var sent by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { SoundPreview.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.edit_title_new else R.string.edit_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete))
                        }
                    }
                }
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick = {
                            if (sent) return@Button
                            sent = true
                            // Untouched time: keep the stored one, which moves on if it was completed meanwhile.
                            val untouched = LocalDateTime.of(date, time) == initial
                            val due = if (existing != null && untouched) existing.dueAt else Fmt.toMillis(LocalDateTime.of(date, time))
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
                    ) { Text(stringResource(R.string.save)) }
            }
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
                value = title, onValueChange = { title = Limits.clip(it, Limits.TITLE) },
                label = { Text(stringResource(R.string.field_title)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = Limits.clip(it, Limits.NOTES) },
                label = { Text(stringResource(R.string.field_notes)) }, minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.label_time), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                    Text(Fmt.date(ctx, date), maxLines = 1)
                }
                OutlinedButton(onClick = { showTime = true }) {
                    Text(Fmt.time(ctx, LocalDateTime.of(date, time)))
                }
            }

            Text(stringResource(R.string.label_repeat), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((type, labelRes) in repeatOptions) {
                    FilterChip(
                        selected = repeat == type,
                        onClick = { repeat = type },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }
            if (repeat == RepeatType.WEEKLY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (day in DayOfWeek.entries) {
                        FilterChip(
                            selected = Recurrence.has(mask, day),
                            onClick = { mask = mask xor Recurrence.bit(day) },
                            label = { Text(Fmt.dayShort(ctx, day)) }
                        )
                    }
                }
            }

            Text(stringResource(R.string.label_sound), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in Sounds.all) {
                    FilterChip(
                        selected = sound == s.key,
                        onClick = { sound = s.key },
                        label = { Text(stringResource(s.label)) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { SoundPreview.play(ctx, Sounds.byKey(sound)) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_play_sound))
                }
                Text(stringResource(R.string.sound_preview, stringResource(Sounds.byKey(sound).label)), style = MaterialTheme.typography.bodyMedium)
            }

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
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) }
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(Limits.clip(existing.title, Limits.TITLE)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; if (!sent) { sent = true; onDelete(existing.id) } }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
