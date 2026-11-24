package com.kairos.app.data

import com.kairos.app.logic.Chain
import com.kairos.app.logic.Limits
import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import com.kairos.app.logic.SnoozeMachine
import com.kairos.app.logic.WallClock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** State changes of a single reminder, shared by [Repo] and the before-first-unlock path. Pure. */
object ReminderState {
    /** "Done": a repeating reminder moves to its next occurrence at its clock time, a one-off is finished. */
    fun complete(r: Reminder, now: Long, zone: ZoneId): Reminder {
        val next = Recurrence.nextAfter(
            WallClock.local(r.dueLocal, r.dueAt, zone),
            LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone),
            RepeatType.parse(r.repeatType), r.weekdays, r.anchorDay
        )
        return if (next == null) {
            r.copy(done = true, doneAt = now, snoozeCount = 0, snoozeUntil = null)
        } else {
            r.copy(dueAt = WallClock.toMillis(next, zone), dueLocal = next.toString(), snoozeCount = 0, snoozeUntil = null)
        }
    }

    /**
     * A snooze / done button pressed on [r], or null when the press is stale: drawn for another occurrence
     * or snooze round, or the reminder is already finished.
     */
    fun answer(
        r: Reminder,
        action: SnoozeMachine.Action,
        shownDue: Long?,
        shownCount: Int?,
        now: Long,
        zone: ZoneId
    ): Reminder? {
        if (r.done) return null
        if (shownDue != null && shownDue != r.dueAt) return null
        if (shownCount != null && shownCount != r.snoozeCount) return null
        val outcome = SnoozeMachine.apply(r.snoozeCount, action)
        return when {
            outcome.done -> complete(r, now, zone)
            outcome.delayMinutes != null ->
                r.copy(snoozeCount = outcome.newCount, snoozeUntil = now + outcome.delayMinutes * 60_000L)
            else -> null
        }
    }

    /**
     * What is kept for ringing before the first unlock: open reminders that are ringing or snoozed, plus the
     * nearest [Chain.MAX_WAITING] waiting ones. Titles are clipped; notes are never kept.
     */
    fun forLockedMode(all: List<Reminder>, now: Long): List<Reminder> {
        val (waiting, active) = all.filter { !it.done }.sortedBy { it.dueAt }
            .partition { Chain.isWaiting(it.dueAt, it.snoozeUntil, now) }
        return (active + waiting.take(Chain.MAX_WAITING)).map {
            it.copy(title = Limits.clip(it.title, Limits.TITLE), notes = "", calendarEventId = null)
        }
    }
}
