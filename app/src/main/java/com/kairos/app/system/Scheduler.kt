package com.kairos.app.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kairos.app.data.Reminder

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
    const val SNOOZE_STAGE = 3
    private const val MIN = 60_000L

    /** Offsets of stages 0,1,2 relative to the chosen time T: T-10min, T, T+5min. */
    private val OFFSETS = longArrayOf(-10 * MIN, 0L, 5 * MIN)

    fun stageTime(r: Reminder, stage: Int): Long = r.dueAt + OFFSETS[stage]

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
            if (canExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context, id: Int) {
        val am = alarmManager(ctx)
        for (stage in 0..SNOOZE_STAGE) am.cancel(pending(ctx, id, stage))
    }

    /**
     * Schedules the chain for the reminder's current occurrence. Future stages get their own alarm;
     * the latest already-passed stage fires almost immediately (reboot / late edit catch-up).
     */
    fun schedule(ctx: Context, r: Reminder) {
        cancel(ctx, r.id)
        if (r.done) return
        val now = System.currentTimeMillis()
        val (past, future) = (0..2).partition { stageTime(r, it) <= now }
        future.forEach { setAt(ctx, r.id, it, stageTime(r, it)) }
        past.lastOrNull()?.let { setAt(ctx, r.id, it, now + 1_500L) }
    }

    /** Re-fires the chain's final notification at [at] (used by snooze). */
    fun scheduleSnooze(ctx: Context, id: Int, at: Long) = setAt(ctx, id, SNOOZE_STAGE, at)
}
