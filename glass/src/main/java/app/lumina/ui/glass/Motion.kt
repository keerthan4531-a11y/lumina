package app.lumina.ui.glass

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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

/**
 * Springs, not durations. Durations feel mechanical; springs feel physical — which is the
 * whole point of "liquid" glass.
 */
object LuminaSpring {
    /** size / position / layout-ish changes */
    const val spatialDamping = 0.78f
    const val spatialStiffness = 380f

    /** press feedback — fast and tight, overshoot <= 2% */
    const val scaleDamping = 0.75f
    const val scaleStiffness = 900f

    /** fades, colours, anything the eye reads as "soft" */
    const val softDamping = 0.90f
    const val softStiffness = 180f

    /** ms between staggered list items */
    const val entranceStagger = 30

    fun <T> spatial(): androidx.compose.animation.core.SpringSpec<T> =
        spring(dampingRatio = spatialDamping, stiffness = spatialStiffness)

    fun <T> scale(): androidx.compose.animation.core.SpringSpec<T> =
        spring(dampingRatio = scaleDamping, stiffness = scaleStiffness)

    fun <T> soft(): androidx.compose.animation.core.SpringSpec<T> =
        spring(dampingRatio = softDamping, stiffness = softStiffness)
}

private const val PRESS_SCALE = 0.92f

/**
 * Press physics for every interactive glass surface.
 * Icon and its glass background scale together — never the icon alone.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = PRESS_SCALE,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && enabled && !isReducedMotion()) pressedScale else 1f
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
 * Micro-bounce on state change: tabs, favourites, tracker counter.
 * 1 -> 1.15 -> 1, no loop, no idle animation.
 */
@Composable
fun iconBounce(trigger: Any?): Float {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == null || isReducedMotion()) return@LaunchedEffect
        scale.animateTo(1.15f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh))
        scale.animateTo(1f, LuminaSpring.scale())
    }
    return scale.value
}

/**
 * Accessibility + battery: honour the system "remove animations" setting.
 * When true: springs become short fades, no scale, no stagger.
 */
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
