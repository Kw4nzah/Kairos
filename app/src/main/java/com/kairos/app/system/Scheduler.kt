package com.kairos.app.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kairos.app.data.Reminder
import com.kairos.app.logic.Chain

/** Request codes / notification ids derived from the reminder id. */
object Codes {
    private fun base(id: Int) = id * 16
    fun alarm(id: Int, stage: Int) = base(id) + stage          // 0..3
    fun action(id: Int, ordinal: Int) = base(id) + 4 + ordinal  // 4..6
    fun repost(id: Int, stage: Int) = base(id) + 8 + stage      // 8..10
    fun content(id: Int) = base(id) + 12
    fun notif(id: Int, stage: Int) = id * 4 + stage
}

object Scheduler {
    private fun alarmManager(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(ctx).canScheduleExactAlarms()

    private fun pending(ctx: Context, id: Int, stage: Int): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_STAGE, stage)
        return PendingIntent.getBroadcast(
            ctx, Codes.alarm(id, stage), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun setAt(ctx: Context, id: Int, stage: Int, at: Long) {
        val am = alarmManager(ctx)
        val pi = pending(ctx, id, stage)
        try {
            // An "alarm clock" alarm is exempt from Doze and most OEM battery killers, so it fires on time
            // with the screen off and the phone locked. The show-intent just opens the app.
            val show = PendingIntent.getActivity(
                ctx, Codes.content(id), Intent(ctx, com.kairos.app.MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context, id: Int) {
        val am = alarmManager(ctx)
        for (stage in 0..Chain.SNOOZE_STAGE) am.cancel(pending(ctx, id, stage))
    }

    /** Replaces the reminder's alarms with the ones its current state calls for (see [Chain.plan]). */
    fun schedule(ctx: Context, r: Reminder) {
        cancel(ctx, r.id)
        if (r.done) return
        Chain.plan(r.dueAt, r.snoozeUntil, System.currentTimeMillis()).forEach { setAt(ctx, r.id, it.stage, it.at) }
    }
}
