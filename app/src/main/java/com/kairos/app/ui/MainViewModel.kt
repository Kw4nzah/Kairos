package com.kairos.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.Reminder
import com.kairos.app.data.AppLanguage
import com.kairos.app.data.Repo
import com.kairos.app.data.Settings
import com.kairos.app.data.ThemeMode
import com.kairos.app.system.Locales
import com.kairos.app.system.Notifier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)

    val reminders: StateFlow<List<Reminder>> =
        repo.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** null while DataStore is still loading (avoids flashing the onboarding screen). */
    val settings: StateFlow<Settings?> =
        repo.prefs.settings.stateIn<Settings?>(viewModelScope, SharingStarted.Eagerly, null)

    /** Set by the widget "+" button; MainApp consumes it and opens the new-reminder screen. */
    val newRequested = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun save(r: Reminder) = viewModelScope.launch { repo.save(r) }
    fun delete(id: Int) = viewModelScope.launch { repo.delete(id) }
    fun complete(r: Reminder) = viewModelScope.launch { repo.complete(r.id, r.dueAt) }
    fun reopen(id: Int) = viewModelScope.launch { repo.reopen(id) }

    fun saveProfile(name: String) = viewModelScope.launch { repo.prefs.setProfile(name) }
    fun setDefaultSound(key: String) = viewModelScope.launch { repo.prefs.setDefaultSound(key) }

    /** Applies the language live, stores it for non-UI code (alarms) and refreshes channel names. */
    fun setLanguage(lang: AppLanguage) {
        Locales.apply(lang)
        val app = getApplication<Application>()
        viewModelScope.launch {
            repo.prefs.setLanguage(lang)
            Notifier.ensureChannels(Locales.localized(app, lang))
        }
    }

    /** Keeps the stored language in step with a change made through the system per-app language screen. */
    fun syncLanguageFromSystem() = viewModelScope.launch {
        val now = Locales.current()
        if (now != repo.prefs.settings.first().language) repo.prefs.setLanguage(now)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.prefs.setThemeMode(mode) }

    /** Caller must have obtained calendar permissions before enabling. */
    fun setHardcore(on: Boolean) = viewModelScope.launch {
        repo.prefs.setHardcore(on)
        if (on) repo.syncAllToCalendar() else repo.removeAllFromCalendar()
    }
}
