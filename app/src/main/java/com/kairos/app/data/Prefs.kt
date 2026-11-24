package com.kairos.app.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kairos.app.system.Sounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// An unreadable file (power cut mid-write) would otherwise crash every start and every alarm.
private val Context.kairosStore by preferencesDataStore(
    name = "kairos_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun parse(raw: String?): ThemeMode = entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

/** In-app language choice. SYSTEM follows the device (or per-app system setting). */
enum class AppLanguage(val tag: String) {
    SYSTEM(""), ENGLISH("en"), TURKISH("tr"), RUSSIAN("ru");

    companion object {
        fun parse(raw: String?): AppLanguage = entries.firstOrNull { it.name == raw } ?: SYSTEM
        fun fromTags(tags: String): AppLanguage =
            entries.firstOrNull { it != SYSTEM && tags.startsWith(it.tag) } ?: SYSTEM
    }
}

data class Settings(
    val name: String = "",
    val defaultSound: String = Sounds.DEFAULT_KEY,
    val hardcore: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM
) {
    val onboarded: Boolean get() = name.isNotBlank()
}

/** Local-only settings (DataStore). Nothing here ever leaves the device. */
class Prefs(context: Context) {
    private val ctx = context.applicationContext
    private val store = ctx.kairosStore

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            name = p[NAME] ?: "",
            defaultSound = p[SOUND] ?: Sounds.DEFAULT_KEY,
            hardcore = p[HARDCORE] ?: false,
            themeMode = ThemeMode.parse(p[THEME]),
            language = AppLanguage.parse(p[LANGUAGE])
        )
    }

    suspend fun setProfile(name: String) {
        store.edit {
            it[NAME] = name
        }
        mirror()
    }

    suspend fun setDefaultSound(key: String) {
        store.edit { it[SOUND] = key }
        mirror()
    }

    suspend fun setHardcore(on: Boolean) {
        store.edit { it[HARDCORE] = on }
        mirror()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
        mirror()
    }

    suspend fun setLanguage(lang: AppLanguage) {
        store.edit { it[LANGUAGE] = lang.name }
        mirror()
    }

    /** The running test alarm (see [Repo.startTest]); not a user setting, so not part of [Settings]. */
    suspend fun testReminderId(): Int? = store.data.first()[TEST_ID]

    suspend fun setTestReminderId(id: Int?) {
        store.edit { if (id == null) it.remove(TEST_ID) else it[TEST_ID] = id }
    }

    /** Name, theme and language are also needed to ring before the first unlock (see [LockedStore]). */
    private suspend fun mirror() {
        val now = settings.first()
        LockedStore.update(ctx) { it?.copy(settings = now) }
    }

    private companion object {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme_mode")
        val NAME = stringPreferencesKey("name")
        val SOUND = stringPreferencesKey("default_sound")
        val HARDCORE = booleanPreferencesKey("hardcore")
        val TEST_ID = intPreferencesKey("test_reminder_id")
    }
}
