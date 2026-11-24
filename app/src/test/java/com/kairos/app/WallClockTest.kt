package com.kairos.app

import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import com.kairos.app.logic.WallClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WallClockTest {
    private val istanbul = ZoneId.of("Europe/Istanbul")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val hour = 3_600_000L

    private fun at(local: String, zone: ZoneId) = WallClock.toMillis(LocalDateTime.parse(local), zone)

    @Test fun roundTrip() {
        val due = at("2026-11-25T08:00", istanbul)
        assertEquals("2026-11-25T08:00", WallClock.of(due, istanbul))
        assertEquals(LocalDateTime.parse("2026-11-25T08:00"), WallClock.local("2026-11-25T08:00", due, berlin))
    }

    @Test fun missingOrBrokenWallTimeFallsBackToDueAt() {
        val due = at("2026-11-25T08:00", istanbul)
        assertEquals(LocalDateTime.parse("2026-11-25T08:00"), WallClock.local(null, due, istanbul))
        assertEquals(LocalDateTime.parse("2026-11-25T06:00"), WallClock.local("garbage", due, berlin))
    }

    @Test fun waitingReminderKeepsItsClockTimeAfterTravel() {
        // Picked 08:00 in Istanbul; the phone is now in Berlin (2 h behind) the evening before.
        val due = at("2026-11-25T08:00", istanbul)
        val now = at("2026-11-24T20:00", berlin)
        assertEquals(at("2026-11-25T08:00", berlin), WallClock.follow("2026-11-25T08:00", due, null, berlin, now))
        assertEquals(due + 2 * hour, WallClock.follow("2026-11-25T08:00", due, null, berlin, now))
    }

    @Test fun sameZoneDoesNotMove() {
        val due = at("2026-11-25T08:00", istanbul)
        assertNull(WallClock.follow("2026-11-25T08:00", due, null, istanbul, due - 5 * hour))
    }

    @Test fun startedSnoozedOrUnstoredRemindersStay() {
        val due = at("2026-11-25T08:00", istanbul)
        assertNull(WallClock.follow("2026-11-25T08:00", due, null, berlin, due - 5 * 60_000L)) // heads-up out
        assertNull(WallClock.follow("2026-11-25T08:00", due, due + hour, berlin, due - 5 * hour)) // snoozed
        assertNull(WallClock.follow(null, due, null, berlin, due - 5 * hour))
    }

    @Test fun clockTimeAlreadyPassedInNewZoneStaysAtOldMoment() {
        // 23:30 Istanbul; at 02:00 Tokyo the 23:30 has passed there, but the old moment (05:30) has not.
        val due = at("2026-11-25T23:30", istanbul)
        val now = at("2026-11-26T02:00", tokyo)
        assertNull(WallClock.follow("2026-11-25T23:30", due, null, tokyo, now))
    }

    @Test fun nextOccurrenceUsesClockTimeInCurrentZone() {
        // The kept occurrence above is done in Tokyo: the next one is 23:30 Tokyo, not 05:30.
        val current = WallClock.local("2026-11-25T23:30", at("2026-11-25T23:30", istanbul), tokyo)
        val now = LocalDateTime.parse("2026-11-26T05:31")
        val next = Recurrence.nextAfter(current, now, RepeatType.DAILY, 0, 25)!!
        assertEquals(LocalDateTime.parse("2026-11-26T23:30"), next)
    }

    @Test fun dstGapTimeKeepsItsIntendedTimeNextDay() {
        // 2027-03-28 02:30 does not exist in Berlin; that day it rings at 03:30, the next day at 02:30 again.
        val gap = LocalDateTime.parse("2027-03-28T02:30")
        assertEquals(at("2027-03-28T03:30", berlin), WallClock.toMillis(gap, berlin))
        val next = Recurrence.next(WallClock.local(gap.toString(), 0L, berlin), RepeatType.DAILY, 0, 28)!!
        assertEquals(at("2027-03-29T02:30", berlin), WallClock.toMillis(next, berlin))
        assertEquals("2027-03-29T02:30", WallClock.of(WallClock.toMillis(next, berlin), berlin))
    }
}
