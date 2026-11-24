package com.kairos.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairos.app.data.Repo
import com.kairos.app.logic.SnoozeMachine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTION_FIRE = "com.kairos.app.FIRE"
const val ACTION_USER = "com.kairos.app.USER_ACTION"
const val ACTION_REPOST = "com.kairos.app.REPOST"
const val EXTRA_ID = "id"
const val EXTRA_STAGE = "stage"
const val EXTRA_ACTION = "action"

/** Which occurrence and snooze round a button was drawn for, so a stale or repeated press does nothing. */
const val EXTRA_DUE = "due"
const val EXTRA_COUNT = "count"

/**
 * Runs [block] off the main thread. Before the first unlock after a reboot it runs [locked] instead, since
 * Room cannot be opened then (see [LockedMode]); null means nothing to do until the unlock.
 */
private fun BroadcastReceiver.async(
    context: Context,
    locked: (suspend (Context) -> Unit)? = null,
    block: suspend (Repo) -> Unit
) {
    val app = context.applicationContext
    val isLocked = LockedMode.isLocked(app)
    if (isLocked && locked == null) return
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            if (isLocked) locked?.invoke(app) else block(Repo(app))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An uncaught error here kills the process, and every other alarm being handled with it.
            Log.e("Kairos", "broadcast failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** Fires a chain stage (0 heads-up, 1 main, 2 check, 3 = snoozed re-fire of the check). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val stage = intent.getIntExtra(EXTRA_STAGE, -1)
        if (id <= 0 || stage < 0) return
        async(context, { LockedMode.fire(it, id, stage) }) { it.fire(id, stage) }
    }
}

/** Notification buttons (snooze / done) and the swipe-away deleteIntent. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id <= 0) return
        when (intent.action) {
            ACTION_REPOST -> {
                val stage = intent.getIntExtra(EXTRA_STAGE, -1)
                if (stage in 0..2) async(context, { LockedMode.repost(it, id, stage) }) { it.repost(id, stage) }
            }
            ACTION_USER -> {
                val action = runCatching {
                    SnoozeMachine.Action.valueOf(intent.getStringExtra(EXTRA_ACTION) ?: "")
                }.getOrNull() ?: return
                val due = if (intent.hasExtra(EXTRA_DUE)) intent.getLongExtra(EXTRA_DUE, 0L) else null
                val count = if (intent.hasExtra(EXTRA_COUNT)) intent.getIntExtra(EXTRA_COUNT, 0) else null
                async(context, { LockedMode.handleAction(it, id, action, due, count) }) {
                    it.handleAction(id, action, due, count)
                }
            }
        }
    }
}

/**
 * The broadcasts BootReceiver acts on. All are protected, so only the system can send them. The receiver
 * has to be exported, and without this check any app could make overdue reminders ring again.
 */
private val RESCHEDULE_ON = setOf(
    Intent.ACTION_LOCKED_BOOT_COMPLETED,
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
)

/**
 * Restores alarms after reboot (also before the first unlock), app update or exact-alarm grant, and
 * follows clock changes.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            !in RESCHEDULE_ON -> return
            // Before the first unlock: ring from the locked copy. Without a PIN BOOT_COMPLETED follows at once.
            Intent.ACTION_LOCKED_BOOT_COMPLETED ->
                async(context, { LockedMode.boot(it) }) { /* unlocked already: BOOT_COMPLETED does it */ }
            // Alarms are wall-clock alarms and survive these. Rescheduling would ring every overdue one again.
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> async(context) { it.onClockChanged() }
            else -> async(context) { it.rescheduleAll() }
        }
    }
}
