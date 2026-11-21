package com.kairos.app.logic

/** Snooze state machine for the final ("did you do it?") notification. */
object SnoozeMachine {
    const val MAX_SNOOZES = 3

    enum class Action { SNOOZE_5, SNOOZE_10, DONE }

    /** [delayMinutes] is non-null when the final notification must be re-fired later. */
    data class Outcome(val newCount: Int, val delayMinutes: Int?, val done: Boolean)

    fun actions(count: Int): List<Action> =
        if (count < MAX_SNOOZES) listOf(Action.SNOOZE_5, Action.SNOOZE_10)
        else listOf(Action.SNOOZE_10, Action.DONE)

    fun apply(count: Int, action: Action): Outcome {
        if (action !in actions(count)) return Outcome(count, null, false) // stale button: ignore
        return when (action) {
            Action.SNOOZE_5 -> Outcome(count + 1, 5, false)
            Action.SNOOZE_10 -> Outcome(count + 1, 10, false)
            Action.DONE -> Outcome(count, null, true)
        }
    }
}
