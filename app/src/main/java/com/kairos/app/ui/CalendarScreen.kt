package com.kairos.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kairos.app.R
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
    val ctx = LocalContext.current
    val firstDay = Fmt.firstDayOfWeek(ctx)
    val weekDays = DayOfWeek.entries.let { all -> all.drop(firstDay.ordinal) + all.take(firstDay.ordinal) }

    val dayItems = remember(reminders, selected) {
        reminders.filter { occurs(it, selected) }.sortedBy { Fmt.local(it.dueAt).toLocalTime() }
    }

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
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cal_prev_month))
                }
                AnimatedContent(
                    targetState = monthIndex,
                    transitionSpec = { monthTransition(targetState > initialState) },
                    label = "monthTitle"
                ) { mi ->
                    Text(
                        Fmt.month(ctx, YearMonth.of(mi / 12, mi % 12 + 1).atDay(1)),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = { monthIndex += 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cal_next_month))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                for (d in weekDays) {
                    Text(
                        Fmt.dayShort(ctx, d),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            AnimatedContent(
                targetState = monthIndex,
                transitionSpec = { monthTransition(targetState > initialState) },
                label = "monthGrid"
            ) { mi ->
            val month = YearMonth.of(mi / 12, mi % 12 + 1)
            val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
            val cells: List<LocalDate?> =
                List<LocalDate?>(offset) { null } + (1..month.lengthOfMonth()).map<Int, LocalDate?> { month.atDay(it) }
            val padded: List<LocalDate?> = cells + List<LocalDate?>((7 - cells.size % 7) % 7) { null }
            val marked = remember(reminders, month) {
                (1..month.lengthOfMonth()).map { month.atDay(it) }.filterTo(HashSet()) { d -> reminders.any { occurs(it, d) } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (week in padded.chunked(7)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in week) {
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (d != null) DayCell(
                                    day = d,
                                    isSelected = d == selected,
                                    isToday = d == today,
                                    hasItems = d in marked,
                                    onClick = { selectedEpochDay = d.toEpochDay() }
                                )
                            }
                        }
                    }
                }
            }
            }
        }
        item {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(tween(DurMed, easing = EaseOut)) togetherWith fadeOut(tween(DurShort)) },
                label = "selectedDate",
                modifier = Modifier.padding(top = 16.dp, start = 4.dp)
            ) { d ->
                Text(Fmt.date(ctx, d), style = MaterialTheme.typography.titleMedium)
            }
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
                val source = remember { MutableInteractionSource() }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.animateItem().fillMaxWidth().pressScale(source)
                        .clickable(interactionSource = source, indication = LocalIndication.current) { onOpen(r) }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Fmt.time(ctx, Fmt.local(r.dueAt)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        StrikeText(
                            r.title,
                            struck = r.done,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun monthTransition(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    return (fadeIn(tween(DurMed, delayMillis = 40, easing = EaseOut)) +
        slideInHorizontally(tween(DurMed, easing = EaseOut)) { dir * it / 8 }) togetherWith
        (fadeOut(tween(DurShort)) + slideOutHorizontally(tween(DurMed, easing = EaseOut)) { -dir * it / 8 })
}

@Composable
private fun DayCell(day: LocalDate, isSelected: Boolean, isToday: Boolean, hasItems: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val bg by animateColorAsState(
        if (isSelected) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f),
        tween(DurShort, easing = EaseOut), label = "dayBg"
    )
    val fg by animateColorAsState(
        if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
        tween(DurShort, easing = EaseOut), label = "dayFg"
    )
    val source = remember { MutableInteractionSource() }
    var mod = Modifier.fillMaxWidth().aspectRatio(1f).pressScale(source, 0.92f).background(bg, shape)
    mod = if (isToday) mod.border(1.5.dp, scheme.primary, shape) else mod
    Box(
        mod.clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = fg
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
