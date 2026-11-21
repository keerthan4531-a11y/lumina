package app.lumina.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/**
 * Springs, not durations. Mirrors preview/js/lumina.js + LuminaSpring in the design spec.
 */
object LuminaSpring {
    const val spatialDamping = 0.78f
    const val spatialStiffness = 380f
    const val scaleDamping = 0.75f
    const val scaleStiffness = 900f
    const val softDamping = 0.90f
    const val softStiffness = 180f
    const val entranceStagger = 30 // ms per item

    fun <T> spatial() = spring<T>(dampingRatio = spatialDamping, stiffness = spatialStiffness)
    fun <T> scale() = spring<T>(dampingRatio = scaleDamping, stiffness = scaleStiffness)
    fun <T> soft() = spring<T>(dampingRatio = softDamping, stiffness = softStiffness)
}

/** True when the OS asks for less motion (Settings > Remove animations / animator scale 0). */
@Composable
fun isReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Press physics for every interactive glass surface.
 * The icon and its glass scale together — never the icon alone.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.92f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = isReducedMotion()
    val target = if (pressed && enabled && !reduced) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = LuminaSpring.scale(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fade + slide, 30 ms apart. Cheap, and it is what makes a page feel built rather than loaded.
 * Under reduced motion it becomes an instant appearance (no delay, no offset).
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    enabled: Boolean = true,
    distance: Float = 24f,
    content: @Composable () -> Unit,
) {
    val reduced = isReducedMotion()
    val progress = remember { Animatable(if (reduced || !enabled) 1f else 0f) }

    LaunchedEffect(index, reduced) {
        if (reduced || !enabled) {
            progress.snapTo(1f)
        } else {
            delay(index * LuminaSpring.entranceStagger.toLong())
            progress.animateTo(1f, LuminaSpring.spatial())
        }
    }

    androidx.compose.foundation.layout.Box(
        Modifier
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * distance
            },
    ) {
        content()
    }
}

/** 1 → 1.15 → 1 on state change. Tabs, favourites, counters. Never loops. */
@Composable
fun iconBounce(trigger: Any?): Float {
    val scale = remember { Animatable(1f) }
    val reduced = isReducedMotion()
    LaunchedEffect(trigger) {
        if (trigger == null || reduced) return@LaunchedEffect
        scale.animateTo(1.15f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh))
        scale.animateTo(1f, LuminaSpring.scale())
    }
    return scale.value
}

/** Haptic tick on press — the cheapest way to make glass feel physical. */
@Composable
fun rememberHapticTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            runCatching {
                view.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
            }
            Unit
        }
    }
}
