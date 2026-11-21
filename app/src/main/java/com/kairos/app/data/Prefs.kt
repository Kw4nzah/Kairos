package com.kairos.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kairos.app.system.Sounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kairosStore by preferencesDataStore(name = "kairos_prefs")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun parse(raw: String?): ThemeMode = entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

data class Settings(
    val name: String = "",
    val defaultSound: String = Sounds.DEFAULT_KEY,
    val hardcore: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    val onboarded: Boolean get() = name.isNotBlank()
}

/** Local-only settings (DataStore). Nothing here ever leaves the device. */
class Prefs(context: Context) {
    private val store = context.applicationContext.kairosStore

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            name = p[NAME] ?: "",
            defaultSound = p[SOUND] ?: Sounds.DEFAULT_KEY,
            hardcore = p[HARDCORE] ?: false,
            themeMode = ThemeMode.parse(p[THEME])
        )
    }

    suspend fun setProfile(name: String) {
        store.edit {
            it[NAME] = name
        }
    }

    suspend fun setDefaultSound(key: String) {
        store.edit { it[SOUND] = key }
    }

    suspend fun setHardcore(on: Boolean) {
        store.edit { it[HARDCORE] = on }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val NAME = stringPreferencesKey("name")
        val SOUND = stringPreferencesKey("default_sound")
        val HARDCORE = booleanPreferencesKey("hardcore")
    }
}
