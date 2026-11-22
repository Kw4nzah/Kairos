package com.kairos.app.logic

/** Text limits. Notifications, popup intents and saved state all travel over Binder, which caps at about 1 MB. */
object Limits {
    const val TITLE = 200
    const val NOTES = 10_000

    /** What a notification, the popup or the calendar gets to show. */
    const val NOTES_SHOWN = 2_000

    fun clip(text: String, max: Int): String {
        if (text.length <= max) return text
        // Never cut an emoji in half.
        return text.substring(0, if (Character.isHighSurrogate(text[max - 1])) max - 1 else max)
    }
}
