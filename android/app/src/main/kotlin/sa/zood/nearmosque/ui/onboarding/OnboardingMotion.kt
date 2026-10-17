package sa.zood.nearmosque.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sa.zood.nearmosque.core.OnboardingTiming

/**
 * The onboarding's three springs (shared/design/tokens.json "motion"; Compose stiffness = (2π / response)²), the same
 * on iOS and the desktop: [spring] for steps, presses and the header, [bouncy] for chips, checks and rings, [soft] for
 * the sun, the horizon and anything that glides.
 */
internal object OnbMotion {
    fun <T> spring(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 224f)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 146f)
    fun <T> soft(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 49f)

    const val PRESS_SCALE = 0.96f
    const val PRESS_IN_MS = 90
    /** Reduce Motion: every change is this crossfade instead. */
    const val FADE_MS = 200
}

/** "Remove animations" (or a still test frame): crossfades only, nothing moves. Provided by OnboardingScreen. */
internal val LocalOnbReduce = compositionLocalOf { false }

/** Press feedback: down to 0.96 in 90 ms, back with the spring. Nothing moves under Reduce Motion. */
internal fun Modifier.pressScale(source: InteractionSource): Modifier = composed {
    val reduce = LocalOnbReduce.current
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed && !reduce) OnbMotion.PRESS_SCALE else 1f,
        if (pressed) tween(OnbMotion.PRESS_IN_MS, easing = FastOutSlowInEasing) else OnbMotion.spring(),
        label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

/**
 * A step's child entering: from 24 dp lower and transparent, with the spring, [index] × 40 ms after the step appears
 * (title, body, controls, button). Under Reduce Motion a short fade only.
 */
internal fun Modifier.enter(index: Int): Modifier = composed {
    val reduce = LocalOnbReduce.current
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduce) {
            p.animateTo(1f, tween(OnbMotion.FADE_MS))
        } else {
            delay(index * OnboardingTiming.STAGGER.toLong())
            p.animateTo(1f, OnbMotion.spring())
        }
    }
    val dy = with(LocalDensity.current) { 24.dp.toPx() }
    graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        if (!reduce) translationY = dy * (1 - p.value)
    }
}

/** A check or badge popping in: scale 0.2 → 1 and opacity 0 → 1 with the bouncy spring (a fade under Reduce Motion). */
internal fun Modifier.pop(): Modifier = composed {
    val reduce = LocalOnbReduce.current
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, if (reduce) tween(OnbMotion.FADE_MS) else OnbMotion.bouncy()) }
    graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        if (!reduce) { val s = 0.2f + 0.8f * p.value; scaleX = s; scaleY = s }
    }
}
