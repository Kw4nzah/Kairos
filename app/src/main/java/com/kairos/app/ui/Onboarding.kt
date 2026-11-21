package com.kairos.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** First-launch screen. Copy is neutral because the tone is only known once the age is entered. */
@Composable
fun OnboardingScreen(onDone: (name: String, age: Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    val ageValue = age.toIntOrNull()
    val valid = name.isNotBlank() && ageValue != null && ageValue in 5..120

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Kairos", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Doğru an geldiğinde hatırlatırım. Önce seni tanıyayım.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            label = { Text("Adın") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = age,
            onValueChange = { v -> age = v.filter { it.isDigit() }.take(3) },
            label = { Text("Yaşın") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Bu bilgiler yalnızca bu cihazda saklanır, hiçbir yere gönderilmez. " +
                "Yaşına göre sana hitap şeklimi seçerim.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onDone(name.trim(), ageValue!!) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Başla") }
    }
}
