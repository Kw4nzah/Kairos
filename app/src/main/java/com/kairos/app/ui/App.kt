package com.kairos.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.stringResource
import com.kairos.app.R
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.Reminder
import com.kairos.app.data.Settings
import com.kairos.app.logic.ToneSet
import kotlinx.coroutines.launch

@Composable
fun KairosRoot(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    when {
        s == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        !s.onboarded -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            OnboardingScreen { name -> vm.saveProfile(name) }
            StatusBarScrim(fade = 0.dp)
        }
        else -> MainApp(vm, s)
    }
}

@Composable
private fun MainApp(vm: MainViewModel, settings: Settings) {
    val ctx = LocalContext.current
    val tone = remember(ctx) { ToneSet(ctx) }
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var editOpen by rememberSaveable { mutableStateOf(false) }
    // 0 = new reminder, >0 = editing that reminder id
    var editingId by rememberSaveable { mutableStateOf(0) }
    val newRequested by vm.newRequested.collectAsStateWithLifecycle()
    LaunchedEffect(newRequested) {
        if (newRequested) {
            editingId = 0; editOpen = true
            vm.newRequested.value = false
        }
    }

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
    // Keeps the last edited reminder around so the edit screen does not flash empty while it animates out.
    val shown = remember { arrayOfNulls<Reminder>(1) }
    if (editingId == 0) shown[0] = null else if (existing != null) shown[0] = existing

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = editOpen,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(DurMed, easing = EaseOut)) +
                        slideInVertically(tween(DurMed, easing = EaseOut)) { it / 14 }) togetherWith
                        fadeOut(tween(DurShort))
                } else {
                    (fadeIn(tween(DurMed, easing = EaseOut)) +
                        scaleIn(tween(DurMed, easing = EaseOut), initialScale = 0.985f)) togetherWith
                        (fadeOut(tween(DurShort)) +
                            slideOutVertically(tween(DurMed, easing = EaseOut)) { it / 14 })
                }
            },
            label = "screen"
        ) { editing ->
            if (editing) {
                BackHandler { editOpen = false }
                EditScreen(
                    existing = shown[0],
                    defaultSound = settings.defaultSound,
                    onBack = { editOpen = false },
                    onSave = { r ->
                        vm.save(r)
                        say(if (r.id == 0) tone.snackAdded else tone.snackUpdated)
                        editOpen = false
                    },
                    onDelete = { id ->
                        vm.delete(id)
                        say(tone.snackDeleted)
                        editOpen = false
                    }
                )
            } else {
                MainScaffold(
                    tab = tab,
                    onTab = { tab = it },
                    snackbar = snackbar,
                    onNew = { editingId = 0; editOpen = true }
                ) { padding ->
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(DurMed, easing = EaseOut)) +
                                slideInHorizontally(tween(DurMed, easing = EaseOut)) { dir * it / 12 }) togetherWith
                                (fadeOut(tween(DurShort)) +
                                    slideOutHorizontally(tween(DurMed, easing = EaseOut)) { -dir * it / 12 })
                        },
                        label = "tab"
                    ) { t ->
                        when (t) {
                            0 -> TasksScreen(
                                reminders = reminders,
                                tone = tone,
                                name = settings.name,
                                padding = padding,
                                onToggle = { r ->
                                    if (r.done) {
                                        vm.reopen(r.id); say(tone.snackReopened)
                                    } else {
                                        vm.complete(r); say(tone.snackDone)
                                    }
                                },
                                onOpen = { editingId = it.id; editOpen = true }
                            )
                            1 -> CalendarScreen(
                                reminders = reminders,
                                tone = tone,
                                padding = padding,
                                onOpen = { editingId = it.id; editOpen = true }
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
        }
        // Solid strip behind the status bar. The edit screen has its own app bar, so no fade there.
        val fade by animateDpAsState(if (editOpen) 0.dp else 24.dp, tween(DurMed), label = "scrimFade")
        StatusBarScrim(fade = fade)
    }
}

@Composable
private fun MainScaffold(
    tab: Int,
    onTab: (Int) -> Unit,
    snackbar: SnackbarHostState,
    onNew: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = tab < 2,
                enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(DurShort)),
                exit = scaleOut(tween(DurShort)) + fadeOut(tween(DurShort))
            ) {
                FloatingActionButton(onClick = onNew) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_task))
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { onTab(0) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_tasks)) }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { onTab(1) },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_calendar)) }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { onTab(2) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        },
        content = content
    )
}
