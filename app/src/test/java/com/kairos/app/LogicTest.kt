package com.kairos.app

import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import com.kairos.app.logic.SnoozeMachine
import com.kairos.app.logic.SnoozeMachine.Action
import com.kairos.app.logic.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class LogicTest {
    private fun dt(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    // ---- Recurrence ----

    @Test fun noneHasNoNext() {
        assertNull(Recurrence.next(dt(2026, 1, 5), RepeatType.NONE, 0, 5))
    }

    @Test fun dailyAddsOneDay() {
        assertEquals(dt(2026, 1, 6), Recurrence.next(dt(2026, 1, 5), RepeatType.DAILY, 0, 5))
    }

    @Test fun weeklyPicksNextSelectedWeekday() {
        // 2026-01-05 is a Monday; Mon+Wed+Fri selected.
        val mask = Recurrence.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals(dt(2026, 1, 7), Recurrence.next(dt(2026, 1, 5), RepeatType.WEEKLY, mask, 5))
        assertEquals(dt(2026, 1, 9), Recurrence.next(dt(2026, 1, 7), RepeatType.WEEKLY, mask, 7))
        assertEquals(dt(2026, 1, 12), Recurrence.next(dt(2026, 1, 9), RepeatType.WEEKLY, mask, 9))
    }

    @Test fun weeklySingleDayRepeatsAfterSevenDays() {
        val mask = Recurrence.maskOf(listOf(DayOfWeek.MONDAY))
        assertEquals(dt(2026, 1, 12), Recurrence.next(dt(2026, 1, 5), RepeatType.WEEKLY, mask, 5))
    }

    @Test fun weeklyWithEmptyMaskFallsBackToOwnWeekday() {
        assertEquals(dt(2026, 1, 12), Recurrence.next(dt(2026, 1, 5), RepeatType.WEEKLY, 0, 5))
    }

    @Test fun monthlyClampsToShortMonthsAndKeepsAnchor() {
        val feb = Recurrence.next(dt(2026, 1, 31), RepeatType.MONTHLY, 0, 31)
        assertEquals(dt(2026, 2, 28), feb)
        assertEquals(dt(2026, 3, 31), Recurrence.next(feb!!, RepeatType.MONTHLY, 0, 31))
    }

    @Test fun monthlyCalendarRuleClampsLikeTheApp() {
        assertEquals("FREQ=MONTHLY", Recurrence.monthlyRule(5))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=29", Recurrence.monthlyRule(29))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=-1", Recurrence.monthlyRule(31))
    }

    @Test fun nextAfterSkipsMissedOccurrences() {
        val next = Recurrence.nextAfter(dt(2026, 1, 1), dt(2026, 1, 10, 12), RepeatType.DAILY, 0, 1)
        assertEquals(dt(2026, 1, 11), next)
    }

    @Test fun occursOnMatchesRules() {
        val due = LocalDate.of(2026, 1, 5) // Monday
        assertTrue(Recurrence.occursOn(due, RepeatType.NONE, 0, 5, due))
        assertFalse(Recurrence.occursOn(due, RepeatType.NONE, 0, 5, due.plusDays(1)))
        assertFalse(Recurrence.occursOn(due, RepeatType.DAILY, 0, 5, due.minusDays(1)))
        assertTrue(Recurrence.occursOn(due, RepeatType.DAILY, 0, 5, due.plusDays(40)))
        val mask = Recurrence.maskOf(listOf(DayOfWeek.TUESDAY))
        assertTrue(Recurrence.occursOn(due, RepeatType.WEEKLY, mask, 5, LocalDate.of(2026, 1, 13)))
        assertFalse(Recurrence.occursOn(due, RepeatType.WEEKLY, mask, 5, LocalDate.of(2026, 1, 12)))
        assertTrue(Recurrence.occursOn(due, RepeatType.MONTHLY, 0, 5, LocalDate.of(2026, 3, 5)))
    }

    // ---- Snooze state machine ----

    @Test fun firstThreeStatesOfferBothSnoozes() {
        for (count in 0..2) {
            assertEquals(listOf(Action.SNOOZE_5, Action.SNOOZE_10), SnoozeMachine.actions(count))
        }
    }

    @Test fun afterThreeSnoozesOnlyLongSnoozeAndDone() {
        assertEquals(listOf(Action.SNOOZE_10, Action.DONE), SnoozeMachine.actions(3))
        assertEquals(listOf(Action.SNOOZE_10, Action.DONE), SnoozeMachine.actions(7))
    }

    @Test fun snoozeIncrementsCounterAndSetsDelay() {
        assertEquals(SnoozeMachine.Outcome(1, 5, false), SnoozeMachine.apply(0, Action.SNOOZE_5))
        assertEquals(SnoozeMachine.Outcome(3, 10, false), SnoozeMachine.apply(2, Action.SNOOZE_10))
    }

    @Test fun onlyDoneFinishesAndStaleActionsAreIgnored() {
        assertEquals(SnoozeMachine.Outcome(3, null, true), SnoozeMachine.apply(3, Action.DONE))
        assertEquals(SnoozeMachine.Outcome(0, null, false), SnoozeMachine.apply(0, Action.DONE))
        assertEquals(SnoozeMachine.Outcome(3, null, false), SnoozeMachine.apply(3, Action.SNOOZE_5))
    }

    // ---- Tone ----

    @Test fun toneVariantPickingIsStableAndInRange() {
        for (seed in -50..50) {
            val i = Tone.pickIndex(3, seed)
            assertTrue(i in 0..2)
            assertEquals(i, Tone.pickIndex(3, seed))
        }
        assertEquals(1, Tone.pickIndex(3, 1))
        assertEquals(2, Tone.pickIndex(3, -1))
    }

    @Test
    fun updaterVersionComparison() {
        assertTrue(com.kairos.app.system.Updater.isNewer("1.3.0", "1.2.0"))
        assertTrue(com.kairos.app.system.Updater.isNewer("1.10.0", "1.9.9"))
        assertFalse(com.kairos.app.system.Updater.isNewer("1.2.0", "1.2.0"))
        assertFalse(com.kairos.app.system.Updater.isNewer("1.1.9", "1.2.0"))
    }
}
