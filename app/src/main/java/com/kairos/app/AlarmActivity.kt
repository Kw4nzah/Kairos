package com.kairos.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.os.BundleCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairos.app.data.ThemeMode
import com.kairos.app.system.ACTION_USER
import com.kairos.app.system.ActionReceiver
import com.kairos.app.system.EXTRA_ACTION
import com.kairos.app.system.EXTRA_COUNT
import com.kairos.app.system.EXTRA_DUE
import com.kairos.app.system.EXTRA_ID
import com.kairos.app.ui.KairosTheme
import java.lang.ref.WeakReference

private const val STATE_RINGING = "ringing"

/**
 * Alarm-style popup shown over the lock screen and other apps. It cannot be dismissed with Back;
 * it only closes through a snooze / done button or when the reminder is completed.
 */
class AlarmActivity : ComponentActivity() {
    private var content by mutableStateOf(AlarmContent("", "", "", emptyList()))
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var reminderId = -1

    /** Everything ringing right now. The newest is on screen; answering it brings back the one before. */
    private val ringing = ArrayList<Intent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .let { if (Build.VERSION.SDK_INT >= 26) it.requestDismissKeyguard(this, null) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* intentionally not dismissable */ }
        })
        current = WeakReference(this)
        val saved = savedInstanceState?.let { BundleCompat.getParcelableArrayList(it, STATE_RINGING, Intent::class.java) }
        if (saved.isNullOrEmpty()) ringing.add(intent) else ringing.addAll(saved)
        apply(ringing.last())
        setContent {
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                )
                onDispose { }
            }
            KairosTheme(darkTheme = dark) {
                AlarmScreen(content) { action ->
                    val shown = ringing.lastOrNull()
                    if (shown != null) {
                        val answer = Intent(this, ActionReceiver::class.java).setAction(ACTION_USER)
                            .putExtra(EXTRA_ID, reminderId).putExtra(EXTRA_ACTION, action)
                        if (shown.hasExtra(EXTRA_DUE)) answer.putExtra(EXTRA_DUE, shown.getLongExtra(EXTRA_DUE, 0L))
                        if (shown.hasExtra(EXTRA_COUNT)) answer.putExtra(EXTRA_COUNT, shown.getIntExtra(EXTRA_COUNT, 0))
                        sendBroadcast(answer)
                        dismiss(reminderId)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getIntExtra(EXTRA_ID, -1)
        ringing.removeAll { it.getIntExtra(EXTRA_ID, -1) == id }
        ringing.add(intent)
        apply(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // The last few are plenty, and the saved state has to stay far below the Binder limit.
        outState.putParcelableArrayList(STATE_RINGING, ArrayList(ringing.takeLast(20)))
    }

    private fun dismiss(id: Int) {
        ringing.removeAll { it.getIntExtra(EXTRA_ID, -1) == id }
        val next = ringing.lastOrNull()
        if (next == null) finishAndRemoveTask() else apply(next)
    }

    private fun apply(i: Intent) {
        reminderId = i.getIntExtra(EXTRA_ID, -1)
        themeMode = ThemeMode.parse(i.getStringExtra(X_THEME))
        content = AlarmContent(
            i.getStringExtra(X_TITLE).orEmpty(),
            i.getStringExtra(X_BODY).orEmpty(),
            i.getStringExtra(X_NOTES).orEmpty(),
            (i.getStringArrayExtra(X_ACTIONS) ?: emptyArray()).zip(i.getStringArrayExtra(X_LABELS) ?: emptyArray())
        )
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        const val X_TITLE = "title"
        const val X_BODY = "body"
        const val X_NOTES = "notes"
        const val X_ACTIONS = "actions"
        const val X_LABELS = "labels"
        const val X_THEME = "theme"
        private var current: WeakReference<AlarmActivity>? = null

        /** Closes the popup if it is showing the given reminder (it was snoozed or completed elsewhere). */
        fun close(id: Int) {
            current?.get()?.let { a -> a.runOnUiThread { a.dismiss(id) } }
        }
    }
}

private data class AlarmContent(val title: String, val body: String, val notes: String, val actions: List<Pair<String, String>>)

@androidx.compose.runtime.Composable
private fun AlarmScreen(c: AlarmContent, onAction: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Only the text scrolls: the buttons are the sole way to stop the alarm and must stay on screen.
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(c.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            Text(
                c.body, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp, bottom = if (c.notes.isBlank()) 40.dp else 12.dp)
            )
            if (c.notes.isNotBlank()) {
                Text(
                    c.notes, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 40.dp)
                )
            }
        }
        c.actions.forEachIndexed { i, (key, label) ->
            val m = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            if (i == 0) Button({ onAction(key) }, m) { Text(label) }
            else OutlinedButton({ onAction(key) }, m) { Text(label) }
        }
    }
}
