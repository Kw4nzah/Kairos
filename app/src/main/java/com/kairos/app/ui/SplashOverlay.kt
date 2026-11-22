package com.kairos.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.R
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Geometry of the launcher icon (ic_launcher_foreground, 108 x 108 viewport): an open ring whose gap
// is "the right moment" (kairos), an amber dot sitting in the gap and a hand pointing at it.
private const val RING_R = 22f
private const val GAP_CENTER = -75f      // degrees, 0 = 3 o'clock, clockwise
private const val RING_START = 310f      // where the open ring starts
private const val RING_SWEEP = 310f
private const val HAND_LEN = 11f

private fun seg(p: Float, a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun ease(t: Float) = FastOutSlowInEasing.transform(t)

/**
 * Picks up exactly where the system splash stops (same background, same icon, same size and place),
 * then plays the idea of the app once: the hand sweeps a full turn and lands on the amber dot while
 * the ring closes around it, and the name settles underneath. About a second, once per cold start.
 */
@Composable
fun SplashOverlay() {
    var visible by rememberSaveable { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color(0xFF161210) else Color(0xFF0B4F4B)
    val amber = if (dark) Color(0xFFD7B978) else Color(0xFFF2BC6B)

    LaunchedEffect(Unit) {
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale == 0f) {
            progress.snapTo(1f); delay(400)
        } else {
            delay(80)
            progress.animateTo(1f, tween(720, easing = LinearEasing))
            delay(160)
        }
        visible = false
    }

    AnimatedVisibility(visible = visible, exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
            val p = progress.value
            Canvas(Modifier.size(288.dp)) {
                val u = size.width / 108f
                val c = Offset(54f * u, 54f * u)
                val close = ease(seg(p, 0f, 0.62f))
                val e = 25f * close                      // each end of the ring grows 25 degrees to meet at the dot
                drawArc(
                    color = Color.White,
                    startAngle = RING_START - e,
                    sweepAngle = RING_SWEEP + 2 * e,
                    useCenter = false,
                    topLeft = Offset(c.x - RING_R * u, c.y - RING_R * u),
                    size = Size(2 * RING_R * u, 2 * RING_R * u),
                    style = Stroke(width = 5f * u, cap = StrokeCap.Round)
                )
                val gap = GAP_CENTER * (PI / 180.0)
                val dot = Offset(c.x + (RING_R * u * cos(gap)).toFloat(), c.y + (RING_R * u * sin(gap)).toFloat())
                val pulse = 1f + 0.24f * sin(PI * seg(p, 0.5f, 0.85f)).toFloat()
                drawCircle(amber, radius = 5f * u * pulse, center = dot)
                val angle = (GAP_CENTER + 360f * ease(seg(p, 0f, 0.85f))) * (PI / 180.0)
                val tip = Offset(c.x + (HAND_LEN * u * cos(angle)).toFloat(), c.y + (HAND_LEN * u * sin(angle)).toFloat())
                drawLine(amber, c, tip, strokeWidth = 4.5f * u, cap = StrokeCap.Round)
            }
            Column(
                Modifier.offset(y = 118.dp).graphicsLayer {
                    alpha = ease(seg(p, 0.45f, 0.9f)); translationY = (1f - ease(seg(p, 0.45f, 0.9f))) * 8.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.app_name),
                    color = Color(0xFFFFF8F0), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp
                )
                // "Kairos" is Greek for the right moment; shown in the original, in every language.
                Text(
                    "καιρός",
                    modifier = Modifier.graphicsLayer { alpha = 0.8f * ease(seg(p, 0.6f, 1f)) },
                    color = amber, fontSize = 14.sp, letterSpacing = 3.sp
                )
            }
        }
    }
}
