package com.kairos.app.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kairos.app.R


import androidx.compose.ui.unit.dp
import com.kairos.app.data.Reminder
import com.kairos.app.logic.ToneSet
import com.kairos.app.system.PermKind
import com.kairos.app.system.openPermissionSettings
import com.kairos.app.system.rememberPerms
import androidx.compose.material3.TextButton
import java.time.LocalDate

@Composable
fun TasksScreen(
    reminders: List<Reminder>,
    tone: ToneSet,
    name: String,
    padding: PaddingValues,
    onToggle: (Reminder) -> Unit,
    onOpen: (Reminder) -> Unit
) {
    val ctx = LocalContext.current
    val perms = rememberPerms()
    val today = LocalDate.now()
    val titleToday = stringResource(R.string.section_today)
    val titleUpcoming = stringResource(R.string.section_upcoming)
    val titleDone = stringResource(R.string.section_done)
    val open = reminders.filter { !it.done }
    val dueToday = open.filter { !Fmt.local(it.dueAt).toLocalDate().isAfter(today) }
    val upcoming = open.filter { Fmt.local(it.dueAt).toLocalDate().isAfter(today) }
    val done = reminders.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "greeting") {
            Text(
                tone.greeting(name, today.dayOfYear),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fadeIn().padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
        if (perms.reminderIssues) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tone.permissionBanner, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        TextButton(onClick = {
                            val kind = when {
                                !perms.notifications -> PermKind.NOTIFICATIONS
                                !perms.exactAlarm -> PermKind.EXACT_ALARM
                                else -> PermKind.FULL_SCREEN
                            }
                            openPermissionSettings(ctx, kind)
                        }) { Text(stringResource(R.string.fix_permissions)) }
                    }
                }
            }
        }
        section(titleToday, dueToday, tone.emptyToday, onToggle, onOpen)
        section(titleUpcoming, upcoming, tone.emptyUpcoming, onToggle, onOpen)
        section(titleDone, done, tone.emptyDone, onToggle, onOpen)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<Reminder>,
    empty: String,
    onToggle: (Reminder) -> Unit,
    onOpen: (Reminder) -> Unit
) {
    item(key = "header-$title") {
        Row(
            Modifier.animateItem().padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                "  ${list.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (list.isEmpty()) {
        item(key = "empty-$title") {
            Text(
                empty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.animateItem().padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    } else {
        items(list, key = { "r-${it.id}" }) { r -> ReminderRow(r, onToggle, onOpen, Modifier.animateItem()) }
    }
}

@Composable
fun ReminderRow(
    r: Reminder,
    onToggle: (Reminder) -> Unit,
    onOpen: (Reminder) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val source = remember { MutableInteractionSource() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().pressScale(source)
            .clickable(interactionSource = source, indication = LocalIndication.current) { onOpen(r) }
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = r.done, onCheckedChange = { onToggle(r) })
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                StrikeText(
                    r.title,
                    struck = r.done,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Text(
                    Fmt.subtitle(ctx, r),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
