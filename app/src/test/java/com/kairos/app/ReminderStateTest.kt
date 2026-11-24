package com.kairos.app

import com.kairos.app.data.Reminder
import com.kairos.app.data.ReminderState
import com.kairos.app.logic.Chain
import com.kairos.app.logic.RepeatType
import com.kairos.app.logic.SnoozeMachine.Action
import com.kairos.app.logic.WallClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderStateTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val min = 60_000L
    private fun at(local: String) = WallClock.toMillis(LocalDateTime.parse(local), zone)

    private val daily = Reminder(
        id = 1, title = "Gym", notes = "secret", dueAt = at("2026-11-25T08:00"), dueLocal = "2026-11-25T08:00",
        repeatType = RepeatType.DAILY.name, anchorDay = 25, calendarEventId = 9L
    )

    @Test fun snoozeSetsTheReFire() {
        val now = daily.dueAt + 2 * min
        val r = ReminderState.answer(daily, Action.SNOOZE_5, daily.dueAt, 0, now, zone)!!
        assertEquals(1, r.snoozeCount)
        assertEquals(now + 5 * min, r.snoozeUntil)
        assertEquals(daily.dueAt, r.dueAt)
    }

    /** "Done" is offered on the notification after the third snooze. */
    private val nagging = daily.copy(snoozeCount = 3, snoozeUntil = daily.dueAt + 30 * min)

    @Test fun doneBeforeThirdSnoozeIsIgnored() {
        assertNull(ReminderState.answer(daily, Action.DONE, daily.dueAt, 0, daily.dueAt + min, zone))
    }

    @Test fun doneMovesRepeatToNextDay() {
        val r = ReminderState.answer(nagging, Action.DONE, daily.dueAt, 3, daily.dueAt + 31 * min, zone)!!
        assertEquals(at("2026-11-26T08:00"), r.dueAt)
        assertEquals("2026-11-26T08:00", r.dueLocal)
        assertEquals(0, r.snoozeCount)
        assertNull(r.snoozeUntil)
    }

    @Test fun doneFinishesOneOff() {
        val once = nagging.copy(repeatType = RepeatType.NONE.name)
        val now = once.dueAt + min
        val r = ReminderState.answer(once, Action.DONE, null, null, now, zone)!!
        assertTrue(r.done)
        assertEquals(now, r.doneAt)
    }

    @Test fun stalePressesAreIgnored() {
        val now = daily.dueAt + min
        assertNull(ReminderState.answer(daily, Action.DONE, daily.dueAt - 1, 0, now, zone)) // other occurrence
        assertNull(ReminderState.answer(daily, Action.SNOOZE_5, daily.dueAt, 1, now, zone)) // other round
        assertNull(ReminderState.answer(nagging.copy(done = true), Action.DONE, null, null, now, zone))
        // A second "done" from the same notification lands on the next occurrence and does nothing.
        val next = ReminderState.answer(nagging, Action.DONE, daily.dueAt, 3, now, zone)!!
        assertNull(ReminderState.answer(next, Action.DONE, daily.dueAt, 3, now, zone))
    }

    @Test fun lockedCopyKeepsActiveAndNearestWaitingWithoutNotes() {
        val now = at("2026-11-24T12:00")
        val ringing = daily.copy(id = 2, dueAt = now - min)
        val snoozed = daily.copy(id = 3, dueAt = now - 30 * min, snoozeUntil = now + 5 * min)
        val finished = daily.copy(id = 4, done = true)
        val waiting = (100 until 100 + Chain.MAX_WAITING + 5).map { daily.copy(id = it, dueAt = now + it * 60 * min) }
        val copy = ReminderState.forLockedMode(waiting.reversed() + listOf(ringing, snoozed, finished), now)
        val ids = copy.map { it.id }
        assertTrue(2 in ids && 3 in ids && 4 !in ids)
        assertEquals(Chain.MAX_WAITING + 2, copy.size)
        assertTrue((100 until 100 + Chain.MAX_WAITING).all { it in ids })
        assertTrue(copy.all { it.notes.isEmpty() && it.calendarEventId == null })
        assertEquals("Gym", copy.first { it.id == 2 }.title)
    }
}
