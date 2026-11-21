package com.kairos.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kairos.app.data.Repo
import com.kairos.app.logic.SnoozeMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTION_FIRE = "com.kairos.app.FIRE"
const val ACTION_USER = "com.kairos.app.USER_ACTION"
const val ACTION_REPOST = "com.kairos.app.REPOST"
const val EXTRA_ID = "id"
const val EXTRA_STAGE = "stage"
const val EXTRA_ACTION = "action"

private fun BroadcastReceiver.async(context: Context, block: suspend (Repo) -> Unit) {
    val pending = goAsync()
    val app = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block(Repo(app))
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
        async(context) { it.fire(id, stage) }
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
                if (stage in 0..2) async(context) { it.repost(id, stage) }
            }
            ACTION_USER -> {
                val action = runCatching {
                    SnoozeMachine.Action.valueOf(intent.getStringExtra(EXTRA_ACTION) ?: "")
                }.getOrNull() ?: return
                async(context) { it.handleAction(id, action) }
            }
        }
    }
}

/** Reschedules everything after reboot, app update, clock/timezone change or exact-alarm grant. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        async(context) { it.rescheduleAll() }
    }
}
