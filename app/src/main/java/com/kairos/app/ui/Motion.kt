package com.kairos.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

/** Shared easing: quick start, soft landing. */
val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

const val DurShort = 110
const val DurMed = 170

/**
 * The app draws edge to edge, so the status bar is transparent. This paints a solid strip in the
 * page colour behind the status bar icons and, below it, a smoothstep fade so scrolling content
 * dissolves instead of showing through (same idea as Poreia's StatusBarBackground).
 */
@Composable
fun StatusBarScrim(fade: Dp = 24.dp, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density)
    val fadePx = with(density) { fade.toPx() }
    if (top == 0 && fadePx == 0f) return
    val total = with(density) { (top + fadePx).toDp() }
    Box(
        modifier
            .fillMaxWidth()
            .height(total)
            .drawWithContent {
                val solid = if (size.height > 0f) top / size.height else 1f
                val stops = ArrayList<Pair<Float, Color>>()
                stops += 0f to bg
                stops += solid to bg
                if (fadePx > 0f) {
                    val steps = 12
                    for (i in 1..steps) {
                        val t = i / steps.toFloat()
                        val smooth = t * t * (3 - 2 * t)
                        stops += (solid + (1f - solid) * t) to bg.copy(alpha = 1f - smooth)
                    }
                }
                drawRect(Brush.verticalGradient(colorStops = stops.toTypedArray()))
            }
    )
}

/** Rises a few dp and fades in once; `index` staggers siblings a little. Snaps when animations are off. */
fun Modifier.fadeIn(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale == 0f) {
            progress.snapTo(1f)
        } else {
            delay(minOf(index, 4) * 25L)
            progress.animateTo(1f, tween(200, easing = EaseOut))
        }
    }
    val rise = with(LocalDensity.current) { 8.dp.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/** Gentle spring press feedback; pair with a clickable that shares the same interaction source. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.98f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return graphicsLayer { scaleX = s; scaleY = s }
}

/** Title text whose strike-through draws across left to right when `struck` turns on. */
@Composable
fun StrikeText(
    text: String,
    struck: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    val progress by animateFloatAsState(if (struck) 1f else 0f, tween(DurMed, easing = EaseOut), label = "strike")
    val fade by animateFloatAsState(if (struck) 0.62f else 1f, tween(DurMed, easing = EaseOut), label = "strikeFade")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = MaterialTheme.colorScheme.onSurface
    Text(
        text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier
            .alpha(fade)
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                if (progress <= 0f) return@drawWithContent
                for (i in 0 until l.lineCount) {
                    val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2f
                    val x0 = l.getLineLeft(i)
                    val x1 = x0 + (l.getLineRight(i) - x0) * progress
                    drawLine(color, Offset(x0, y), Offset(x1, y), strokeWidth = 1.5.dp.toPx())
                }
            }
    )
}
