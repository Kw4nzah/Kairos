package com.kairos.app.data

import android.content.Context
import android.os.SystemClock
import com.kairos.app.logic.Chain
import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import com.kairos.app.logic.SnoozeMachine
import com.kairos.app.logic.WallClock
import com.kairos.app.system.CalendarSync
import com.kairos.app.system.Notifier
import com.kairos.app.system.Scheduler
import com.kairos.app.widget.Widget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    suspend fun save(input: Reminder): Unit = writes.withLock {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(input.dueAt).atZone(zone).toLocalDate()
        val before = if (input.id == 0) null else dao.get(input.id)
        // Feb 28 may be a clamped "31st". Editing the text alone must not turn it into "the 28th".
        val sameDay = before != null && Instant.ofEpochMilli(before.dueAt).atZone(zone).toLocalDate() == day
        val toSave = input.copy(
            anchorDay = if (sameDay && before?.repeatType == input.repeatType) before.anchorDay else day.dayOfMonth,
            // An unchanged time keeps its wall time, e.g. 02:30 on a DST-gap day that shows as 03:30.
            dueLocal = if (before != null && before.dueAt == input.dueAt && before.dueLocal != null) {
                before.dueLocal
            } else {
                WallClock.of(input.dueAt, zone)
            },
            snoozeCount = 0,
            snoozeUntil = null
        )
        val id = if (toSave.id == 0) dao.insert(toSave).toInt() else {
            dao.update(toSave)
            toSave.id
        }
        val saved = toSave.copy(id = id)
        Notifier.cancelAll(ctx, id)
        Scheduler.schedule(ctx, saved) // cancels old alarms first; no-op for done reminders
        syncCalendar(saved)
        armNearest()
        Widget.refresh(ctx)
    }

    suspend fun delete(id: Int): Unit = writes.withLock {
        val r = dao.get(id) ?: return@withLock
        Notifier.cancelAll(ctx, id)
        Scheduler.cancel(ctx, id)
        r.calendarEventId?.let { withContext(Dispatchers.IO) { CalendarSync.deleteEvent(ctx, it) } }
        dao.delete(id)
        armNearest()
        Widget.refresh(ctx)
    }

    /**
     * "Done": clears notifications; repeating reminders move on to their next occurrence.
     * [shownDue] is the occurrence the user was looking at, so a second tap cannot skip the one after it.
     */
    suspend fun complete(id: Int, shownDue: Long? = null): Unit = writes.withLock {
        val r = dao.get(id) ?: return@withLock
        if (r.done || (shownDue != null && shownDue != r.dueAt)) return@withLock
        // The list redraws with the next occurrence before a double tap's second half lands on it.
        val tappedAt = SystemClock.elapsedRealtime()
        completedAt[id]?.let { if (tappedAt - it < DOUBLE_TAP_MS) return@withLock }
        completeLocked(r)
        if (RepeatType.parse(r.repeatType) != RepeatType.NONE) completedAt[id] = SystemClock.elapsedRealtime()
    }

    private suspend fun completeLocked(r: Reminder) {
        val id = r.id
        Notifier.cancelAll(ctx, id)
        Scheduler.cancel(ctx, id)
        val zone = ZoneId.systemDefault()
        // From the picked clock time, so the next one is at that time in the zone the phone is in now.
        val current = WallClock.local(r.dueLocal, r.dueAt, zone)
        val next = Recurrence.nextAfter(
            current, LocalDateTime.now(zone), RepeatType.parse(r.repeatType), r.weekdays, r.anchorDay
        )
        val updated = if (next == null) {
            r.copy(done = true, doneAt = System.currentTimeMillis(), snoozeCount = 0, snoozeUntil = null)
        } else {
            r.copy(
                dueAt = WallClock.toMillis(next, zone),
                dueLocal = next.toString(),
                snoozeCount = 0,
                snoozeUntil = null
            )
        }
        dao.update(updated)
        if (!updated.done) Scheduler.schedule(ctx, updated)
        syncCalendar(updated)
        armNearest()
        Widget.refresh(ctx)
    }

    suspend fun reopen(id: Int): Unit = writes.withLock {
        val r = dao.get(id) ?: return@withLock
        if (!r.done) return@withLock
        val updated = r.copy(done = false, doneAt = null, snoozeCount = 0, snoozeUntil = null)
        dao.update(updated)
        Scheduler.schedule(ctx, updated)
        syncCalendar(updated)
        armNearest()
        Widget.refresh(ctx)
    }

    /** Alarm for a chain stage. Stage 3 is a snoozed re-fire of stage 2. */
    suspend fun fire(id: Int, rawStage: Int): Unit = writes.withLock {
        // First, so that a failure below cannot stop the next reminders from being armed.
        armNearest()
        val r = dao.get(id) ?: return@withLock
        if (r.done || !Chain.shouldFire(rawStage, r.dueAt, r.snoozeUntil, System.currentTimeMillis())) return@withLock
        val stage = if (rawStage == Chain.SNOOZE_STAGE) 2 else rawStage
        val settings = prefs.settings.first()
        if (stage == 1) Notifier.cancel(ctx, id, 0) // the heads-up is obsolete once T arrives
        if (stage == 2) Notifier.cancel(ctx, id, 1, closePopup = false) // the check replaces the main alert (and its ringing)
        Notifier.post(ctx, r, stage, settings)
        Widget.refresh(ctx) // it just became due
    }

    /** deleteIntent: the user swiped a notification away, so put it straight back. */
    suspend fun repost(id: Int, stage: Int): Unit = writes.withLock {
        val r = dao.get(id) ?: return@withLock
        if (r.done) return@withLock
        val now = System.currentTimeMillis()
        if (stage == 0 && now >= r.dueAt) return@withLock
        if (stage >= 1 && r.snoozeUntil == null && now < r.dueAt) return@withLock // an earlier occurrence
        if (r.snoozeUntil != null && r.snoozeUntil > now) return@withLock
        Notifier.post(ctx, r, stage, prefs.settings.first(), silent = stage < 1)
    }

    suspend fun handleAction(id: Int, action: SnoozeMachine.Action, shownDue: Long?, shownCount: Int?): Unit = writes.withLock {
        val r = dao.get(id) ?: return@withLock
        if (r.done) return@withLock
        if (shownDue != null && shownDue != r.dueAt) return@withLock
        if (shownCount != null && shownCount != r.snoozeCount) return@withLock
        val outcome = SnoozeMachine.apply(r.snoozeCount, action)
        when {
            outcome.done -> completeLocked(r)
            outcome.delayMinutes != null -> {
                val snoozed = r.copy(
                    snoozeCount = outcome.newCount,
                    snoozeUntil = System.currentTimeMillis() + outcome.delayMinutes * 60_000L
                )
                dao.update(snoozed)
                Notifier.cancelAll(ctx, id)
                Scheduler.schedule(ctx, snoozed) // drops the rest of the chain
                Widget.refresh(ctx)
            }
        }
    }

    suspend fun rescheduleAll(): Unit = writes.withLock {
        followZone() // the zone may have changed while the phone was off
        val now = System.currentTimeMillis()
        val (waiting, active) = dao.getAll().filter { !it.done }.sortedBy { it.dueAt }
            .partition { Chain.isWaiting(it.dueAt, it.snoozeUntil, now) }
        // Free the far ones first, so the near ones always fit under the limit.
        waiting.drop(Chain.MAX_WAITING).forEach { Scheduler.cancel(ctx, it.id) }
        (active + waiting.take(Chain.MAX_WAITING)).forEach { Scheduler.schedule(ctx, it) }
        // Up to 1.4.2 a finished one-off left its event, and its alarms, in the calendar.
        if (prefs.settings.first().hardcore) {
            dao.getAll().filter { it.done && it.calendarEventId != null }.forEach { syncOne(it) }
        }
        Widget.refresh(ctx)
    }

    /**
     * Clock or zone change. Pending alarms are wall-clock instants and survive it; only reminders that have
     * not started move to their clock time in the new zone (see [WallClock.follow]).
     */
    suspend fun onClockChanged(): Unit = writes.withLock {
        val moved = followZone()
        // Under the limit each moved one is armed here; above it armNearest picks the nearest.
        if (dao.getAll().count { !it.done } < Chain.MAX_WAITING) moved.forEach { Scheduler.schedule(ctx, it) }
        armNearest()
        Widget.refresh(ctx)
    }

    /** Moves waiting reminders to their clock time in the current zone; returns them with alarms cancelled. */
    private suspend fun followZone(): List<Reminder> {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val moved = dao.getAll().filter { !it.done }.mapNotNull { r ->
            WallClock.follow(r.dueLocal, r.dueAt, r.snoozeUntil, zone, now)?.let { r.copy(dueAt = it) }
        }
        for (r in moved) {
            Scheduler.cancel(ctx, r.id)
            dao.update(r)
            syncCalendar(r)
        }
        return moved
    }

    /** Keeps the pending alarms under Android's limit (see [Chain.MAX_WAITING]); cheap for normal list sizes. */
    private suspend fun armNearest() {
        val now = System.currentTimeMillis()
        val open = dao.getAll().filter { !it.done }
        // Counts all open ones, not just waiting: several can start at once and all make room together.
        if (open.size < Chain.MAX_WAITING) return
        val waiting = open.filter { Chain.isWaiting(it.dueAt, it.snoozeUntil, now) }.sortedBy { it.dueAt }
        waiting.drop(Chain.MAX_WAITING).forEach { Scheduler.cancel(ctx, it.id) }
        waiting.take(Chain.MAX_WAITING).forEach { Scheduler.schedule(ctx, it) }
    }

    private companion object {
        /** One writer at a time: receivers, the widget and the UI each have their own Repo. */
        val writes = Mutex()

        const val DOUBLE_TAP_MS = 1_000L
        val completedAt = HashMap<Int, Long>() // guarded by writes
    }

    // ---- Hardcore mode (system calendar mirror) ----

    private suspend fun syncCalendar(r: Reminder) {
        if (!prefs.settings.first().hardcore) return
        syncOne(r)
    }

    private suspend fun syncOne(r: Reminder) {
        if (r.done) {
            // A finished one-off must not keep ringing through the calendar.
            val stored = r.calendarEventId ?: return
            withContext(Dispatchers.IO) { CalendarSync.deleteEvent(ctx, stored) }
            dao.setCalendarEventId(r.id, null)
            return
        }
        val eventId = withContext(Dispatchers.IO) { CalendarSync.upsert(ctx, r) }
        if (eventId != null && eventId != r.calendarEventId) {
            dao.setCalendarEventId(r.id, eventId)
        }
    }

    suspend fun syncAllToCalendar() {
        // Start clean: turning it off without calendar permission leaves the old events, which would double up.
        removeAllFromCalendar()
        // Lock per reminder: holding it for the whole list would hold up alarms.
        for (id in dao.getAll().map { it.id }) writes.withLock { dao.get(id)?.let { syncOne(it) } }
    }

    suspend fun removeAllFromCalendar(): Unit = writes.withLock {
        withContext(Dispatchers.IO) { CalendarSync.removeCalendar(ctx) }
        dao.getAll().filter { it.calendarEventId != null }.forEach {
            dao.setCalendarEventId(it.id, null)
        }
    }
}
