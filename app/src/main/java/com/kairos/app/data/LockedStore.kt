package com.kairos.app.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A copy of what ringing needs, kept in device-protected storage so alarms work after a reboot before the
 * first unlock, when Room and DataStore (credential-protected) cannot be opened. It holds titles and times,
 * never notes. While the phone is unlocked [Repo] rewrites it after every change; before the first unlock
 * the locked path edits it and marks what it touched in [State.dirty], and [Repo] copies those back.
 */
object LockedStore {
    data class State(
        val settings: Settings,
        val reminders: List<Reminder>,
        /** Reminders answered or rung before the first unlock whose state Room does not have yet. */
        val dirty: Set<Int> = emptySet()
    )

    private const val FILE = "locked_state.json"
    private val lock = Any()

    private fun file(ctx: Context) =
        AtomicFile(File(ctx.createDeviceProtectedStorageContext().filesDir, FILE))

    fun read(ctx: Context): State? = synchronized(lock) { readLocked(ctx) }

    /** Read-modify-write; [change] returns the new state, or the same object to write nothing. */
    fun update(ctx: Context, change: (State?) -> State?) = synchronized(lock) {
        val old = readLocked(ctx)
        val new = change(old)
        if (new != null && new !== old) writeLocked(ctx, new)
    }

    private fun readLocked(ctx: Context): State? = try {
        val f = file(ctx)
        if (!f.baseFile.exists()) null else decode(JSONObject(String(f.readFully(), Charsets.UTF_8)))
    } catch (e: Exception) {
        // A broken copy only costs the before-unlock alarms; the next change rewrites it.
        Log.w("Kairos", "locked state unreadable", e)
        null
    }

    private fun writeLocked(ctx: Context, state: State) {
        val f = file(ctx)
        val out = try {
            f.startWrite()
        } catch (e: Exception) {
            Log.w("Kairos", "locked state not written", e)
            return
        }
        try {
            out.write(encode(state).toString().toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            Log.w("Kairos", "locked state not written", e)
        }
    }

    // Enum values are stored by their names, as in Room and DataStore.
    private fun encode(s: State) = JSONObject().apply {
        put("v", 1)
        put("settings", JSONObject().apply {
            put("name", s.settings.name)
            put("sound", s.settings.defaultSound)
            put("theme", s.settings.themeMode.name)
            put("language", s.settings.language.name)
        })
        put("reminders", JSONArray().apply {
            s.reminders.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("dueAt", r.dueAt)
                    r.dueLocal?.let { put("dueLocal", it) }
                    put("repeatType", r.repeatType)
                    put("weekdays", r.weekdays)
                    put("anchorDay", r.anchorDay)
                    put("sound", r.sound)
                    put("done", r.done)
                    r.doneAt?.let { put("doneAt", it) }
                    put("snoozeCount", r.snoozeCount)
                    r.snoozeUntil?.let { put("snoozeUntil", it) }
                })
            }
        })
        put("dirty", JSONArray().apply { s.dirty.forEach { put(it) } })
    }

    private fun decode(o: JSONObject): State {
        val st = o.getJSONObject("settings")
        val settings = Settings(
            name = st.optString("name"),
            defaultSound = st.optString("sound", Settings().defaultSound),
            themeMode = ThemeMode.parse(st.optString("theme")),
            language = AppLanguage.parse(st.optString("language"))
        )
        val list = o.getJSONArray("reminders")
        val reminders = (0 until list.length()).map { i ->
            val r = list.getJSONObject(i)
            Reminder(
                id = r.getInt("id"),
                title = r.getString("title"),
                dueAt = r.getLong("dueAt"),
                dueLocal = if (r.has("dueLocal")) r.getString("dueLocal") else null,
                repeatType = r.getString("repeatType"),
                weekdays = r.getInt("weekdays"),
                anchorDay = r.getInt("anchorDay"),
                sound = r.getString("sound"),
                done = r.getBoolean("done"),
                doneAt = if (r.has("doneAt")) r.getLong("doneAt") else null,
                snoozeCount = r.getInt("snoozeCount"),
                snoozeUntil = if (r.has("snoozeUntil")) r.getLong("snoozeUntil") else null
            )
        }
        val d = o.optJSONArray("dirty") ?: JSONArray()
        return State(settings, reminders, (0 until d.length()).map { d.getInt(it) }.toSet())
    }
}
