package com.kairos.app.logic

/** When each alarm of a reminder's chain is due. */
object Chain {
    const val SNOOZE_STAGE = 3

    private const val MIN = 60_000L
    private const val CATCH_UP_MS = 1_500L

    /** Offsets of stages 0,1,2 relative to the chosen time T: T-10min, T, T+5min. */
    private val OFFSETS = longArrayOf(-10 * MIN, 0L, 5 * MIN)

    data class Alarm(val stage: Int, val at: Long)

    /**
     * A snooze replaces the rest of the chain, so it is the only alarm while it is set. Otherwise the
     * future stages are due at their own time and the latest passed one almost at once (catch-up).
     */
    fun plan(dueAt: Long, snoozeUntil: Long?, now: Long): List<Alarm> {
        val soon = now + CATCH_UP_MS
        if (snoozeUntil != null) {
            return listOf(Alarm(SNOOZE_STAGE, if (snoozeUntil > now) snoozeUntil else soon))
        }
        val (past, future) = OFFSETS.indices.map { Alarm(it, dueAt + OFFSETS[it]) }.partition { it.at <= now }
        return future + listOfNotNull(past.lastOrNull()?.copy(at = soon))
    }
}
