package com.kairos.app.system

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import com.kairos.app.data.Reminder
import com.kairos.app.logic.Recurrence
import com.kairos.app.logic.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/** Mirrors reminders into a local "Kairos" calendar (Hardcore mode). */
object CalendarSync {
    private const val ACCOUNT_NAME = "Kairos"
    private const val EVENT_LENGTH_MS = 15 * 60_000L

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(ctx: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Writes to a LOCAL account calendar must go through the sync-adapter flavour of the URI. */
    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun calendarId(ctx: Context, create: Boolean): Long? {
        val cr = ctx.contentResolver
        cr.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        if (!create) return null

        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "kairos")
            put(Calendars.CALENDAR_DISPLAY_NAME, "Kairos")
            put(Calendars.CALENDAR_COLOR, 0xFF0B6B66.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val uri = cr.insert(asSyncAdapter(Calendars.CONTENT_URI), values) ?: return null
        return ContentUris.parseId(uri)
    }

    private fun rrule(r: Reminder): String? = when (RepeatType.parse(r.repeatType)) {
        RepeatType.NONE -> null
        RepeatType.DAILY -> "FREQ=DAILY"
        RepeatType.MONTHLY -> "FREQ=MONTHLY"
        RepeatType.WEEKLY -> {
            val due = Instant.ofEpochMilli(r.dueAt).atZone(ZoneId.systemDefault()).dayOfWeek
            val mask = if ((r.weekdays and 0x7F) == 0) Recurrence.bit(due) else r.weekdays
            val codes = mapOf(
                DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
                DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
                DayOfWeek.SUNDAY to "SU"
            )
            "FREQ=WEEKLY;BYDAY=" + DayOfWeek.entries.filter { Recurrence.has(mask, it) }
                .joinToString(",") { codes.getValue(it) }
        }
    }

    private fun writeAlarms(ctx: Context, eventId: Long) {
        val cr = ctx.contentResolver
        cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        for (minutes in intArrayOf(10, 0)) {
            val v = ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
                put(Reminders.MINUTES, minutes)
            }
            cr.insert(Reminders.CONTENT_URI, v)
        }
    }

    /** Creates or updates the event for [r]; returns its id, or null if it could not be written. */
    fun upsert(ctx: Context, r: Reminder): Long? {
        if (!hasPermission(ctx)) return null
        return try {
            val calId = calendarId(ctx, create = true) ?: return null
            val cr = ctx.contentResolver
            val rule = rrule(r)
            val v = ContentValues().apply {
                put(Events.CALENDAR_ID, calId)
                put(Events.TITLE, r.title)
                put(Events.DESCRIPTION, r.notes)
                put(Events.DTSTART, r.dueAt)
                put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(Events.HAS_ALARM, 1)
                if (rule == null) {
                    put(Events.DTEND, r.dueAt + EVENT_LENGTH_MS)
                    putNull(Events.RRULE)
                    putNull(Events.DURATION)
                } else {
                    put(Events.RRULE, rule)
                    put(Events.DURATION, "PT15M")
                    putNull(Events.DTEND)
                }
            }
            val existing = r.calendarEventId
            if (existing != null) {
                val rows = cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, existing), v, null, null)
                if (rows > 0) {
                    writeAlarms(ctx, existing)
                    return existing
                }
            }
            val uri = cr.insert(Events.CONTENT_URI, v) ?: return null
            val id = ContentUris.parseId(uri)
            writeAlarms(ctx, id)
            id
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    fun deleteEvent(ctx: Context, eventId: Long) {
        if (!hasPermission(ctx)) return
        try {
            ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
        } catch (_: RuntimeException) {
        }
    }

    /** Removes the whole Kairos calendar together with all its events. */
    fun removeCalendar(ctx: Context) {
        if (!hasPermission(ctx)) return
        try {
            val id = calendarId(ctx, create = false) ?: return
            ctx.contentResolver.delete(
                ContentUris.withAppendedId(asSyncAdapter(Calendars.CONTENT_URI), id), null, null
            )
        } catch (_: RuntimeException) {
        }
    }
}
