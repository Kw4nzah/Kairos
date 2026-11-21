package com.kairos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kairos.app.data.Reminder
import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import com.kairos.app.logic.ToneSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private fun occurs(r: Reminder, date: LocalDate): Boolean = Recurrence.occursOn(
    Fmt.local(r.dueAt).toLocalDate(), RepeatType.parse(r.repeatType), r.weekdays, r.anchorDay, date
)

@Composable
fun CalendarScreen(
    reminders: List<Reminder>,
    tone: ToneSet,
    padding: PaddingValues,
    onOpen: (Reminder) -> Unit
) {
    // Both values are stored as plain numbers so they survive rotation.
    var monthIndex by rememberSaveable { mutableStateOf(YearMonth.now().let { it.year * 12 + it.monthValue - 1 }) }
    var selectedEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    val today = LocalDate.now()

    val dayItems = reminders.filter { occurs(it, selected) }
        .sortedBy { Fmt.local(it.dueAt).toLocalTime() }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { monthIndex -= 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki ay")
                }
                Text(Fmt.month(month.atDay(1)), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { monthIndex += 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki ay")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                for (d in DayOfWeek.entries) {
                    Text(
                        Fmt.dayShort.getValue(d),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells: List<LocalDate?> =
                List<LocalDate?>(offset) { null } + (1..month.lengthOfMonth()).map<Int, LocalDate?> { month.atDay(it) }
            val padded: List<LocalDate?> = cells + List<LocalDate?>((7 - cells.size % 7) % 7) { null }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (week in padded.chunked(7)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in week) {
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (d != null) DayCell(
                                    day = d,
                                    isSelected = d == selected,
                                    isToday = d == today,
                                    hasItems = reminders.any { occurs(it, d) },
                                    onClick = { selectedEpochDay = d.toEpochDay() }
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                Fmt.date(selected),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, start = 4.dp)
            )
        }
        if (dayItems.isEmpty()) {
            item {
                Text(
                    tone.emptyDay,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        } else {
            items(dayItems, key = { "c-${it.id}" }) { r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(r) }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Fmt.time(Fmt.local(r.dueAt)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            r.title,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (r.done) TextDecoration.LineThrough else null,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, isSelected: Boolean, isToday: Boolean, hasItems: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    var mod = Modifier.fillMaxWidth().aspectRatio(1f)
    mod = if (isSelected) mod.background(scheme.primaryContainer, shape) else mod
    mod = if (isToday) mod.border(1.5.dp, scheme.primary, shape) else mod
    Box(mod.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface
        )
        if (hasItems) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(5.dp)
                    .background(scheme.secondary, CircleShape)
            )
        }
    }
}
