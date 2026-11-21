package com.kairos.app.ui

import android.content.Context
import android.text.format.DateFormat
import com.kairos.app.R
import com.kairos.app.data.Reminder
import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Display helpers. Everything is formatted for the locale of the given context. */
object Fmt {
    fun locale(ctx: Context): Locale = ctx.resources.configuration.locales[0]

    fun dayShort(ctx: Context, d: DayOfWeek): String {
        val l = locale(ctx)
        return d.getDisplayName(TextStyle.SHORT, l).replaceFirstChar { it.titlecase(l) }
    }

    /** First day of the week for the active locale (calendar grid column order). */
    fun firstDayOfWeek(ctx: Context): DayOfWeek = WeekFields.of(locale(ctx)).firstDayOfWeek

    fun local(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
    fun toMillis(dt: LocalDateTime): Long = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun date(ctx: Context, d: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale(ctx)).format(d)

    fun month(ctx: Context, d: LocalDate): String {
        val l = locale(ctx)
        return DateTimeFormatter.ofPattern("LLLL yyyy", l).format(d).replaceFirstChar { it.titlecase(l) }
    }

    fun time(ctx: Context, dt: LocalDateTime): String {
        val l = locale(ctx)
        val f = if (DateFormat.is24HourFormat(ctx)) DateTimeFormatter.ofPattern("HH:mm", l)
        else DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(l)
        return f.format(dt)
    }

    fun repeatLabel(ctx: Context, type: RepeatType, mask: Int): String = when (type) {
        RepeatType.NONE -> ctx.getString(R.string.repeat_label_none)
        RepeatType.DAILY -> ctx.getString(R.string.repeat_label_daily)
        RepeatType.MONTHLY -> ctx.getString(R.string.repeat_label_monthly)
        RepeatType.WEEKLY -> {
            val days = DayOfWeek.entries.filter { Recurrence.has(mask, it) }
            if (days.isEmpty()) ctx.getString(R.string.repeat_label_weekly)
            else days.joinToString(", ") { dayShort(ctx, it) }
        }
    }

    /** One-line subtitle for a list row. */
    fun subtitle(ctx: Context, r: Reminder): String {
        val dt = local(r.dueAt)
        val today = LocalDate.now()
        val day = when (dt.toLocalDate()) {
            today -> ctx.getString(R.string.day_today)
            today.plusDays(1) -> ctx.getString(R.string.day_tomorrow)
            else -> DateTimeFormatter.ofPattern("d MMM", locale(ctx)).format(dt)
        }
        val type = RepeatType.parse(r.repeatType)
        val base = "$day ${time(ctx, dt)}"
        return if (type == RepeatType.NONE) base else "$base · ${repeatLabel(ctx, type, r.weekdays)}"
    }
}
