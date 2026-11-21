package com.kairos.app.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

enum class RepeatType {
    NONE, DAILY, WEEKLY, MONTHLY;

    companion object {
        fun parse(value: String): RepeatType = entries.firstOrNull { it.name == value } ?: NONE
    }
}

/** Pure repeat-rule math. Weekday masks use bit0 = Monday ... bit6 = Sunday. */
object Recurrence {
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun maskOf(days: Collection<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or bit(d) }

    fun has(mask: Int, day: DayOfWeek): Boolean = (mask and bit(day)) != 0

    /** Effective mask: an empty weekly selection falls back to the weekday of [fallback]. */
    private fun effectiveMask(mask: Int, fallback: DayOfWeek): Int =
        if ((mask and 0x7F) == 0) bit(fallback) else mask

    /** The occurrence strictly following [current], or null for non-repeating reminders. */
    fun next(current: LocalDateTime, type: RepeatType, mask: Int, anchorDay: Int): LocalDateTime? =
        when (type) {
            RepeatType.NONE -> null
            RepeatType.DAILY -> current.plusDays(1)
            RepeatType.WEEKLY -> {
                val m = effectiveMask(mask, current.dayOfWeek)
                (1L..7L).map { current.plusDays(it) }.first { has(m, it.dayOfWeek) }
            }
            RepeatType.MONTHLY -> {
                val first = current.withDayOfMonth(1).plusMonths(1)
                first.withDayOfMonth(minOf(anchorDay, first.toLocalDate().lengthOfMonth()))
            }
        }

    /** First occurrence after [current] that is also strictly after [now] (skips missed ones). */
    fun nextAfter(
        current: LocalDateTime,
        now: LocalDateTime,
        type: RepeatType,
        mask: Int,
        anchorDay: Int
    ): LocalDateTime? {
        var n = next(current, type, mask, anchorDay) ?: return null
        var guard = 0
        while (!n.isAfter(now) && guard < 5000) {
            n = next(n, type, mask, anchorDay) ?: return null
            guard++
        }
        return n
    }

    /** Whether a reminder whose current occurrence is on [due] shows up on [date] (calendar dots). */
    fun occursOn(
        due: LocalDate,
        type: RepeatType,
        mask: Int,
        anchorDay: Int,
        date: LocalDate
    ): Boolean {
        if (date.isBefore(due)) return false
        return when (type) {
            RepeatType.NONE -> date == due
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> has(effectiveMask(mask, due.dayOfWeek), date.dayOfWeek)
            RepeatType.MONTHLY -> date.dayOfMonth == minOf(anchorDay, date.lengthOfMonth())
        }
    }
}
