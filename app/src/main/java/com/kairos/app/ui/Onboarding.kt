package com.kairos.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** First-launch screen: asks only for a name, stored on-device. */
@Composable
fun OnboardingScreen(onDone: (name: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val valid = name.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Kairos", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fadeIn(0))
        Spacer(Modifier.height(8.dp))
        Text(
            "Doğru an geldiğinde hatırlatırım. Önce adını öğreneyim.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fadeIn(1)
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            label = { Text("Adın") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().fadeIn(2)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Adın yalnızca bu cihazda saklanır, hiçbir yere gönderilmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onDone(name.trim()) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().fadeIn(3)
        ) { Text("Başla") }
    }
}
