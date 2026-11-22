package com.kairos.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairos.app.system.ACTION_USER
import com.kairos.app.system.ActionReceiver
import com.kairos.app.system.EXTRA_ACTION
import com.kairos.app.system.EXTRA_ID
import com.kairos.app.ui.KairosTheme
import java.lang.ref.WeakReference

/**
 * Alarm-style popup shown over the lock screen and other apps. It cannot be dismissed with Back;
 * it only closes through a snooze / done button or when the reminder is completed.
 */
class AlarmActivity : ComponentActivity() {
    private var content by mutableStateOf(AlarmContent("", "", "", emptyList()))
    private var reminderId = -1

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
        apply(intent)
        setContent {
            KairosTheme {
                AlarmScreen(content) { action ->
                    sendBroadcast(
                        Intent(this, ActionReceiver::class.java).setAction(ACTION_USER)
                            .putExtra(EXTRA_ID, reminderId).putExtra(EXTRA_ACTION, action)
                    )
                    finishAndRemoveTask()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        apply(intent)
    }

    private fun apply(i: Intent) {
        reminderId = i.getIntExtra(EXTRA_ID, -1)
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
        private var current: WeakReference<AlarmActivity>? = null

        /** Closes the popup if it is showing the given reminder (it was snoozed or completed elsewhere). */
        fun close(id: Int) {
            current?.get()?.takeIf { it.reminderId == id }?.let { a -> a.runOnUiThread { a.finishAndRemoveTask() } }
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
        c.actions.forEachIndexed { i, (key, label) ->
            val m = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            if (i == 0) Button({ onAction(key) }, m) { Text(label) }
            else OutlinedButton({ onAction(key) }, m) { Text(label) }
        }
    }
}
