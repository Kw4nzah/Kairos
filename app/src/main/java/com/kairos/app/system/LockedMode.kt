package com.kairos.app.system

import android.content.Context
import androidx.core.os.UserManagerCompat
import com.kairos.app.data.LockedStore
import com.kairos.app.data.ReminderState
import com.kairos.app.logic.SnoozeMachine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Alarms after a reboot, before the first unlock (Direct Boot). Room and DataStore are locked then, so this
 * works from the copy in [LockedStore] alone, and [com.kairos.app.data.Repo] copies the answers back once
 * the phone is unlocked. Everything else (editing, widget, calendar) waits for the unlock.
 */
object LockedMode {
    private val lock = Mutex()

    fun isLocked(ctx: Context): Boolean = !UserManagerCompat.isUserUnlocked(ctx)

    /** LOCKED_BOOT_COMPLETED: alarms do not survive a reboot, so set them again from the copy. */
    suspend fun boot(ctx: Context): Unit = lock.withLock {
        val state = LockedStore.read(ctx) ?: return@withLock
        state.reminders.filter { !it.done }.forEach { Scheduler.schedule(ctx, it) }
    }

    suspend fun fire(ctx: Context, id: Int, rawStage: Int): Unit = lock.withLock {
        val state = LockedStore.read(ctx) ?: return@withLock
        val r = state.reminders.firstOrNull { it.id == id } ?: return@withLock
        Notifier.ring(ctx, r, rawStage, state.settings)
    }

    suspend fun repost(ctx: Context, id: Int, stage: Int): Unit = lock.withLock {
        val state = LockedStore.read(ctx) ?: return@withLock
        val r = state.reminders.firstOrNull { it.id == id } ?: return@withLock
        Notifier.repost(ctx, r, stage, state.settings)
    }

    suspend fun handleAction(
        ctx: Context,
        id: Int,
        action: SnoozeMachine.Action,
        shownDue: Long?,
        shownCount: Int?
    ): Unit = lock.withLock {
        LockedStore.update(ctx) { state ->
            val r = state?.reminders?.firstOrNull { it.id == id } ?: return@update state
            val answered = ReminderState.answer(
                r, action, shownDue, shownCount, System.currentTimeMillis(), ZoneId.systemDefault()
            ) ?: return@update state
            Notifier.cancelAll(ctx, id)
            Scheduler.schedule(ctx, answered) // snooze: only the re-fire; done: next occurrence or nothing
            state.copy(
                reminders = state.reminders.map { if (it.id == id) answered else it },
                dirty = state.dirty + id
            )
        }
    }
}
