package com.kairos.app

import com.kairos.app.logic.Chain
import com.kairos.app.logic.Chain.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test

class ChainTest {
    private val min = 60_000L
    private val t = 1_000 * min
    private val soon = 1_500L

    @Test fun futureReminderGetsWholeChain() {
        assertEquals(
            listOf(Alarm(0, t - 10 * min), Alarm(1, t), Alarm(2, t + 5 * min)),
            Chain.plan(t, null, t - 30 * min)
        )
    }

    @Test fun onlyLatestPassedStageCatchesUp() {
        val now = t + min
        assertEquals(listOf(Alarm(2, t + 5 * min), Alarm(1, now + soon)), Chain.plan(t, null, now))
    }

    @Test fun overdueReminderFiresCheckAtOnce() {
        val now = t + 60 * min
        assertEquals(listOf(Alarm(2, now + soon)), Chain.plan(t, null, now))
    }

    @Test fun tenMinuteSnoozeAtTimeDropsTheFiveMinuteCheck() {
        assertEquals(listOf(Alarm(3, t + 10 * min)), Chain.plan(t, t + 10 * min, t))
    }

    @Test fun fiveMinuteSnoozeAtTimeRingsOnce() {
        assertEquals(listOf(Alarm(3, t + 5 * min)), Chain.plan(t, t + 5 * min, t))
    }

    @Test fun pendingSnoozeSurvivesReschedule() {
        val until = t + 25 * min
        assertEquals(listOf(Alarm(3, until)), Chain.plan(t, until, t + 18 * min))
    }

    @Test fun snoozeMissedWhilePoweredOffFiresAtOnce() {
        val now = t + 40 * min
        assertEquals(listOf(Alarm(3, now + soon)), Chain.plan(t, t + 10 * min, now))
    }
}
