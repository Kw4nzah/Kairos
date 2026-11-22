package com.kairos.app.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kairos.app.AlarmActivity
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.data.Reminder
import com.kairos.app.data.Settings
import com.kairos.app.logic.SnoozeMachine
import com.kairos.app.logic.ToneSet

/**
 * Posts the notification chain. Every notification is deliberately sticky: ongoing, not auto-cancelled,
 * no dismiss action, and a deleteIntent that immediately re-posts it if the user swipes it away.
 */
object Notifier {
    private val VIBRATION = longArrayOf(0, 600, 250, 600, 250, 900)

    /** One max-importance alarm-category channel per sound (channel sound is immutable). */
    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        for (s in Sounds.all) {
            val ch = NotificationChannel(
                Sounds.channelId(s.key), ctx.getString(R.string.channel_name, s.name(ctx)), NotificationManager.IMPORTANCE_HIGH
            )
            ch.description = ctx.getString(R.string.channel_desc, s.name(ctx))
            ch.setSound(Sounds.uri(ctx, s), attrs)
            ch.enableVibration(true)
            ch.vibrationPattern = VIBRATION
            ch.enableLights(true)
            ch.lightColor = Color.parseColor("#0B6B66")
            ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            ch.setBypassDnd(true)
            nm.createNotificationChannel(ch)
        }
    }

    private fun broadcast(ctx: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openApp(ctx: Context, id: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            ctx, Codes.content(id), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun popupIntent(ctx: Context, r: Reminder, title: String, body: String, stage: Int): Intent {
        val actions = if (stage >= 1) SnoozeMachine.actions(r.snoozeCount) else emptyList()
        return Intent(ctx, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_ID, r.id)
            .putExtra(AlarmActivity.X_TITLE, title)
            .putExtra(AlarmActivity.X_BODY, body)
            .putExtra(AlarmActivity.X_NOTES, r.notes)
            .putExtra(AlarmActivity.X_ACTIONS, actions.map { it.name }.toTypedArray())
            .putExtra(AlarmActivity.X_LABELS, actions.map { actionLabel(ctx, it, r.snoozeCount) }.toTypedArray())
    }

    private fun repostIntent(ctx: Context, id: Int, stage: Int): PendingIntent =
        broadcast(
            ctx, Codes.repost(id, stage),
            Intent(ctx, ActionReceiver::class.java).setAction(ACTION_REPOST)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_STAGE, stage)
        )

    private fun actionIntent(ctx: Context, id: Int, action: SnoozeMachine.Action): PendingIntent =
        broadcast(
            ctx, Codes.action(id, action.ordinal),
            Intent(ctx, ActionReceiver::class.java).setAction(ACTION_USER)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_ACTION, action.name)
        )

    private fun actionLabel(ctx: Context, action: SnoozeMachine.Action, snoozeCount: Int): String = ctx.getString(
        when (action) {
            SnoozeMachine.Action.SNOOZE_5 -> R.string.action_snooze_5
            SnoozeMachine.Action.SNOOZE_10 ->
                if (snoozeCount >= SnoozeMachine.MAX_SNOOZES) R.string.action_snooze_10_more
                else R.string.action_snooze_10
            SnoozeMachine.Action.DONE -> R.string.action_done
        }
    )

    @SuppressLint("MissingPermission")
    fun post(baseCtx: Context, r: Reminder, stage: Int, settings: Settings, silent: Boolean = false) {
        // Receivers run outside the Activity, so build a context for the language chosen in settings.
        val ctx = Locales.localized(baseCtx, settings.language)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(ctx)

        val tone = ToneSet(ctx)
        val name = settings.name
        val seed = r.id + stage
        val nagging = stage == 2 && r.snoozeCount >= SnoozeMachine.MAX_SNOOZES
        val title: String
        val body: String
        when {
            stage == 0 -> {
                title = tone.headsUpTitle(name); body = tone.headsUpBody(r.title, name, seed)
            }
            stage == 1 -> {
                title = tone.mainTitle(name); body = tone.mainBody(r.title, name, seed)
            }
            nagging -> {
                title = tone.nagTitle(name); body = tone.nagBody(r.title, name, seed)
            }
            else -> {
                title = tone.checkTitle(name); body = tone.checkBody(r.title, name, seed)
            }
        }
        val big = if (r.notes.isBlank()) body else "$body\n${r.notes}"

        val b = NotificationCompat.Builder(ctx, Sounds.channelId(r.sound))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Color.parseColor("#0B6B66"))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(VIBRATION)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(silent)
            .setContentIntent(openApp(ctx, r.id))
            .setDeleteIntent(repostIntent(ctx, r.id, stage))
        if (silent) b.setSilent(true)
        val popup = if (stage >= 1) popupIntent(ctx, r, title, body, stage) else null
        if (popup != null) {
            b.setFullScreenIntent(
                PendingIntent.getActivity(ctx, Codes.content(r.id) + 7, popup, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                true
            )
        }
        if (stage >= 1) {
            // Only snooze / done actions are offered; there is intentionally no plain dismiss.
            for (a in SnoozeMachine.actions(r.snoozeCount)) {
                b.addAction(0, actionLabel(ctx, a, r.snoozeCount), actionIntent(ctx, r.id, a))
            }
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = b.build()
        // From the main reminder on, sound and vibration repeat until the notification is cancelled,
        // i.e. until the user presses a snooze / done button.
        if (stage >= 1 && !silent) notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        if (stage >= 1 && !silent) wakeScreen(ctx)
        nm.notify(Codes.notif(r.id, stage), notification)
        // Alarm-style popup on top of whatever is on screen. Background launches are blocked unless the
        // "display over other apps" permission is granted; the full-screen intent covers a locked screen.
        if (popup != null && !silent) runCatching { ctx.startActivity(popup) }
    }

    /** Turns the screen on for a few seconds so the alarm is seen even when the phone is idle. */
    @Suppress("DEPRECATION")
    private fun wakeScreen(ctx: Context) {
        runCatching {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.newWakeLock(
                android.os.PowerManager.FULL_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                "kairos:alarm"
            ).acquire(15_000L)
        }
    }

    fun cancel(ctx: Context, id: Int, stage: Int, closePopup: Boolean = true) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(Codes.notif(id, stage))
        if (stage >= 1 && closePopup) AlarmActivity.close(id)
    }

    fun cancelAll(ctx: Context, id: Int) {
        for (stage in 0..2) cancel(ctx, id, stage)
    }
}
