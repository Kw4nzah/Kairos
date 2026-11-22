package com.kairos.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.R
import kotlinx.coroutines.delay

private val SplashBg = Color(0xFF0B4F4B)

/**
 * Continues the system splash screen (same background and icon) and adds the app name, which the
 * system splash cannot show on every Android version. Shown once per cold start, then fades out.
 */
@Composable
fun SplashOverlay() {
    var visible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(700)
        visible = false
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(220))) {
        Box(Modifier.fillMaxSize().background(SplashBg), contentAlignment = Alignment.Center) {
            // Same size and position as the system splash icon so the hand-over is seamless.
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(288.dp))
            Text(
                stringResourceAppName(),
                modifier = Modifier.offset(y = 120.dp),
                color = Color(0xFFFFF8F0), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp
            )
        }
    }
}

@Composable
private fun stringResourceAppName(): String = androidx.compose.ui.res.stringResource(R.string.app_name)
