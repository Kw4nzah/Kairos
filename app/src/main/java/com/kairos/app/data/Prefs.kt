package com.kairos.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kairos.app.system.Sounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kairosStore by preferencesDataStore(name = "kairos_prefs")

data class Settings(
    val name: String = "",
    val age: Int = 0,
    val defaultSound: String = Sounds.DEFAULT_KEY,
    val hardcore: Boolean = false
) {
    val onboarded: Boolean get() = name.isNotBlank() && age > 0
}

/** Local-only settings (DataStore). Nothing here ever leaves the device. */
class Prefs(context: Context) {
    private val store = context.applicationContext.kairosStore

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            name = p[NAME] ?: "",
            age = p[AGE] ?: 0,
            defaultSound = p[SOUND] ?: Sounds.DEFAULT_KEY,
            hardcore = p[HARDCORE] ?: false
        )
    }

    suspend fun setProfile(name: String, age: Int) {
        store.edit {
            it[NAME] = name
            it[AGE] = age
        }
    }

    suspend fun setDefaultSound(key: String) {
        store.edit { it[SOUND] = key }
    }

    suspend fun setHardcore(on: Boolean) {
        store.edit { it[HARDCORE] = on }
    }

    private companion object {
        val NAME = stringPreferencesKey("name")
        val AGE = intPreferencesKey("age")
        val SOUND = stringPreferencesKey("default_sound")
        val HARDCORE = booleanPreferencesKey("hardcore")
    }
}
