package com.kairos.app.logic

import android.content.Context
import com.kairos.app.R
import java.util.Locale

/**
 * All user-facing copy: one friendly tone, resolved through string resources of [ctx], so it follows
 * whichever locale that context carries. Templates use %1$s = name and %2$s = task title.
 */
class ToneSet(private val ctx: Context) {
    private val locale: Locale get() = ctx.resources.configuration.locales[0]

    private fun variant(arrayRes: Int, seed: Int, name: String, title: String): String {
        val list = ctx.resources.getStringArray(arrayRes)
        return String.format(locale, list[Tone.pickIndex(list.size, seed)], name, title)
    }

    private fun s(res: Int) = ctx.getString(res)

    fun greeting(name: String, seed: Int = 0) = variant(R.array.tone_greetings, seed, name, "")
    fun headsUpTitle(name: String) = s(R.string.tone_headsup_title)
    fun headsUpBody(title: String, name: String, seed: Int) = variant(R.array.tone_headsup_bodies, seed, name, title)
    fun mainTitle(name: String) = s(R.string.tone_main_title)
    fun mainBody(title: String, name: String, seed: Int) = variant(R.array.tone_main_bodies, seed, name, title)
    fun checkTitle(name: String) = s(R.string.tone_check_title)
    fun checkBody(title: String, name: String, seed: Int) = variant(R.array.tone_check_bodies, seed, name, title)
    fun nagTitle(name: String) = s(R.string.tone_nag_title)
    fun nagBody(title: String, name: String, seed: Int) = variant(R.array.tone_nag_bodies, seed, name, title)

    val emptyToday get() = s(R.string.tone_empty_today)
    val emptyUpcoming get() = s(R.string.tone_empty_upcoming)
    val emptyDone get() = s(R.string.tone_empty_done)
    val emptyDay get() = s(R.string.tone_empty_day)
    val snackAdded get() = s(R.string.tone_snack_added)
    val snackUpdated get() = s(R.string.tone_snack_updated)
    val snackDeleted get() = s(R.string.tone_snack_deleted)
    val snackDone get() = s(R.string.tone_snack_done)
    val snackReopened get() = s(R.string.tone_snack_reopened)
    val snackHardcoreOn get() = s(R.string.tone_snack_hardcore_on)
    val snackHardcoreOff get() = s(R.string.tone_snack_hardcore_off)
    val snackCalendarDenied get() = s(R.string.tone_snack_calendar_denied)
    val snackProfileSaved get() = s(R.string.tone_snack_profile_saved)
    val permissionBanner get() = s(R.string.tone_permission_banner)
}

object Tone {
    /** Deterministic variant choice that works for any (also negative) seed. */
    fun pickIndex(size: Int, seed: Int): Int = Math.floorMod(seed, size)
}
