package com.kairos.app.data

import android.content.Context
import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import com.kairos.app.logic.SnoozeMachine
import com.kairos.app.system.CalendarSync
import com.kairos.app.system.Notifier
import com.kairos.app.system.Scheduler
import com.kairos.app.widget.Widget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Single entry point that keeps Room, alarms, notifications and the system calendar in sync. */
class Repo(context: Context) {
    private val ctx = context.applicationContext
    private val dao = AppDatabase.get(ctx).dao()
    val prefs = Prefs(ctx)

    fun observe(): Flow<List<Reminder>> = dao.observeAll()

    suspend fun save(input: Reminder) {
        val zone = ZoneId.systemDefault()
        val toSave = input.copy(
            anchorDay = Instant.ofEpochMilli(input.dueAt).atZone(zone).dayOfMonth,
            snoozeCount = 0
        )
        val id = if (toSave.id == 0) dao.insert(toSave).toInt() else {
            dao.update(toSave)
            toSave.id
        }
        val saved = toSave.copy(id = id)
        Notifier.cancelAll(ctx, id)
        Scheduler.schedule(ctx, saved) // cancels old alarms first; no-op for done reminders
        syncCalendar(saved)
        Widget.refresh(ctx)
    }

    suspend fun delete(id: Int) {
        val r = dao.get(id) ?: return
        Notifier.cancelAll(ctx, id)
        Scheduler.cancel(ctx, id)
        r.calendarEventId?.let { withContext(Dispatchers.IO) { CalendarSync.deleteEvent(ctx, it) } }
        dao.delete(id)
        Widget.refresh(ctx)
    }

    /** "Done": clears notifications; repeating reminders move on to their next occurrence. */
    suspend fun complete(id: Int) {
        val r = dao.get(id) ?: return
        Notifier.cancelAll(ctx, id)
        Scheduler.cancel(ctx, id)
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(r.dueAt).atZone(zone).toLocalDateTime()
        val next = Recurrence.nextAfter(
            current, LocalDateTime.now(zone), RepeatType.parse(r.repeatType), r.weekdays, r.anchorDay
        )
        val updated = if (next == null) {
            r.copy(done = true, doneAt = System.currentTimeMillis(), snoozeCount = 0)
        } else {
            r.copy(dueAt = next.atZone(zone).toInstant().toEpochMilli(), snoozeCount = 0)
        }
        dao.update(updated)
        if (!updated.done) Scheduler.schedule(ctx, updated)
        syncCalendar(updated)
        Widget.refresh(ctx)
    }

    suspend fun reopen(id: Int) {
        val r = dao.get(id) ?: return
        val updated = r.copy(done = false, doneAt = null, snoozeCount = 0)
        dao.update(updated)
        Scheduler.schedule(ctx, updated)
        syncCalendar(updated)
        Widget.refresh(ctx)
    }

    /** Alarm for a chain stage. Stage 3 is a snoozed re-fire of stage 2. */
    suspend fun fire(id: Int, rawStage: Int) {
        val r = dao.get(id) ?: return
        if (r.done) return
        val stage = if (rawStage == Scheduler.SNOOZE_STAGE) 2 else rawStage
        if (stage > 2) return
        val settings = prefs.settings.first()
        if (stage == 1) Notifier.cancel(ctx, id, 0) // the heads-up is obsolete once T arrives
        Notifier.post(ctx, r, stage, settings)
    }

    /** deleteIntent: the user swiped a notification away, so put it straight back. */
    suspend fun repost(id: Int, stage: Int) {
        val r = dao.get(id) ?: return
        if (r.done) return
        if (stage == 0 && System.currentTimeMillis() >= r.dueAt) return
        Notifier.post(ctx, r, stage, prefs.settings.first(), silent = true)
    }

    suspend fun handleAction(id: Int, action: SnoozeMachine.Action) {
        val r = dao.get(id) ?: return
        val outcome = SnoozeMachine.apply(r.snoozeCount, action)
        when {
            outcome.done -> complete(id)
            outcome.delayMinutes != null -> {
                dao.update(r.copy(snoozeCount = outcome.newCount))
                Notifier.cancelAll(ctx, id)
                Scheduler.scheduleSnooze(ctx, id, System.currentTimeMillis() + outcome.delayMinutes * 60_000L)
            }
        }
    }

    suspend fun rescheduleAll() {
        dao.getAll().filter { !it.done }.forEach { Scheduler.schedule(ctx, it) }
        Widget.refresh(ctx)
    }

    // ---- Hardcore mode (system calendar mirror) ----

    private suspend fun syncCalendar(r: Reminder) {
        if (!prefs.settings.first().hardcore) return
        syncOne(r)
    }

    private suspend fun syncOne(r: Reminder) {
        val eventId = withContext(Dispatchers.IO) { CalendarSync.upsert(ctx, r) }
        if (eventId != null && eventId != r.calendarEventId) {
            dao.update(r.copy(calendarEventId = eventId))
        }
    }

    suspend fun syncAllToCalendar() {
        dao.getAll().forEach { syncOne(it) }
    }

    suspend fun removeAllFromCalendar() {
        withContext(Dispatchers.IO) { CalendarSync.removeCalendar(ctx) }
        dao.getAll().filter { it.calendarEventId != null }.forEach {
            dao.update(it.copy(calendarEventId = null))
        }
    }
}
