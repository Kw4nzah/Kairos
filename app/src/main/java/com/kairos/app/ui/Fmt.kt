package com.kairos.app.ui

import com.kairos.app.data.Reminder
import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Turkish display helpers. */
object Fmt {
    val TR: Locale = Locale.forLanguageTag("tr-TR")
    private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", TR)
    private val shortDateFmt = DateTimeFormatter.ofPattern("d MMM", TR)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", TR)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", TR)

    val dayShort = mapOf(
        DayOfWeek.MONDAY to "Pzt", DayOfWeek.TUESDAY to "Sal", DayOfWeek.WEDNESDAY to "Çar",
        DayOfWeek.THURSDAY to "Per", DayOfWeek.FRIDAY to "Cum", DayOfWeek.SATURDAY to "Cmt",
        DayOfWeek.SUNDAY to "Paz"
    )

    fun local(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
    fun toMillis(dt: LocalDateTime): Long = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun date(d: LocalDate): String = dateFmt.format(d)
    fun month(d: LocalDate): String = monthFmt.format(d).replaceFirstChar { it.titlecase(TR) }
    fun time(dt: LocalDateTime): String = timeFmt.format(dt)

    fun repeatLabel(type: RepeatType, mask: Int): String = when (type) {
        RepeatType.NONE -> "Tekrar yok"
        RepeatType.DAILY -> "Her gün"
        RepeatType.MONTHLY -> "Her ay"
        RepeatType.WEEKLY -> {
            val days = DayOfWeek.entries.filter { Recurrence.has(mask, it) }
            if (days.isEmpty()) "Haftalık" else days.joinToString(", ") { dayShort.getValue(it) }
        }
    }

    /** One-line subtitle for a list row. */
    fun subtitle(r: Reminder): String {
        val dt = local(r.dueAt)
        val today = LocalDate.now()
        val day = when (dt.toLocalDate()) {
            today -> "Bugün"
            today.plusDays(1) -> "Yarın"
            else -> shortDateFmt.format(dt)
        }
        val type = RepeatType.parse(r.repeatType)
        val base = "$day ${time(dt)}"
        return if (type == RepeatType.NONE) base else "$base · ${repeatLabel(type, r.weekdays)}"
    }
}
