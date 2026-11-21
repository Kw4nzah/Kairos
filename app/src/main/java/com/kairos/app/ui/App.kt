package com.kairos.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.Settings
import com.kairos.app.logic.Tone
import kotlinx.coroutines.launch

@Composable
fun KairosRoot(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    when {
        s == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        !s.onboarded -> OnboardingScreen { name, age -> vm.saveProfile(name, age) }
        else -> MainApp(vm, s)
    }
}

@Composable
private fun MainApp(vm: MainViewModel, settings: Settings) {
    val ctx = LocalContext.current
    val tone = remember(settings.age) { Tone.forAge(settings.age) }
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    // -1 = list, 0 = new reminder, >0 = editing that reminder id
    var editingId by rememberSaveable { mutableStateOf(-1) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun say(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    val existing = reminders.firstOrNull { it.id == editingId }
    when {
        editingId == 0 || (editingId > 0 && existing != null) -> {
            BackHandler { editingId = -1 }
            EditScreen(
                existing = existing,
                defaultSound = settings.defaultSound,
                onBack = { editingId = -1 },
                onSave = { r ->
                    vm.save(r)
                    say(if (r.id == 0) tone.snackAdded else tone.snackUpdated)
                    editingId = -1
                },
                onDelete = { id ->
                    vm.delete(id)
                    say(tone.snackDeleted)
                    editingId = -1
                }
            )
        }
        editingId > 0 -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        else -> Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (tab < 2) {
                    FloatingActionButton(onClick = { editingId = 0 }) {
                        Icon(Icons.Default.Add, contentDescription = "Yeni görev")
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Görevler") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        label = { Text("Takvim") }
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Ayarlar") }
                    )
                }
            }
        ) { padding ->
            when (tab) {
                0 -> TasksScreen(
                    reminders = reminders,
                    tone = tone,
                    name = settings.name,
                    padding = padding,
                    onToggle = { r ->
                        if (r.done) {
                            vm.reopen(r.id); say(tone.snackReopened)
                        } else {
                            vm.complete(r.id); say(tone.snackDone)
                        }
                    },
                    onOpen = { editingId = it.id }
                )
                1 -> CalendarScreen(
                    reminders = reminders,
                    tone = tone,
                    padding = padding,
                    onOpen = { editingId = it.id }
                )
                else -> SettingsScreen(
                    settings = settings,
                    tone = tone,
                    padding = padding,
                    vm = vm,
                    say = ::say
                )
            }
        }
    }
}
