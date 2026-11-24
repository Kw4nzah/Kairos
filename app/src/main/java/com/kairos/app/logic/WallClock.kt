package com.kairos.app.logic

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * A reminder's time as the user picked it: a wall-clock date and time ("08:00 on the 25th"), not an instant.
 * The instant (dueAt) is derived from it in the phone's current zone, so a reminder keeps its clock time
 * when the user travels, and a time that falls into a DST gap keeps its intended time on later days.
 */
object WallClock {
    fun of(dueAt: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDateTime().toString()

    /** The stored wall time, or the one [dueAt] shows in [zone] when none is stored (or it is unreadable). */
    fun local(dueLocal: String?, dueAt: Long, zone: ZoneId): LocalDateTime =
        dueLocal?.let {
            try {
                LocalDateTime.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDateTime()

    fun toMillis(local: LocalDateTime, zone: ZoneId): Long = local.atZone(zone).toInstant().toEpochMilli()

    /**
     * The new dueAt after the zone changed, or null to leave the reminder alone. Only a reminder whose
     * chain has not started moves: one that is ringing, overdue or snoozed already happened for the user.
     * It also stays put when its clock time has already passed in the new zone, so it rings once at the
     * old moment instead of at once; the next occurrence then follows the new clock.
     */
    fun follow(dueLocal: String?, dueAt: Long, snoozeUntil: Long?, zone: ZoneId, now: Long): Long? {
        if (dueLocal == null || !Chain.isWaiting(dueAt, snoozeUntil, now)) return null
        val moved = toMillis(local(dueLocal, dueAt, zone), zone)
        return if (moved != dueAt && moved > now) moved else null
    }
}
