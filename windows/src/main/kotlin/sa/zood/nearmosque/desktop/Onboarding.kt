package sa.zood.nearmosque.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.zood.nearmosque.core.AdhanChoice
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.City
import sa.zood.nearmosque.core.DayArc
import sa.zood.nearmosque.core.DaySchedule
import sa.zood.nearmosque.core.FridayPreview
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.OnboardingFlow
import sa.zood.nearmosque.core.OnboardingStep
import sa.zood.nearmosque.core.OnboardingTiming
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.Qibla
import sa.zood.nearmosque.core.WeatherCondition
import sa.zood.nearmosque.core.WeatherLook
import sa.zood.nearmosque.core.WeatherNow
import sa.zood.nearmosque.core.WeatherPreview
import java.time.Duration
import java.time.Instant
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * "First Light" on Windows and Linux: the phones' first-run flow (ios OnboardingView, android OnboardingScreen) on
 * the same shared core (sa.zood.nearmosque.core.OnboardingFlow, OnboardingTiming, WeatherPreview, FridayPreview,
 * OnboardingFeature). Twelve steps: the desktop has one dark look and no style or appearance setting, so no "look".
 * A PC has no location permission to ask: the place comes from the city table, the time zone's largest city offered
 * first. No notification prompt either: the desktop shows its notifications without asking. Every answer is saved
 * through the real setting the moment it is given, so closing the window mid-way loses nothing.
 *
 * A wide window has two panes (the sky, the sun and "YOUR DAY" on the leading side, the step on glass on the
 * trailing side); a narrow one is one column on the sky. With the desktop's animations off (Platform.animations)
 * every change is a short crossfade and nothing moves.
 */

/** The onboarding's colours on the desktop's one dark theme (Main.kt's Navy, Card, Gold, Ink, Muted). */
internal object OnbLook {
    /** Glass: the card colour, see-through, with a hairline. */
    val glass = Card.copy(alpha = 0.72f)
    val line = Ink.copy(alpha = 0.18f)
    /** Rows and small buttons on the glass, a shade brighter under the pointer. */
    val fill = Color.White.copy(alpha = 0.08f)
    val fillHover = Color.White.copy(alpha = 0.14f)
    val chip = Color.White.copy(alpha = 0.14f)
    val chipLine = Color.White.copy(alpha = 0.28f)
    val placeholder = Color.White.copy(alpha = 0.36f)
    val track = Color.White.copy(alpha = 0.3f)
    val accentSoft = Gold.copy(alpha = 0.18f)
    /** Secondary text straight on the sky: 80% of the ink, with [textShadow]. */
    val inkSoft = Ink.copy(alpha = 0.8f)
    val textShadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset.Zero, 7f)
    // The hero (shared/design/tokens.json sunCore, sunMid, sunEdge, moonlight).
    val sunCore = Color(0xFFFFF6DA)
    val sunMid = Color(0xFFF7C76A)
    val sunEdge = Color(0xFFE9A23B)
    val moonlight = Color(0xFFC9D8FF)
    val card = RoundedCornerShape(16.dp)
    val panel = RoundedCornerShape(22.dp)
    val capsule = RoundedCornerShape(50)
}

/** The three springs (tokens.json "motion"; stiffness = (2π / response)²), the same as on Android and iOS. */
internal object OnbMotion {
    /** Steps, presses, the header. */
    fun <T> spring(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 224f)
    /** Chips, checks, rings. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 146f)
    /** The sun and anything that glides. */
    fun <T> soft(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 49f)

    const val PRESS_SCALE = 0.96f
    const val PRESS_IN_MS = 90
    /** With animations off, every change is this crossfade instead. */
    const val FADE_MS = 200
}

/** The desktop's animations are off, or a still frame: crossfades only, nothing moves. */
internal val LocalOnbReduce = compositionLocalOf { false }
/** A still frame (the screenshot tests): every animation at its end, no timers, no auto-advance. */
internal val LocalOnbStill = compositionLocalOf { false }
/** Text straight on the sky (the one-column layout, the leading pane) carries a soft shadow. */
private val LocalOnSky = compositionLocalOf { false }
/**
 * Whether the keyboard moved last (Tab, the arrows) rather than the pointer: focus rings show only then, not after a
 * click, as a browser's focus-visible.
 */
private val LocalOnbKeyboard = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }

/** Material icons (Apache-2.0) the desktop has no drawable for; the Android app takes the same ones from Icons.Filled. */
internal object OnbIcons {
    private fun icon(name: String, path: String, mirror: Boolean = false) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).addPath(addPathNodes(path), fill = SolidColor(Color.White)).build()

    val back = icon("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z", mirror = true)
    val chevron = icon("ChevronRight", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z", mirror = true)
    val check = icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val play = icon("PlayArrow", "M8,5v14l11,-7z")
    val stop = icon("Stop", "M6,6h12v12H6z")
    val date = icon(
        "DateRange",
        "M9,11H7v2h2v-2zM13,11h-2v2h2v-2zM17,11h-2v2h2v-2zM19,4h-1V2h-2v2H8V2H6v2H5c-1.11,0 -1.99,0.9 -1.99,2L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM19,20H5V9h14v11z",
    )
    val computer = icon("Computer", "M20,18c1.1,0 1.99,-0.9 1.99,-2L22,6c0,-1.1 -0.9,-2 -2,-2H4c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2H0v2h24v-2h-4zM4,6h16v10H4V6z")
}

/** One of the Android app's vector icons, copied into the app's resources by the build (build.gradle.kts). */
@Composable
internal fun onbIcon(name: String): Painter = painterResource("icons/$name.xml")

/** The weather condition's icon (the night one under the stars), as on the weather line. */
@Composable
private fun weatherIcon(c: WeatherCondition, night: Boolean): Painter = painterResource(weatherIconPath(c, night))

// The state and what every step is given

/** The Allow (here: Finish) button's three looks: its label, a circle with a spinner, a circle with the check. */
internal enum class AllowPhase { IDLE, WORKING, DONE }

/** What the flow keeps while it runs: the step, the answers given, the weather preview, the day arc's scrubbing, the sun. */
@Stable
internal class OnbState(start: OnboardingStep, preview: WeatherCondition, flow: OnboardingFlow) {
    var step by mutableStateOf(start)
    var leaving by mutableStateOf(false)
    /** The furthest step reached: every step before it counts as passed (starting later counts the earlier as answered). */
    var furthest by mutableIntStateOf(flow.index(start).coerceAtLeast(0))
    var answered by mutableStateOf(emptySet<OnboardingStep>())
    var preview by mutableStateOf(preview)
    var previewPicked by mutableStateOf(false)
    /** The day step: the half shown (null: the one in progress) and the position on it (null: now). */
    var half by mutableStateOf<DayArc.Half?>(null)
    var scrub by mutableStateOf<Float?>(null)
    var scrubPeriod by mutableStateOf<SkyPeriod?>(null)
    var arcIsDay by mutableStateOf(true)
    /** The sun's place on the day arc and the header's sun slot, in pixels from the onboarding's top left. */
    var arcPoint by mutableStateOf<Offset?>(null)
    var slot by mutableStateOf<Offset?>(null)
    var origin = Offset.Zero
    /** Bounces for an answer chosen again (the chip already in the header). */
    var methodBump by mutableIntStateOf(0)
    var asrBump by mutableIntStateOf(0)
    var adhanBump by mutableIntStateOf(0)
    var fridayBump by mutableIntStateOf(0)
    var sheet by mutableStateOf(false)
}

/** Everything a step needs: the real settings and actions, the layout, and how to move on. */
internal class Onb(
    val state: OnbState,
    val flow: OnboardingFlow,
    val settings: DesktopSettings,
    /** The app's city: the one chosen, or (before a choice) the time zone's largest, used only for the sky. */
    val city: City?,
    val clock: Clock?,
    val now: Instant,
    /** The real weather for the sky (null while Sky follows the weather is off or has no fresh data). */
    val weather: WeatherNow?,
    val wide: Boolean,
    val still: Boolean,
    val reduce: Boolean,
    val tray: Boolean,
    val startup: Boolean,
    val update: (DesktopSettings) -> Unit,
    val pickCity: (City) -> Unit,
    val go: (OnboardingStep) -> Unit,
    /** Moves on after the answer has been seen ([OnboardingTiming.ADVANCE]), if still on [from]. */
    val advance: (from: OnboardingStep, to: OnboardingStep) -> Unit,
) {
    val step: OnboardingStep get() = state.step
    /** The city chosen (none before the place step is answered). */
    val chosen: City? get() = city?.takeIf { it.id == settings.cityId }
    val lang: String get() = Strings.language

    fun answered(s: OnboardingStep): Boolean = s in state.answered || flow.index(s) < state.furthest
    fun answer(s: OnboardingStep) { if (s !in state.answered) state.answered = state.answered + s }
    fun next(): OnboardingStep = flow.next(step) ?: OnboardingStep.DONE
}

/**
 * "First Light": the first-run flow on the living sky. A promise, then the place, the Asr school, the Adhan, Friday
 * and the weather sky, each answer flying up into the "YOUR DAY" header; the day built from them; what else the app
 * does; exactly what leaves the PC; and the sun landing where it really is. Escape steps back. [startStep], [still],
 * [preview] and [tray] are for the tests (a later step, a still frame, the weather preview, the notification area);
 * [startup] says whether start at sign-in exists here.
 */
@Composable
internal fun OnboardingDesktop(
    settings: DesktopSettings,
    city: City?,
    now: Instant,
    onSettings: (DesktopSettings) -> Unit,
    onCity: (City) -> Unit,
    weather: WeatherNow? = null,
    animate: Boolean = Platform.animations,
    startStep: OnboardingStep = OnboardingStep.PROMISE,
    still: Boolean = false,
    preview: WeatherCondition? = null,
    tray: Boolean = Platform.hasTray,
    startup: Boolean = Startup.available || Startup.isStorePackage,
) {
    val flow = remember { OnboardingFlow(hasLook = false) }
    val start = startStep.takeIf { it in flow.steps } ?: OnboardingStep.PROMISE
    val state = remember { OnbState(start, preview ?: WeatherPreview.order.first(), flow) }
    val reduce = still || !animate
    val scope = rememberCoroutineScope()
    var moving by remember { mutableStateOf<Job?>(null) }
    val clock = remember(city, settings) { city?.let { Clock(it, settings) } }
    fun go(to: OnboardingStep) {
        moving?.cancel()
        moving = scope.launch {
            if (!still) {
                state.leaving = true
                delay(OnboardingTiming.LEAVE.toLong())
            }
            state.step = to
            state.leaving = false
            state.furthest = maxOf(state.furthest, flow.index(to))
        }
    }
    fun advance(from: OnboardingStep, to: OnboardingStep) {
        if (still) return
        scope.launch {
            delay(OnboardingTiming.ADVANCE.toLong())
            if (state.step == from) go(to)
        }
    }
    // The weather step's preview changes condition until one is picked (crossfades, also with animations off).
    LaunchedEffect(state.step, state.previewPicked, still) {
        if (state.step != OnboardingStep.WEATHER || state.previewPicked || still) return@LaunchedEffect
        while (true) {
            delay(OnboardingTiming.PREVIEW_CYCLE.toLong())
            val order = WeatherPreview.order
            state.preview = order[(order.indexOf(state.preview) + 1) % order.size]
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val keyboard = remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Navy)
            .onGloballyPositioned { state.origin = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) keyboard.value = false
                    }
                }
            }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) keyboard.value = true
                if (e.type != KeyEventType.KeyDown || e.key != Key.Escape) return@onPreviewKeyEvent false
                if (state.sheet) { state.sheet = false; return@onPreviewKeyEvent true }
                flow.back(state.step)?.let { go(it); true } ?: false
            }
            .focusRequester(focus).focusable(),
    ) {
        val wide = maxWidth >= 760.dp && maxWidth > maxHeight * 1.05f
        val o = Onb(
            state, flow, settings, city, clock, now, if (settings.skyWeather) weather else null, wide, still, reduce, tray, startup,
            update = onSettings, pickCity = onCity, go = ::go, advance = ::advance,
        )
        CompositionLocalProvider(LocalOnbReduce provides reduce, LocalOnbStill provides still, LocalOnbKeyboard provides keyboard) {
            OnbSky(o)
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.58f).fillMaxHeight()) { LeadingPane(o) }
                    Box(Modifier.weight(0.42f).fillMaxHeight()) { StepHost(o) }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 520.dp).fillMaxSize()) {
                        CompositionLocalProvider(LocalOnSky provides true) {
                            DayHeader(o, Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp))
                            Box(Modifier.weight(1f).fillMaxWidth()) { StepHost(o) }
                        }
                    }
                }
            }
            HeroLayer(o, with(LocalDensity.current) { Size(maxWidth.toPx(), maxHeight.toPx()) })
            if (state.sheet) MethodSheet(o)
        }
    }
}

// The sky, the panes and the step

/** The period whose sky is shown: the scrubbed moment on the day step, midday on a wide Friday, otherwise now. */
private fun skyPeriod(o: Onb): SkyPeriod {
    val s = o.state
    if (o.step == OnboardingStep.DAY) s.scrubPeriod?.let { return it }
    if (o.step == OnboardingStep.FRIDAY && o.wide) return SkyPeriod.DAY
    return o.clock?.let { SkyPeriod.of(it, o.now) } ?: SkyPeriod.NIGHT
}

/** The weather drawn: the preview on the weather step (samples, never sent for), else the real weather if it is on. */
private fun skyWeather(o: Onb): WeatherNow? =
    if (o.step == OnboardingStep.WEATHER) WeatherPreview.now(o.state.preview, o.now) else o.weather

/**
 * The whole window's sky: the desktop's time-of-day sky (SkyStrip) unframed, no ridges. The weather preview
 * crossfades between conditions; in one column the promise and the end get a scrim under their text.
 */
@Composable
private fun OnbSky(o: Onb) {
    val period = skyPeriod(o)
    val animate = !o.reduce
    if (o.step == OnboardingStep.WEATHER) {
        Crossfade(o.state.preview, animationSpec = tween(if (o.still) 0 else OnboardingTiming.PREVIEW_FADE), label = "preview") { c ->
            SkyStrip(period, WeatherPreview.now(c, o.now), o.now, animate, Modifier.fillMaxSize(), framed = false)
        }
    } else {
        SkyStrip(period, o.weather, o.now, animate, Modifier.fillMaxSize(), framed = false)
    }
    if (!o.wide) {
        val hero = o.step == OnboardingStep.PROMISE || o.step == OnboardingStep.DONE
        val scrim by animateFloatAsState(if (hero) 1f else 0f, if (o.still) snap() else tween(450), label = "scrim")
        if (scrim > 0f) {
            val low = lerp(SkyColors.of(period).low, Color.Black, 0.45f)
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                drawRect(
                    Brush.verticalGradient(
                        0.5f to low.copy(alpha = 0f), 0.7f to low.copy(alpha = 0.55f * scrim), 1f to low.copy(alpha = 0.85f * scrim),
                    ),
                )
            }
        }
    }
}

/**
 * The leading pane of a wide window: the header at its top, the mosque skyline under the Friday step's midday sky,
 * and the large day arc on the day (where the sun rides it), privacy and done steps.
 */
@Composable
private fun LeadingPane(o: Onb) {
    CompositionLocalProvider(LocalOnSky provides true) {
        Box(Modifier.fillMaxSize()) {
            val friday by animateFloatAsState(
                if (o.step == OnboardingStep.FRIDAY) 1f else 0f, if (o.still) snap() else tween(OnboardingTiming.PREVIEW_FADE), label = "skyline",
            )
            if (friday > 0f) {
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                    val w = size.width * 0.62f
                    val h = w * 0.24f
                    val color = lerp(SkyColors.of(SkyPeriod.DAY).low, Color.Black, 0.35f)
                    drawMosqueSkyline(Offset((size.width - w) / 2, size.height - h), Size(w, h), color.copy(alpha = friday))
                }
            }
            val inArc = o.step == OnboardingStep.DAY || o.step == OnboardingStep.PRIVACY || o.step == OnboardingStep.DONE
            val arc by animateFloatAsState(if (inArc) 1f else 0f, if (o.still) snap() else tween(450), label = "arc")
            if (arc > 0f && o.clock != null) {
                Box(
                    Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 92.dp, bottom = 32.dp).graphicsLayer { alpha = arc },
                    contentAlignment = Alignment.Center,
                ) {
                    DayArcView(o, o.clock, interactive = o.step == OnboardingStep.DAY, disc = if (o.step == OnboardingStep.DONE) 46.dp else 17.dp)
                }
            }
            DayHeader(o, Modifier.padding(top = 18.dp, start = 24.dp, end = 24.dp))
        }
    }
}

/** The current step, leaving with a short fade up (blurred) and entering with the spring, each step afresh. */
@Composable
private fun StepHost(o: Onb) {
    val leave by animateFloatAsState(
        if (o.state.leaving) 1f else 0f,
        if (o.still || !o.state.leaving) snap() else tween(OnboardingTiming.LEAVE, easing = FastOutLinearInEasing), label = "leave",
    )
    val blur = if (!o.reduce && leave > 0f) Modifier.blur((6 * leave).dp) else Modifier
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - leave; if (!o.reduce) translationY = -12.dp.toPx() * leave }.then(blur)) {
        val step = o.step
        key(step) {
            // Every step after the place needs one (only a test or a replay could start there without).
            if (o.chosen == null && o.flow.index(step) > o.flow.index(OnboardingStep.PLACE) && step != OnboardingStep.DONE) PlaceStep(o)
            else when (step) {
                OnboardingStep.PROMISE -> PromiseStep(o)
                OnboardingStep.PLACE -> PlaceStep(o)
                OnboardingStep.ASR -> AsrStep(o)
                OnboardingStep.ADHAN -> AdhanStep(o)
                OnboardingStep.FRIDAY -> FridayStep(o)
                OnboardingStep.WEATHER -> WeatherStep(o)
                OnboardingStep.LOOK -> Unit // not in the desktop's flow (OnboardingDesktop never starts there)
                OnboardingStep.BUILDING -> BuildingStep(o)
                OnboardingStep.DAY -> DayStep(o)
                OnboardingStep.EXTRAS -> ExtrasStep(o)
                OnboardingStep.MORE -> MoreStep(o)
                OnboardingStep.PRIVACY -> PrivacyStep(o)
                OnboardingStep.DONE -> DoneStep(o)
            }
        }
    }
}

/**
 * One step's frame. Wide: a glass panel (at most 440 wide, at least 360 tall, centred) in the trailing pane, with the
 * top bar inside. One column: the top bar under the header, then the content, 24 from each side. The content scrolls
 * when it does not fit ([scroll]; off, it fills the height and lays itself out), and [button] stays pinned at the
 * bottom. [hero] steps (the promise, the end) centre their content in the panel, or anchor it to the bottom.
 */
@Composable
internal fun StepFrame(
    o: Onb,
    button: (@Composable () -> Unit)? = null,
    scroll: Boolean = true,
    hero: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    /** Called when the content runs past the panel (it then scrolls), for a step that can set itself tighter. */
    onOverflow: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val back = o.flow.back(o.step)
    val skip = o.flow.skip(o.step)
    val bar = back != null || skip != null || trailing != null
    val scrollState = rememberScrollState()
    if (onOverflow != null) LaunchedEffect(scrollState.maxValue) { if (scrollState.maxValue > 0) onOverflow() }
    val body: @Composable () -> Unit = {
        val arrangement = when {
            hero && o.wide -> Arrangement.Center
            hero -> Arrangement.Bottom
            else -> Arrangement.Top
        }
        // The 4 dp inside the scroll (and its clip) leave room for the rings drawn around rows and cards.
        Column(
            if (scroll) Modifier.fillMaxWidth().scrollEdges(scrollState).verticalScroll(scrollState).padding(4.dp)
            else Modifier.fillMaxWidth().padding(horizontal = 4.dp).fillMaxHeight(),
            verticalArrangement = arrangement, content = content,
        )
    }
    val barSlot: @Composable () -> Unit = {
        if (bar) TopBar(o, back, skip, trailing, Modifier.padding(bottom = 8.dp))
    }
    val buttonSlot: @Composable () -> Unit = {
        if (button != null) Box(Modifier.fillMaxWidth().padding(top = 16.dp)) { button() }
    }
    if (o.wide) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 22.dp), contentAlignment = Alignment.Center) {
            PanelColumn(
                minHeight = 360.dp, fill = !scroll, center = hero, bar = barSlot, body = body, button = buttonSlot,
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().heightIn(max = maxHeight).glass(OnbLook.panel)
                    .padding(top = if (bar) 16.dp else 28.dp, start = 28.dp, end = 28.dp, bottom = 28.dp),
            )
        }
    } else {
        val side = if (hero) 24.dp else 20.dp
        PanelColumn(
            minHeight = 0.dp, fill = true, center = false, bar = barSlot, body = body, button = buttonSlot,
            modifier = Modifier.fillMaxSize().padding(top = 10.dp, start = side, end = side, bottom = 24.dp),
        )
    }
}

/** The scrolling content fades out at an edge with more beyond it, so a cut-off card reads as "scroll for more". */
private fun Modifier.scrollEdges(state: androidx.compose.foundation.ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val edge = 28.dp.toPx() / size.height.coerceAtLeast(1f)
        val top = if (state.canScrollBackward) 0f else 1f
        val bottom = if (state.canScrollForward) 0f else 1f
        if (top < 1f || bottom < 1f) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = top), edge to Color.Black, 1 - edge to Color.Black, 1f to Color.Black.copy(alpha = bottom),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * The panel's column: the bar at the top, the button at the bottom, the body between them, given what height is left.
 * Not [fill]ing, the panel is as tall as its content (at least [minHeight]) with the button at its bottom; [center]
 * centres a short body in that height.
 */
@Composable
private fun PanelColumn(
    minHeight: Dp, fill: Boolean, center: Boolean, bar: @Composable () -> Unit, body: @Composable () -> Unit, button: @Composable () -> Unit,
    modifier: Modifier,
) {
    Layout(contents = listOf(bar, body, button), modifier = modifier) { (barM, bodyM, buttonM), c ->
        val w = c.maxWidth
        val loose = Constraints(minWidth = w, maxWidth = w)
        val barP = barM.map { it.measure(loose) }
        val buttonP = buttonM.map { it.measure(loose) }
        val barH = barP.maxOfOrNull { it.height } ?: 0
        val buttonH = buttonP.maxOfOrNull { it.height } ?: 0
        val maxH = if (c.hasBoundedHeight) c.maxHeight else 100_000
        val minH = minHeight.roundToPx().coerceAtLeast(c.minHeight).coerceAtMost(maxH)
        val room = (maxH - barH - buttonH).coerceAtLeast(0)
        val bodyMin = when {
            fill -> room
            center -> (minH - barH - buttonH).coerceIn(0, room)
            else -> 0
        }
        val bodyP = bodyM.map { it.measure(Constraints(minWidth = w, maxWidth = w, minHeight = bodyMin, maxHeight = room)) }
        val bodyH = bodyP.maxOfOrNull { it.height } ?: 0
        val total = if (fill) maxH else (barH + bodyH + buttonH).coerceIn(minH, maxH)
        layout(w, total) {
            barP.forEach { it.place(0, 0) }
            bodyP.forEach { it.place(0, barH) }
            buttonP.forEach { it.place(0, total - buttonH) }
        }
    }
}

/** Back (a glass circle) at the leading edge, Skip and [trailing] (the language) at the trailing edge. */
@Composable
private fun TopBar(o: Onb, back: OnboardingStep?, skip: OnboardingStep?, trailing: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) CircleButton(rememberVectorPainter(OnbIcons.back), t("onb_back"), onClick = { o.go(back) })
        Spacer(Modifier.weight(1f))
        if (skip != null) TextAction(t("onb_skip"), onClick = { o.go(skip) })
        trailing?.invoke()
    }
}

// Small pieces: text, buttons, rows, the selection ring

/** The style of text, with the sky's soft shadow when it sits straight on the sky. */
@Composable
private fun onSky(style: TextStyle = TextStyle()): TextStyle = if (LocalOnSky.current) style.copy(shadow = OnbLook.textShadow) else style

/** Secondary text: Muted on glass, 80% ink on the sky. */
@Composable
internal fun onbSecondary(): Color = if (LocalOnSky.current) OnbLook.inkSoft else Muted

/** A small heading in the accent: upper case and tracked, except in right-to-left scripts (no letter spacing in Arabic). */
@Composable
internal fun OnbEyebrow(text: String, modifier: Modifier = Modifier, size: TextUnit = 12.sp, weight: FontWeight = FontWeight.SemiBold) {
    val rtl = Strings.isRtl
    Text(
        if (rtl) text else text.uppercase(Locale.forLanguageTag(Strings.language)), color = Gold, fontSize = size, fontWeight = weight,
        letterSpacing = if (rtl) 0.sp else 0.08.em, maxLines = 1, style = onSky(), modifier = modifier,
    )
}

@Composable
internal fun OnbTitle(
    text: String, modifier: Modifier = Modifier, large: Boolean = false, align: TextAlign = TextAlign.Start, maxLines: Int = Int.MAX_VALUE,
    /** A smaller size and line height (the privacy step set tighter), instead of the title's own. */
    size: Pair<TextUnit, TextUnit>? = null,
) {
    Text(
        text, color = Ink, fontSize = size?.first ?: if (large) 32.sp else 28.sp,
        lineHeight = size?.second ?: if (large) 38.sp else 34.sp, fontWeight = FontWeight.Bold,
        textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis, style = onSky(), modifier = modifier.semantics { heading() },
    )
}

@Composable
internal fun OnbBody(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, color = onbSecondary(), fontSize = 17.sp, lineHeight = 24.sp, textAlign = align, style = onSky(), modifier = modifier)
}

@Composable
internal fun OnbFoot(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, color: Color = onbSecondary()) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp, textAlign = align, style = onSky(), modifier = modifier)
}

internal fun Modifier.glass(shape: Shape, color: Color = OnbLook.glass): Modifier = background(color, shape).border(1.dp, OnbLook.line, shape)

/** Press feedback: down to 0.96 in 90 ms, back with the spring. Nothing moves with animations off. */
internal fun Modifier.pressScale(source: InteractionSource): Modifier = composed {
    val reduce = LocalOnbReduce.current
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed && !reduce) OnbMotion.PRESS_SCALE else 1f,
        if (pressed) tween(OnbMotion.PRESS_IN_MS, easing = FastOutSlowInEasing) else OnbMotion.spring(), label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

/** The keyboard focus: a 2 dp ring in the accent, 3 dp outside the control ([radius] null: a capsule). */
internal fun Modifier.focusRing(source: InteractionSource, radius: Dp? = null): Modifier = composed {
    val focused by source.collectIsFocusedAsState()
    val keyboard by LocalOnbKeyboard.current
    drawWithContent {
        drawContent()
        if (focused && keyboard) {
            val g = 3.dp.toPx()
            val r = radius?.toPx() ?: (size.height / 2)
            drawRoundRect(Gold, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g), CornerRadius(r + g), style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * A step's child entering: from 24 dp lower and transparent, with the spring, [index] × 40 ms after the step appears
 * (title, body, controls, button). A short fade only with animations off; already there in a still frame.
 */
internal fun Modifier.enter(index: Int): Modifier = composed {
    val reduce = LocalOnbReduce.current
    val still = LocalOnbStill.current
    val p = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (p.value >= 1f) return@LaunchedEffect
        if (reduce) p.animateTo(1f, tween(OnbMotion.FADE_MS))
        else {
            delay(index * OnboardingTiming.STAGGER.toLong())
            p.animateTo(1f, OnbMotion.spring())
        }
    }
    graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        if (!reduce) translationY = 24.dp.toPx() * (1 - p.value)
    }
}

/** A check or badge popping in: scale 0.2 → 1 and opacity 0 → 1 with the bouncy spring (a fade with animations off). */
internal fun Modifier.pop(): Modifier = composed {
    val reduce = LocalOnbReduce.current
    val still = LocalOnbStill.current
    val p = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { if (p.value < 1f) p.animateTo(1f, if (reduce) tween(OnbMotion.FADE_MS) else OnbMotion.bouncy()) }
    graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        if (!reduce) { val s = 0.2f + 0.8f * p.value; scaleX = s; scaleY = s }
    }
}

/**
 * The chosen row's 2 dp ring in the accent, and a pulse when it is chosen ([pulseKey] changes): a ring growing from
 * 2 to 18 dp outside it while it fades from 75% to nothing over 650 ms.
 */
internal fun Modifier.selectionRing(selected: Boolean, pulseKey: Int, radius: Dp): Modifier = composed {
    val reduce = LocalOnbReduce.current
    val pulse = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(pulseKey) }
    LaunchedEffect(pulseKey) {
        if (pulseKey == last) return@LaunchedEffect
        last = pulseKey
        if (!reduce) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
        }
    }
    drawWithContent {
        drawContent()
        val r = radius.toPx()
        fun ring(grow: Float, c: Color) = drawRoundRect(
            c, Offset(-grow, -grow), Size(size.width + 2 * grow, size.height + 2 * grow), CornerRadius(r + grow), style = Stroke(2.dp.toPx()),
        )
        if (selected) ring(1.dp.toPx(), Gold)
        val p = pulse.value
        if (p < 1f) ring((2f + 16f * p).dp.toPx(), Gold.copy(alpha = 0.75f * (1 - p)))
    }
}

/** The main button: the accent capsule, two lines at most. */
@Composable
internal fun OnbPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp).pressScale(source).focusRing(source)
            .background(if (hovered) lerp(Gold, Color.White, 0.1f) else Gold, OnbLook.capsule)
            .hoverable(source).clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** The second choice beside the main button: the same capsule in glass. */
@Composable
internal fun OnbGlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp).pressScale(source).focusRing(source)
            .glass(OnbLook.capsule, if (hovered) OnbLook.fillHover else OnbLook.fill)
            .hoverable(source).clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** A text button (Skip, the weather's credit). */
@Composable
private fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Ink, size: TextUnit = 16.sp) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier.heightIn(min = 44.dp).pressScale(source).focusRing(source)
            .background(if (hovered) OnbLook.fill else Color.Transparent, OnbLook.capsule)
            .hoverable(source).clickable(source, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = size, fontWeight = FontWeight.SemiBold, style = onSky())
    }
}

/** An icon-only glass circle ([size] across) with its label for screen readers. */
@Composable
private fun CircleButton(icon: Painter, label: String, onClick: () -> Unit, size: Dp = 44.dp, tint: Color = Ink, selected: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier.size(size).pressScale(source).focusRing(source)
            .background(if (selected) OnbLook.accentSoft else if (hovered) OnbLook.fillHover else OnbLook.fill, CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Gold else OnbLook.line, CircleShape)
            .hoverable(source).clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** The 40 dp accent circle at a row's start. */
@Composable
internal fun IconCircle(icon: Painter, size: Dp = 40.dp) {
    Box(Modifier.size(size).background(OnbLook.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(size * 0.55f))
    }
}

/** The check on a chosen card or row, popping in. */
@Composable
private fun CheckBadge(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Box(modifier.size(size).pop().background(Gold, CircleShape), contentAlignment = Alignment.Center) {
        Icon(rememberVectorPainter(OnbIcons.check), contentDescription = null, tint = Navy, modifier = Modifier.size(size * 0.68f))
    }
}

/**
 * A glass row (at least 74 tall): the icon circle, the title and its line, then a check when chosen or a chevron. It
 * brightens under the pointer, presses in, and pulses when chosen.
 */
@Composable
private fun OnbRow(
    icon: Painter, title: String, sub: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    pulseKey: Int = 0, role: Role = Role.Button, minHeight: Dp = 74.dp, chevron: Boolean = true, end: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).pressScale(source).selectionRing(selected, pulseKey, 16.dp).focusRing(source, 16.dp)
            .glass(OnbLook.card, if (hovered) OnbLook.fillHover else OnbLook.fill)
            .hoverable(source).selectable(selected, source, indication = null, role = role, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            sub?.let { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        end?.let { Spacer(Modifier.width(8.dp)); it() }
        if (selected) { Spacer(Modifier.width(8.dp)); CheckBadge() }
        else if (chevron && end == null) {
            Spacer(Modifier.width(8.dp))
            Icon(rememberVectorPainter(OnbIcons.chevron), contentDescription = null, tint = Muted, modifier = Modifier.size(22.dp))
        }
    }
}

/** A glass row the whole of which turns a switch on and off (Friday, Al-Kahf). */
@Composable
private fun SwitchRow(icon: Painter, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier.fillMaxWidth().heightIn(min = 74.dp).pressScale(source).focusRing(source, 16.dp)
            .glass(OnbLook.card, if (hovered) OnbLook.fillHover else OnbLook.fill)
            .hoverable(source).toggleable(checked, source, indication = null, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            sub?.let { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Navy))
    }
}

/** A glass card of the showcase and the day (radius 16, padding 16). */
@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(OnbLook.card, OnbLook.fill).padding(16.dp), content = content)
}

// The header

/** One answer in "YOUR DAY". [bump] changes when the same answer is chosen again (a small bounce). */
private data class DayChip(val key: String, val icon: Painter, val text: String, val bump: Int = 0)

/** A header slot: the chip once answered; before that a dashed pill [placeholder] wide (or nothing). */
private data class ChipSlot(val key: String, val chip: DayChip?, val placeholder: Dp?)

/** The adhan chip's words for each choice. */
private fun adhanChipKey(c: AdhanChoice): String = when (c) {
    AdhanChoice.MAKKAH -> "onb2_chip_makkah"
    AdhanChoice.MADINAH -> "onb2_chip_madinah"
    AdhanChoice.SHORT -> "onb2_adhan_short"
    AdhanChoice.BANNER_ONLY -> "onb2_chip_banner"
}

/** The header's slots, in order: the city and the method once a place is chosen, then each answer as it is given. */
@Composable
private fun chipSlots(o: Onb): List<ChipSlot> {
    val s = o.settings
    val chosen = o.chosen
    val clock = o.clock
    val place = chosen != null && clock != null
    return listOf(
        ChipSlot("city", if (place) DayChip("city", onbIcon("ic_pin"), chosen.displayName(o.lang)) else null, 64.dp),
        ChipSlot("method", if (place) DayChip("method", onbIcon("ic_onb_method"), t("method_short_" + clock.settings.method.name), o.state.methodBump) else null, 80.dp),
        ChipSlot(
            "asr",
            if (o.answered(OnboardingStep.ASR)) {
                DayChip("asr", onbIcon("ic_onb_asr"), t(if (s.madhab == AsrMadhab.HANAFI) "asr_hanafi" else "onb2_asr_standard"), o.state.asrBump)
            } else null,
            70.dp,
        ),
        ChipSlot(
            "adhan",
            if (o.answered(OnboardingStep.ADHAN)) DayChip("adhan", onbIcon("ic_onb_wave"), t(adhanChipKey(s.alerts().adhanChoice)), o.state.adhanBump) else null,
            76.dp,
        ),
        ChipSlot(
            "friday",
            if (o.answered(OnboardingStep.FRIDAY) && (s.friday || s.kahf)) DayChip("friday", onbIcon("ic_onb_friday"), t("onb2_chip_friday"), o.state.fridayBump) else null,
            null,
        ),
        ChipSlot(
            "weather",
            if (o.answered(OnboardingStep.WEATHER) && s.skyWeather) DayChip("weather", onbIcon("ic_wx_partly_cloudy_day"), t("onb2_chip_weather")) else null,
            null,
        ),
    )
}

/**
 * "YOUR DAY": a glass capsule with the sun's slot, the eyebrow and the answers as chips, which fly up into it as they
 * are given (from below, or from the panel's side when wide) and scroll sideways when they overflow. Read as one
 * phrase ("Your day: Riyadh, Umm al-Qura, …"), politely announced as it fills. Shown from the place to privacy; it
 * dissolves on the last step.
 */
@Composable
private fun DayHeader(o: Onb, modifier: Modifier = Modifier) {
    val reduce = o.reduce
    val visible = OnboardingFlow.showsHeader(o.step)
    val slots = chipSlots(o)
    val title = t("onb2_your_day")
    val chips = slots.mapNotNull { it.chip }
    val description = if (chips.isEmpty()) title else title + ": " + chips.joinToString(", ") { it.text }
    val shown by animateFloatAsState(if (visible) 1f else 0f, if (o.still) snap() else tween(450), label = "header")
    val height = if (o.wide) 50.dp else 46.dp
    var firstFrame by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { withFrameNanos { }; firstFrame = false }
    val state = o.state
    Box(
        modifier.fillMaxWidth().height(height)
            .graphicsLayer {
                alpha = shown
                if (!reduce) {
                    translationY = -14.dp.toPx() * (1 - shown)
                    val sc = 0.97f + 0.03f * shown
                    scaleX = sc; scaleY = sc
                }
            }
            .then(if (!reduce && shown < 1f && !visible) Modifier.blur((6 * (1 - shown)).dp) else Modifier)
            .clearAndSetSemantics { if (visible) { contentDescription = description; liveRegion = LiveRegionMode.Polite } },
    ) {
        // The glass is a layer of its own, so chips can fly in from outside the capsule.
        Box(Modifier.matchParentSize().glass(OnbLook.capsule))
        Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).onGloballyPositioned { c -> state.slot = c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f) - state.origin })
            Spacer(Modifier.width(8.dp))
            OnbEyebrow(title, Modifier.padding(end = 2.dp), size = 11.sp, weight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            ChipStrip(slots.indexOfLast { it.chip != null }, height, Modifier.weight(1f).fillMaxHeight()) {
                slots.forEach { slot -> key(slot.key) { ChipSlotView(slot, o.wide, fly = !firstFrame) } }
            }
        }
    }
}

@Composable
private fun ChipSlotView(slot: ChipSlot, wide: Boolean, fly: Boolean) {
    val reduce = LocalOnbReduce.current
    var last by remember { mutableStateOf(slot.chip) }
    if (slot.chip != null) last = slot.chip
    // A chip leaving (the Friday reminders both turned off) fades and shrinks to 0.8 in 200 ms.
    val gone by animateFloatAsState(if (slot.chip == null) 1f else 0f, if (reduce && LocalOnbStill.current) snap() else tween(200), label = "chip-out")
    Box(contentAlignment = Alignment.Center) {
        if (slot.chip == null && slot.placeholder != null && gone >= 1f) Placeholder(slot.placeholder, if (wide) 30.dp else 28.dp)
        val chip = slot.chip ?: last?.takeIf { gone < 1f }
        if (chip != null) {
            key(chip.key, slot.chip == null) {
                Box(Modifier.graphicsLayer { alpha = 1f - gone; val sc = 1f - 0.2f * gone; scaleX = sc; scaleY = sc }) {
                    Chip(chip, wide, fly && slot.chip != null)
                }
            }
        }
    }
}

@Composable
private fun Placeholder(width: Dp, height: Dp) {
    Canvas(Modifier.size(width, height)) {
        val w = 1.5.dp.toPx()
        drawRoundRect(
            OnbLook.placeholder, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(size.height / 2),
            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )
    }
}

/** A chip: it flies up from below (wide: also from the panel's side) with the bouncy spring, and bounces when chosen again. */
@Composable
private fun Chip(chip: DayChip, wide: Boolean, fly: Boolean) {
    val reduce = LocalOnbReduce.current
    val still = LocalOnbStill.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val p = remember { Animatable(if (fly && !still) 0f else 1f) }
    LaunchedEffect(Unit) { if (p.value < 1f) p.animateTo(1f, if (reduce) tween(OnbMotion.FADE_MS) else OnbMotion.bouncy()) }
    val bump = remember { Animatable(1f) }
    var lastBump by remember { mutableIntStateOf(chip.bump) }
    var lastText by remember { mutableStateOf(chip.text) }
    LaunchedEffect(chip.bump, chip.text) {
        if (chip.bump != lastBump || chip.text != lastText) {
            lastBump = chip.bump
            lastText = chip.text
            if (!reduce) { bump.snapTo(0.8f); bump.animateTo(1f, OnbMotion.bouncy()) }
        }
    }
    Row(
        Modifier
            .graphicsLayer {
                val v = p.value
                alpha = if (reduce) v.coerceIn(0f, 1f) else (v * 2.4f).coerceIn(0f, 1f)
                if (!reduce) {
                    translationY = (if (wide) 300.dp else 260.dp).toPx() * (1 - v)
                    if (wide) translationX = (if (rtl) -150.dp else 150.dp).toPx() * (1 - v)
                    val sc = (1.25f - 0.25f * v) * bump.value
                    scaleX = sc; scaleY = sc
                }
            }
            .height(if (wide) 30.dp else 28.dp).widthIn(max = 150.dp)
            .background(OnbLook.chip, OnbLook.capsule).border(1.dp, OnbLook.chipLine, OnbLook.capsule)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(chip.icon, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(chip.text, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The chips' row: scrolls sideways (no scroll bar), fades at an edge with more beyond it, and follows the newest chip
 * ([focus], a slot index). Its drawing layer reaches far above and below the capsule, so a chip can fly in from below.
 */
@Composable
private fun ChipStrip(focus: Int, height: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val jump = LocalOnbStill.current || LocalOnbReduce.current
    var offset by remember { mutableFloatStateOf(0f) }
    var max by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableIntStateOf(0) }
    var focusEnd by remember { mutableIntStateOf(0) }
    val state = rememberScrollableState { d ->
        val old = offset
        offset = (offset - d).coerceIn(0f, max.toFloat())
        old - offset
    }
    LaunchedEffect(focus, max) {
        withFrameNanos { }
        val target = if (focus < 0) 0f else (focusEnd - viewport).coerceIn(0, max).toFloat()
        if (jump) offset = target else animate(offset, target, animationSpec = OnbMotion.spring()) { v, _ -> offset = v }
    }
    val reach = 320.dp
    Box(modifier.scrollable(state, Orientation.Horizontal, reverseDirection = rtl), contentAlignment = Alignment.Center) {
        Layout(
            content = content,
            modifier = Modifier.fillMaxWidth().requiredHeight(height + reach * 2)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Soft edges where more chips are hidden (start and end follow the reading direction).
                    val fade = 14.dp.toPx() / size.width
                    val atStart = offset > 0.5f
                    val atEnd = offset < max - 0.5f
                    val left = if (rtl) atEnd else atStart
                    val right = if (rtl) atStart else atEnd
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = if (left) 0f else 1f), fade to Color.Black,
                            1 - fade to Color.Black, 1f to Color.Black.copy(alpha = if (right) 0f else 1f),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) { measurables, c ->
            val row = height.roundToPx()
            val gap = 6.dp.roundToPx()
            val start = 2.dp.roundToPx()
            val endPad = 12.dp.roundToPx()
            val placeables = measurables.map { it.measure(Constraints(maxHeight = row)) }
            val xs = IntArray(placeables.size)
            var x = start
            placeables.forEachIndexed { i, p ->
                xs[i] = x
                x += p.width + if (p.width > 0) gap else 0
            }
            val total = x - gap + endPad
            viewport = c.maxWidth
            max = (total - c.maxWidth).coerceAtLeast(0)
            focusEnd = if (focus in placeables.indices) xs[focus] + placeables[focus].width + endPad else 0
            layout(c.maxWidth, c.maxHeight) {
                val off = offset.coerceIn(0f, max.toFloat()).roundToInt()
                placeables.forEachIndexed { i, p -> p.placeRelative(xs[i] - off, (c.maxHeight - p.height) / 2) }
            }
        }
    }
}

// The hero: one sun (the moon at night) across every step

/** Where the sun is and how big, for the current step (pixels from the onboarding's top left). */
private data class HeroSpot(val center: Offset, val radius: Float, val halo: Boolean)

/** The day's schedules (yesterday, today, tomorrow) in the city's zone. */
private fun days(clock: Clock, now: Instant): List<DaySchedule> {
    val today = now.atZone(clock.zone).toLocalDate()
    return (-1L..1L).map { clock.day(today.plusDays(it)) }
}

/**
 * The sun's place: big on the promise (rising), in the header's slot through the questions and the showcase, riding
 * the arc on the day, and on the last step at its true place for now (in a wide window on the large arc).
 */
@Composable
private fun HeroLayer(o: Onb, size: Size) {
    val s = o.state
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun dp(v: Float) = with(density) { v.dp.toPx() }
    val paneX = if (!o.wide) size.width / 2 else if (rtl) size.width - size.width * 0.29f else size.width * 0.29f
    val clock = o.clock
    val nowArc = remember(clock, o.now.epochSecond / 60) { clock?.let { DayArc.at(o.now, days(it, o.now)) } }
    val spot = when (o.step) {
        OnboardingStep.PROMISE -> HeroSpot(Offset(paneX, size.height * (if (o.wide) 0.42f else 0.30f)), dp(46f), halo = true)
        OnboardingStep.DAY -> s.arcPoint?.let { HeroSpot(it, dp(17f), false) } ?: s.slot?.let { HeroSpot(it, dp(11f), false) }
        OnboardingStep.DONE -> {
            val arcSpot = if (o.wide) s.arcPoint else null
            if (arcSpot != null) HeroSpot(arcSpot, dp(46f), halo = true)
            else {
                // One column: its true place over the text (no ridge to hide behind on the desktop, so the path is higher).
                val f = (nowArc?.fraction ?: 0.5).toFloat()
                val w = size.width
                val h = size.height
                val x = dp(40f) + (w - dp(80f)) * (if (rtl) 1 - f else f)
                val y = 0.55f * h - 0.33f * h * sin(PI * f).toFloat()
                HeroSpot(Offset(x, y), dp(46f * 1.1f), halo = true)
            }
        }
        else -> s.slot?.let { HeroSpot(it, dp(11f), false) }
    } ?: return
    val moon = when (o.step) {
        OnboardingStep.DAY -> !s.arcIsDay
        OnboardingStep.PRIVACY, OnboardingStep.DONE -> nowArc?.isDay == false
        else -> skyPeriod(o).hasStars
    }
    // Always where it belongs in a still frame; else gliding with the soft spring (a fade out and in with animations off).
    val pos = remember { Animatable(spot.center, Offset.VectorConverter) }
    val radius = remember { Animatable(spot.radius) }
    val fade = remember { Animatable(1f) }
    val rise = remember { Animatable(if (o.still || o.step != OnboardingStep.PROMISE) 1f else 0f) }
    val dragging = o.step == OnboardingStep.DAY && s.scrub != null
    LaunchedEffect(spot.center, spot.radius) {
        when {
            o.still || dragging -> { pos.snapTo(spot.center); radius.snapTo(spot.radius) }
            o.reduce -> {
                if (pos.value != spot.center || radius.value != spot.radius) {
                    fade.animateTo(0f, tween(OnbMotion.FADE_MS / 2))
                    pos.snapTo(spot.center); radius.snapTo(spot.radius)
                    fade.animateTo(1f, tween(OnbMotion.FADE_MS / 2))
                }
            }
            else -> {
                launch { pos.animateTo(spot.center, OnbMotion.soft()) }
                launch { radius.animateTo(spot.radius, OnbMotion.soft()) }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (rise.value < 1f) {
            delay(250)
            rise.animateTo(1f, if (o.reduce) tween(OnbMotion.FADE_MS) else OnbMotion.soft())
        }
    }
    val big = spot.halo
    val breathe = if (big && !o.reduce) {
        val t = rememberInfiniteTransition(label = "halo")
        t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(2750, easing = LinearEasing), RepeatMode.Reverse), label = "halo").value
    } else 1f
    val glow = if (big) {
        val w = skyWeather(o)
        (0.55f + 0.45f * WeatherLook.of(w?.symbol, night = skyPeriod(o).hasStars).glowStrength.toFloat()).coerceIn(0f, 1f)
    } else 1f
    val center = if (o.still) spot.center else pos.value
    val r = if (o.still) spot.radius else radius.value
    val lift = if (o.reduce) 0f else dp(120f) * (1 - rise.value)
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        drawHero(center + Offset(0f, lift), r, moon, big, breathe, glow * fade.value * rise.value.coerceIn(0f, 1f))
    }
}

/**
 * The sun (or the moon) at [center]: the radial gold disc with its glow, or a crescent in moonlight. [halo] adds the
 * breathing light of the promise and the end ([haloScale]).
 */
internal fun DrawScope.drawHero(center: Offset, radius: Float, moon: Boolean, halo: Boolean, haloScale: Float, alpha: Float) {
    if (alpha <= 0f || radius <= 0f) return
    if (moon) {
        val c = OnbLook.moonlight
        if (halo) {
            val hr = radius * 1.75f * haloScale
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.26f * alpha), c.copy(alpha = 0f)), center, hr), hr, center)
        }
        val gr = radius * 1.35f
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f * alpha), c.copy(alpha = 0f)), center, gr), gr, center)
        val disc = Path().apply { addOval(Rect(center, radius)) }
        val cut = Path().apply { addOval(Rect(center + Offset(-radius * 0.46f, -radius * 0.15f), radius * 0.98f)) }
        val crescent = Path().apply { op(disc, cut, PathOperation.Difference) }
        rotate(-22f, center) { drawPath(crescent, c.copy(alpha = alpha)) }
    } else {
        if (halo) {
            val hr = radius * 1.75f * haloScale
            drawCircle(Brush.radialGradient(listOf(OnbLook.sunMid.copy(alpha = 0.38f * alpha), OnbLook.sunMid.copy(alpha = 0f)), center, hr), hr, center)
        }
        val gr = radius * 1.65f
        drawCircle(Brush.radialGradient(listOf(OnbLook.sunMid.copy(alpha = 0.45f * alpha), OnbLook.sunMid.copy(alpha = 0f)), center, gr), gr, center)
        drawCircle(
            Brush.radialGradient(
                0f to OnbLook.sunCore.copy(alpha = alpha), 0.58f to OnbLook.sunMid.copy(alpha = alpha), 1f to OnbLook.sunEdge.copy(alpha = alpha),
                center = center + Offset(0f, -radius * 0.08f), radius = radius,
            ),
            radius, center,
        )
    }
}

/**
 * A mosque on the horizon: a large dome, two side domes, two minarets (the Android app's drawSkyline, which the
 * desktop does not compile). [origin] is its top left, [sz] its size; never mirrored.
 */
internal fun DrawScope.drawMosqueSkyline(origin: Offset, sz: Size, color: Color) {
    fun p(x: Float, y: Float) = Offset(origin.x + x * sz.width, origin.y + y * sz.height)
    val path = Path()
    fun rect(x: Float, y: Float, w: Float, h: Float) {
        val a = p(x, y)
        path.addRect(Rect(a.x, a.y, a.x + w * sz.width, a.y + h * sz.height))
    }
    rect(0.16f, 0.66f, 0.68f, 0.34f)
    fun dome(cx: Float, hw: Float, base: Float, top: Float) {
        val s0 = p(cx - hw, base)
        path.moveTo(s0.x, s0.y)
        val c1 = p(cx - hw * 1.05f, base - (base - top) * 0.55f)
        val c2 = p(cx - hw * 0.45f, top + (base - top) * 0.2f)
        val e1 = p(cx, top)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        val c3 = p(cx + hw * 0.45f, top + (base - top) * 0.2f)
        val c4 = p(cx + hw * 1.05f, base - (base - top) * 0.55f)
        val e2 = p(cx + hw, base)
        path.cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        path.close()
        rect(cx - 0.004f, top - 0.08f, 0.008f, 0.09f)
    }
    dome(0.5f, 0.17f, 0.68f, 0.2f)
    dome(0.27f, 0.08f, 0.7f, 0.46f)
    dome(0.73f, 0.08f, 0.7f, 0.46f)
    for (x in listOf(0.06f, 0.9f)) {
        rect(x, 0.12f, 0.04f, 0.88f)
        rect(x - 0.008f, 0.34f, 0.056f, 0.03f)
        val a = p(x, 0.12f)
        val b = p(x + 0.02f, 0f)
        val c = p(x + 0.04f, 0.12f)
        path.moveTo(a.x, a.y); path.lineTo(b.x, b.y); path.lineTo(c.x, c.y); path.close()
    }
    drawPath(path, color)
}

// The steps

/** One promise, one button; the language at the top. */
@Composable
private fun PromiseStep(o: Onb) {
    StepFrame(o, hero = true, trailing = { LanguageButton(o) }) {
        OnbEyebrow(t("app_name"), Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        OnbTitle(t("onb2_promise_title"), Modifier.enter(1), large = true)
        Spacer(Modifier.height(10.dp))
        OnbBody(t("onb2_promise_body_device"), Modifier.enter(2))
        Spacer(Modifier.height(24.dp))
        OnbPrimaryButton(t("onb2_begin"), onClick = { o.go(OnboardingStep.PLACE) }, modifier = Modifier.enter(3))
        Spacer(Modifier.height(10.dp))
        OnbFoot(t("onb2_promise_foot"), Modifier.enter(4))
    }
}

/** A glass capsule with the globe and the language's name; the app's 36 languages in a menu. */
@Composable
private fun LanguageButton(o: Onb) {
    var open by remember { mutableStateOf(false) }
    val code = o.settings.language
    val name = Strings.languages.firstOrNull { it.code == code }?.name ?: code
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val label = t("language") + ": " + name
    Box {
        Row(
            Modifier.height(38.dp).pressScale(source).focusRing(source)
                .glass(OnbLook.capsule, if (hovered) OnbLook.fillHover else OnbLook.fill)
                .hoverable(source).clickable(source, indication = null, role = Role.Button) { open = true }
                .clearAndSetSemantics { contentDescription = label }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(onbIcon("ic_globe"), contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Strings.languages.forEach { l ->
                DropdownMenuItem(
                    text = { Text(l.name, color = if (l.code == code) Gold else Color.Unspecified) },
                    onClick = { open = false; if (l.code != code) o.update(o.settings.copy(language = l.code)) },
                )
            }
        }
    }
}

/**
 * Where do you pray: the time zone's largest city offered first, then the city search (no location permission on a
 * PC). Picking one puts the city and the method in the header and moves on; a replay with a place set has Continue.
 */
@Composable
private fun PlaceStep(o: Onb) {
    // Taken once as the step opens: before a choice the app's city is the time zone's (Main.kt), after one ask the table.
    val zoneCity = remember { if (o.settings.cityId == null) o.city else Cities.guess() }
    var pickedHere by remember { mutableStateOf(false) }
    var pulse by remember { mutableIntStateOf(0) }
    fun pick(c: City) {
        o.pickCity(c)
        pickedHere = true
        pulse++
        o.answer(OnboardingStep.PLACE)
        o.advance(OnboardingStep.PLACE, OnboardingStep.ASR)
    }
    val chosen = o.chosen
    val replay = chosen != null && !pickedHere && o.step == OnboardingStep.PLACE
    StepFrame(o, scroll = false, button = if (replay) ({ OnbPrimaryButton(t("onb2_continue"), onClick = { o.go(OnboardingStep.ASR) }) }) else null) {
        OnbTitle(t("onb2_where_title"), Modifier.enter(0))
        Spacer(Modifier.height(10.dp))
        OnbBody(t("onb2_where_body_device"), Modifier.enter(1))
        Spacer(Modifier.height(20.dp))
        if (zoneCity != null) {
            val on = chosen?.id == zoneCity.id
            val name = zoneCity.displayName(o.lang)
            OnbRow(
                onbIcon("ic_pin"), name, if (on) t("onb_location_set", name) else t("onb2_where_zone_sub"), on,
                onClick = { pick(zoneCity) }, modifier = Modifier.enter(2), pulseKey = if (on) pulse else 0,
            )
            Spacer(Modifier.height(12.dp))
        }
        CityPicker(onPick = ::pick, modifier = Modifier.fillMaxWidth().weight(1f).enter(3), showTitle = false, selected = chosen?.id)
    }
}

/** The Asr times for each school today, and the method line. */
@Composable
private fun AsrStep(o: Onb) {
    val clock = o.clock ?: return
    val s = o.state
    val calc = remember { PrayerCalculator() }
    val today = o.now.atZone(clock.zone).toLocalDate()
    val times = remember(clock.settings, today) { calc.asrTimes(clock.city.location, today, clock.zone, clock.settings) }
    val method = clock.settings.method
    val line = if (o.settings.method == null) t("onb2_method_line_city", t("method_" + method.name), clock.city.displayName(o.lang))
    else t("onb2_method_line", t("method_" + method.name))
    var pulse by remember { mutableIntStateOf(0) }
    fun pick(m: AsrMadhab) {
        if (o.answered(OnboardingStep.ASR) && m == o.settings.madhab) s.asrBump++
        o.update(o.settings.copy(madhab = m))
        pulse++
        o.answer(OnboardingStep.ASR)
        o.advance(OnboardingStep.ASR, OnboardingStep.ADHAN)
    }
    StepFrame(o) {
        OnbTitle(t("onb2_asr_title"), Modifier.enter(0))
        Spacer(Modifier.height(10.dp))
        OnbBody(t("onb2_asr_body"), Modifier.enter(1))
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.enter(2).fillMaxWidth().glass(RoundedCornerShape(16.dp), OnbLook.fill).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(line, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            TextAction(t("change"), onClick = { s.sheet = true }, color = Gold, size = 14.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.enter(3).fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AsrMadhab.SHAFI, AsrMadhab.HANAFI).forEach { m ->
                val hanafi = m == AsrMadhab.HANAFI
                AsrCard(
                    title = t(if (hanafi) "asr_hanafi" else "onb2_asr_standard"),
                    sub = t(if (hanafi) "onb2_asr_hanafi_sub" else "onb2_asr_standard_sub"),
                    time = t("onb2_asr_at", times[m]?.let { clockText(it, clock.zone) } ?: "—"),
                    hanafi = hanafi, selected = o.settings.madhab == m, pulseKey = if (o.settings.madhab == m) pulse else 0,
                    onClick = { pick(m) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/** One school: the stick, its shadow drawn to scale (once or twice its length), the sun, and today's Asr. */
@Composable
private fun AsrCard(title: String, sub: String, time: String, hanafi: Boolean, selected: Boolean, pulseKey: Int, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier.heightIn(min = 248.dp).pressScale(source).selectionRing(selected, pulseKey, 16.dp).focusRing(source, 16.dp)
            .glass(OnbLook.card, if (hovered) OnbLook.fillHover else OnbLook.fill)
            .hoverable(source).selectable(selected, source, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            AsrArt(hanafi, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(title, color = Ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(sub, color = Muted, fontSize = 13.sp, lineHeight = 17.sp)
            Spacer(Modifier.weight(1f).heightIn(min = 10.dp))
            Text(time, color = Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
        }
        // Top right in both directions: the picture is never mirrored, so its sun stays at the top left.
        if (selected) CheckBadge(Modifier.align(AbsoluteAlignment.TopRight))
    }
}

/**
 * The shadow picture (viewBox 150 × 96, never mirrored): ground, stick, the sun at the school's elevation and the
 * shadow growing in with the soft spring, the ray from the sun fading in after it.
 */
@Composable
private fun AsrArt(hanafi: Boolean, modifier: Modifier) {
    val reduce = LocalOnbReduce.current
    val still = LocalOnbStill.current
    val grow = remember { Animatable(if (still) 1f else 0f) }
    val ray = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (grow.value >= 1f) return@LaunchedEffect
        launch { delay(260); grow.animateTo(1f, if (reduce) tween(OnbMotion.FADE_MS) else OnbMotion.soft()) }
        launch { delay(700); ray.animateTo(1f, tween(400)) }
    }
    Canvas(modifier.aspectRatio(150f / 96f).clearAndSetSemantics { }) {
        val k = size.width / 150f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        val sun = if (hanafi) p(14f, 28f) else p(20f, 16f)
        val end = if (hanafi) 122f else 86f
        drawLine(Ink.copy(alpha = 0.35f), p(6f, 82f), p(144f, 82f), 1.5f * k, cap = StrokeCap.Round)
        drawLine(
            Gold.copy(alpha = 0.7f * ray.value), sun, p(end, 82f), 1.2f * k,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * k, 3f * k)),
        )
        val g = grow.value
        val shadowEnd = if (reduce) end else 50f + (end - 50f) * g
        drawLine(Ink.copy(alpha = 0.55f * (if (reduce) g else 1f)), p(50f, 82f), p(shadowEnd, 82f), 4f * k, cap = StrokeCap.Round)
        drawLine(Ink, p(50f, 82f), p(50f, 46f), 3f * k, cap = StrokeCap.Round)
        val r = 8f * k
        drawCircle(Brush.radialGradient(listOf(OnbLook.sunMid.copy(alpha = 0.45f), OnbLook.sunMid.copy(alpha = 0f)), sun, r * 2f), r * 2f, sun)
        drawCircle(Brush.radialGradient(0f to OnbLook.sunCore, 0.6f to OnbLook.sunMid, 1f to OnbLook.sunEdge, center = sun, radius = r), r, sun)
    }
}

/** The 11 methods with their full names, the current one ticked; picking one saves it (no moving on). */
@Composable
private fun MethodSheet(o: Onb) {
    val clock = o.clock ?: return
    val s = o.state
    val current = clock.settings.method
    val dismiss = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(dismiss, indication = null) { s.sheet = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 620.dp)
                .glass(OnbLook.panel, Card.copy(alpha = 0.97f)).pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
        ) {
            OnbTitle(t("calculation"))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Method.entries.forEach { m ->
                    val on = m == current
                    val source = remember { MutableInteractionSource() }
                    val hovered by source.collectIsHoveredAsState()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRing(source, 12.dp)
                            .background(if (hovered) OnbLook.fillHover else OnbLook.fill, RoundedCornerShape(12.dp))
                            .hoverable(source)
                            .selectable(on, source, indication = null, role = Role.RadioButton) {
                                if (m != current) { o.update(o.settings.copy(method = m)); s.methodBump++ }
                                s.sheet = false
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("method_" + m.name), color = if (on) Gold else Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (on) Icon(rememberVectorPainter(OnbIcons.check), contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OnbGlassButton(t("done"), onClick = { s.sheet = false })
        }
    }
}

/**
 * How we call you to prayer: Makkah, Madinah, the short Adhan, or a banner only, each (but the banner) with its
 * recording to hear. A choice is saved at once; Continue keeps the one shown, which is the current setting.
 */
@Composable
private fun AdhanStep(o: Onb) {
    val s = o.state
    val choice = o.settings.alerts().adhanChoice
    var pulse by remember { mutableIntStateOf(0) }
    val place = if (o.settings.adhanSource == AdhanSource.MADINAH) "madinah" else "makkah"
    val rows = listOf(
        Triple(AdhanChoice.MAKKAH, "onb2_adhan_makkah" to null, "adhan_makkah"),
        Triple(AdhanChoice.MADINAH, "onb2_adhan_madinah" to null, "adhan_madinah"),
        Triple(AdhanChoice.SHORT, "onb2_adhan_short" to "onb2_adhan_short_sub", "adhan_${place}_short"),
        Triple(AdhanChoice.BANNER_ONLY, "onb2_adhan_banner" to null, null),
    )
    val previews = rows.mapNotNull { it.third }.toSet()
    // A recording still playing stops when the step is left.
    DisposableEffect(Unit) { onDispose { if (Sound.playing in previews) Sound.stop() } }
    fun pick(c: AdhanChoice) {
        if (o.answered(OnboardingStep.ADHAN) && c == choice) s.adhanBump++
        o.update(o.settings.withAdhanChoice(c))
        pulse++
        o.answer(OnboardingStep.ADHAN)
    }
    StepFrame(o, button = {
        OnbPrimaryButton(t("onb2_continue"), onClick = {
            val kept = o.settings.keepingShownAdhan(choice)
            if (kept != o.settings) o.update(kept)
            o.answer(OnboardingStep.ADHAN)
            o.go(o.next())
        })
    }) {
        OnbTitle(t("onb2_adhan_title"), Modifier.enter(0))
        Spacer(Modifier.height(10.dp))
        OnbBody(t("onb2_adhan_body"), Modifier.enter(1))
        Spacer(Modifier.height(20.dp))
        Column(Modifier.enter(2).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (c, keys, file) ->
                val label = t(keys.first)
                val playing = file != null && Sound.playing == file
                OnbRow(
                    rememberWave(c == AdhanChoice.BANNER_ONLY, playing), label, keys.second?.let { t(it) }, selected = c == choice,
                    onClick = { pick(c) }, pulseKey = if (c == choice) pulse else 0, role = Role.RadioButton, minHeight = 58.dp, chevron = false,
                    end = file?.let { f ->
                        {
                            CircleButton(
                                rememberVectorPainter(if (playing) OnbIcons.stop else OnbIcons.play),
                                if (playing) t("alarm_stop") else t("preview_play", label),
                                onClick = { if (playing) Sound.stop() else Sound.play(f) }, tint = Gold,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** The five-bar waveform at an Adhan row's start: moving while its recording plays, flat for the banner. */
@Composable
private fun rememberWave(flat: Boolean, playing: Boolean): Painter {
    val reduce = LocalOnbReduce.current
    val phase = if (playing && !reduce) {
        rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "wave").value
    } else 0f
    return remember(flat, phase) { WavePainter(flat, phase) }
}

private class WavePainter(private val flat: Boolean, private val phase: Float) : Painter() {
    override val intrinsicSize: Size get() = Size(24f, 24f)
    override fun DrawScope.onDraw() {
        val heights = if (flat) listOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f) else listOf(0.35f, 0.7f, 1f, 0.6f, 0.3f)
        val bar = size.width / 9f
        heights.forEachIndexed { i, h0 ->
            val h = if (phase > 0f) (h0 * (0.55f + 0.45f * abs(sin(2 * PI * (phase + i * 0.17f)).toFloat()))) else h0
            val x = bar * (1 + i * 1.75f) + bar / 2
            val len = size.height * 0.8f * h
            drawLine(Color.White, Offset(x, size.height / 2 - len / 2), Offset(x, size.height / 2 + len / 2), bar, cap = StrokeCap.Round)
        }
    }
}

/**
 * Get ready for Jumu'ah: the Friday reminder (45 minutes before Dhuhr) and Surat Al-Kahf, the desktop's own switches.
 * One column shows the midday picture with the mosque; a wide window draws the mosque under the leading pane's sky.
 */
@Composable
private fun FridayStep(o: Onb) {
    val clock = o.clock ?: return
    val s = o.state
    val calc = remember { PrayerCalculator() }
    fun set(friday: Boolean = o.settings.friday, kahf: Boolean = o.settings.kahf) {
        val wasOn = o.settings.friday || o.settings.kahf
        o.update(o.settings.copy(friday = friday, kahf = kahf))
        if (wasOn && o.answered(OnboardingStep.FRIDAY) && friday && kahf) s.fridayBump++
        o.answer(OnboardingStep.FRIDAY)
    }
    StepFrame(o, button = { OnbPrimaryButton(t("onb2_continue"), onClick = { o.answer(OnboardingStep.FRIDAY); o.go(o.next()) }) }) {
        if (!o.wide) {
            FridayArt(Modifier.enter(0))
            Spacer(Modifier.height(16.dp))
        }
        OnbTitle(t("onb2_friday_title"), Modifier.enter(0))
        Spacer(Modifier.height(10.dp))
        OnbBody(t("onb2_friday_body"), Modifier.enter(1))
        Spacer(Modifier.height(20.dp))
        Column(Modifier.enter(2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow(onbIcon("ic_onb_friday"), t("alerts_friday"), t("alerts_friday_note"), o.settings.friday, onChange = { set(friday = it) })
            SwitchRow(onbIcon("ic_book"), t("alerts_kahf"), t("alerts_kahf_note"), o.settings.kahf, onChange = { set(kahf = it) })
        }
        if (o.settings.friday) {
            val next = remember(clock.settings, o.now.epochSecond / 60) { FridayPreview.next(clock.city.location, clock.zone, o.now, clock.settings, calc) }
            if (next != null) {
                Spacer(Modifier.height(12.dp))
                OnbFoot(t("onb2_friday_next", clockText(next.reminder, clock.zone), clockText(next.dhuhr, clock.zone)), Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

/** The Friday picture: the midday sky, a soft glow high up and the mosque rising into it. Decorative, never mirrored. */
@Composable
private fun FridayArt(modifier: Modifier = Modifier) {
    val reduce = LocalOnbReduce.current
    val still = LocalOnbStill.current
    val rise = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (rise.value >= 1f) return@LaunchedEffect
        delay(120)
        rise.animateTo(1f, if (reduce) tween(OnbMotion.FADE_MS) else OnbMotion.soft())
    }
    val sky = SkyColors.of(SkyPeriod.DAY)
    Canvas(modifier.fillMaxWidth().height(132.dp).clip(OnbLook.card).clearAndSetSemantics { }) {
        drawRect(Brush.verticalGradient(listOf(sky.top, sky.mid, sky.low)))
        val g = Offset(size.width * 0.5f, size.height * 0.18f)
        val gr = size.width * 0.45f
        drawCircle(Brush.radialGradient(listOf(sky.glow.copy(alpha = 0.75f), sky.glow.copy(alpha = 0f)), g, gr), gr, g)
        val w = size.width * 0.62f
        val h = w * 0.24f
        val dy = if (reduce) 0f else 12.dp.toPx() * (1 - rise.value)
        val color = lerp(sky.low, Color.Black, 0.35f)
        drawMosqueSkyline(Offset((size.width - w) / 2, size.height - h + dy), Size(w, h), color.copy(alpha = rise.value.coerceIn(0f, 1f)))
    }
}

/**
 * Sky follows the weather, the new feature: the whole sky is the live preview (samples, labelled Preview, nothing
 * sent for), changing condition until one is picked. Turn on and Not now are equal choices; it stays off until turned on.
 */
@Composable
private fun WeatherStep(o: Onb) {
    val s = o.state
    val night = skyPeriod(o).hasStars
    // Not now changes nothing: off for someone new, still on for someone seeing the tour again.
    fun finish(on: Boolean) {
        o.update(o.settings.copy(skyWeather = on || o.settings.skyWeather, weatherOffered = true))
        o.answer(OnboardingStep.WEATHER)
        if (on) o.advance(OnboardingStep.WEATHER, o.next()) else o.go(o.next())
    }
    StepFrame(o, button = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OnbPrimaryButton(t("weather_offer_turn_on"), onClick = { finish(true) }, modifier = Modifier.weight(1f))
            OnbGlassButton(t("weather_offer_not_now"), onClick = { finish(false) }, modifier = Modifier.weight(1f))
        }
    }) {
        Text(
            t("onb2_new"), color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.enter(0).background(Gold, OnbLook.capsule).padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Spacer(Modifier.height(10.dp))
        OnbTitle(t("sky_weather"), Modifier.enter(0))
        Spacer(Modifier.height(10.dp))
        OnbBody(t("onb_weather_body"), Modifier.enter(1))
        Spacer(Modifier.height(14.dp))
        val group = t("onb2_weather_conditions")
        Row(
            Modifier.enter(2).fillMaxWidth().selectableGroup().semantics { contentDescription = group },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            WeatherPreview.order.forEach { c ->
                CircleButton(
                    weatherIcon(c, night), t(c.stringKey),
                    onClick = { s.preview = c; s.previewPicked = true }, size = 40.dp, tint = if (c == s.preview) Gold else Ink, selected = c == s.preview,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val sample = WeatherPreview.now(s.preview, o.now)
        Row(Modifier.enter(3).fillMaxWidth().glass(OnbLook.card, OnbLook.fill).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(weatherIcon(s.preview, night), contentDescription = null, tint = Gold, modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                OnbEyebrow(t("onb2_preview"))
                Text(t(s.preview.stringKey), color = Ink, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(weatherLine(sample), color = Muted, fontSize = 15.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            }
        }
        Spacer(Modifier.height(8.dp))
        // MET Norway's credit (CC BY 4.0), linked.
        val credit = remember { MutableInteractionSource() }
        Text(
            t("sky_weather_attribution"), color = Muted, fontSize = 12.sp, lineHeight = 16.sp, textDecoration = TextDecoration.Underline,
            modifier = Modifier.enter(3).focusRing(credit, 4.dp).hoverable(credit)
                .clickable(credit, indication = null, role = Role.Button) { Platform.open(sa.zood.nearmosque.core.Weather.ATTRIBUTION_URL) }
                .padding(vertical = 4.dp),
        )
    }
}

/** Building your prayer day: three lines tick to a check, over a pulsing outline of the day; then the day. */
@Composable
private fun BuildingStep(o: Onb) {
    val clock = o.clock ?: return
    val still = o.still
    var ticks by remember { mutableIntStateOf(if (still) 3 else 0) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        launch { repeat(3) { delay(OnboardingTiming.BUILDING_LINE.toLong()); ticks++ } }
        delay(OnboardingTiming.BUILDING.toLong())
        if (o.state.step == OnboardingStep.BUILDING) o.go(OnboardingStep.DAY)
    }
    // The outline pulses (still with animations off).
    val pulse = if (still || o.reduce) 0.6f else {
        rememberInfiniteTransition(label = "skeleton")
            .animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse), label = "skeleton").value
    }
    val lines = listOf(
        t("onb2_building_method", t("method_short_" + clock.settings.method.name)),
        t("onb2_building_qibla", clock.city.displayName(o.lang)),
        t("onb2_building_sun"),
    )
    StepFrame(o, hero = true) {
        Box(Modifier.fillMaxWidth()) {
            // The outline of what is coming: the arc and three cards (decorative).
            Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
                val c = Color.White.copy(alpha = 0.08f * pulse / 0.6f)
                val w = size.width
                val r = w * 0.42f
                drawArc(c, 200f, 140f, false, Offset(w / 2 - r, 6.dp.toPx()), Size(2 * r, 2 * r), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                val cw = (w - 16.dp.toPx()) / 3
                repeat(3) { i ->
                    drawRoundRect(c, Offset(i * (cw + 8.dp.toPx()), size.height - 56.dp.toPx()), Size(cw, 56.dp.toPx()), CornerRadius(14.dp.toPx()))
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 80.dp)) {
                OnbTitle(t("onb2_building"), Modifier.enter(0))
                Spacer(Modifier.height(18.dp))
                val announced = lines.take(ticks).joinToString(". ")
                Column(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite; if (announced.isNotEmpty()) contentDescription = announced },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    lines.forEachIndexed { i, line ->
                        Row(Modifier.enter(1 + i), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                if (i < ticks) CheckBadge(size = 22.dp)
                                else Box(Modifier.size(10.dp).background(OnbLook.track, CircleShape))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(line, color = if (i < ticks) Ink else Muted, fontSize = 15.sp, lineHeight = 20.sp, style = onSky())
                        }
                    }
                }
            }
        }
    }
}

/**
 * The arc: a circular arc with a 70° half-angle and a chord of 0.86 of the width, [top] below the box's top. [point]
 * follows the reading direction (right to left in RTL); the landscape around it is never mirrored.
 */
private class ArcGeometry(width: Float, top: Float) {
    private val half = Math.toRadians(70.0)
    val chord = 0.86f * width
    val r = (chord / 2 / sin(half)).toFloat()
    val sag = (r * (1 - cos(half))).toFloat()
    val cx = width / 2
    val baseY = top + sag
    private val cy = (baseY + r * cos(half)).toFloat()

    fun point(t: Float, rtl: Boolean): Offset {
        val f = if (rtl) 1 - t else t
        val th = Math.toRadians(-70.0 + 140.0 * f)
        return Offset((cx + r * sin(th)).toFloat(), (cy - r * cos(th)).toFloat())
    }

    /** [distance] from the arc at [t] towards its centre (negative: outside the arc). */
    fun inside(t: Float, rtl: Boolean, distance: Float): Offset {
        val p = point(t, rtl)
        val d = Offset(cx, cy) - p
        return p + d / d.getDistance() * distance
    }
}

/** The prayer begun last at [t] (Isha before Fajr) and the sky's period then. */
private fun periodAt(t: Instant, days: List<DaySchedule>): Pair<PrayerEvent?, SkyPeriod> {
    val i = days.indexOfFirst { it.date == t.atZone(it.zone).toLocalDate() }
    if (i < 0) return null to SkyPeriod.NIGHT
    val d = days[i]
    val current = PrayerEvent.entries.lastOrNull { e -> d[e]?.let { !it.isAfter(t) } == true }
        ?: return (if (i > 0 && days[i - 1][PrayerEvent.ISHA] != null) PrayerEvent.ISHA else null) to SkyPeriod.NIGHT
    return current to SkyPeriod.at(t, d[PrayerEvent.SUNRISE], current)
}

/**
 * The arc of the day (or the night) with the prayers on it; [interactive] (the day step) adds the hint and the
 * Day | Night control. The sun (HeroLayer) rides it: drag it or use the arrow keys to walk through the day; the sky
 * follows, and on release the sun springs back to now. Past an end the day turns to night and back.
 */
@Composable
private fun DayArcView(o: Onb, clock: Clock, interactive: Boolean, modifier: Modifier = Modifier, disc: Dp = 17.dp) {
    val s = o.state
    val now = o.now
    val days = remember(clock, now.atZone(clock.zone).toLocalDate()) { days(clock, now) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentHalf = if (DayArc.at(now, days)?.isDay == false) DayArc.Half.NIGHT else DayArc.Half.DAY
    val half = if (interactive) s.half ?: currentHalf else currentHalf
    val arc = DayArc.at(now, days, half)
    DisposableEffect(Unit) { onDispose { s.arcPoint = null; s.scrubPeriod = null } }
    SideEffect { s.arcIsDay = arc?.isDay ?: (half == DayArc.Half.DAY) }
    if (arc == null) {
        // Polar day or night: no arc to walk along; the times show what exists.
        SideEffect { s.arcPoint = null }
        return
    }
    val scrub = if (interactive) s.scrub else null
    val t = (scrub ?: arc.fraction.toFloat()).coerceIn(0f, 1f)
    val at = arc.start.plus(Duration.ofMillis((Duration.between(arc.start, arc.end).toMillis() * t).toLong()))
    val (event, period) = remember(at.epochSecond / 30, days) { periodAt(at, days) }
    SideEffect { s.scrubPeriod = if (scrub != null) period else null }
    val bubble = (event?.let { t(prayerKey(it)) } ?: t(if (arc.isDay) "onb2_day" else "onb2_night")) + " · " + clockText(at, clock.zone)
    val currentHalfState by rememberUpdatedState(currentHalf)
    fun other(h: DayArc.Half) = if (h == DayArc.Half.DAY) DayArc.Half.NIGHT else DayArc.Half.DAY
    fun moveBy(d: Float) {
        val h = s.half ?: currentHalfState
        var v = (s.scrub ?: arc.fraction.toFloat()) + d
        if (v > 1.06f) { s.half = other(h); v = 0f } else if (v < -0.06f) { s.half = other(h); v = 1f }
        s.scrub = v
    }
    fun release() { s.scrub = null; s.half = null }
    val label = t("onb2_time_of_day")
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = with(density) { maxWidth.toPx() }
            val g = ArcGeometry(w, with(density) { 30.dp.toPx() })
            val height = with(density) { (g.baseY + 46.dp.toPx()).toDp() }
            var origin by remember { mutableStateOf<Offset?>(null) }
            val here = g.point(t, rtl)
            // Read here (not only in the effect), so the box's first layout puts the sun on the arc.
            val at0 = origin
            SideEffect { s.arcPoint = at0?.let { it + here - s.origin } }
            val interaction = if (interactive) Modifier
                .pointerInput(g.chord, rtl) {
                    detectDragGestures(
                        onDragStart = { if (s.scrub == null) s.scrub = arc.fraction.toFloat() },
                        onDragEnd = { release() }, onDragCancel = { release() },
                    ) { change, amount ->
                        change.consume()
                        moveBy(amount.x / g.chord * (if (rtl) -1 else 1))
                    }
                }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = when (e.key) { Key.DirectionRight -> 0.02f; Key.DirectionLeft -> -0.02f; else -> return@onKeyEvent false }
                    moveBy(if (rtl) -step else step)
                    true
                }
                .focusable()
                .semantics {
                    contentDescription = label
                    stateDescription = bubble
                    progressBarRangeInfo = ProgressBarRangeInfo(t, 0f..1f)
                    setProgress { v -> s.scrub = v.coerceIn(0f, 1f); true }
                }
            else Modifier.clearAndSetSemantics { }
            Box(Modifier.fillMaxWidth().height(height).onGloballyPositioned { origin = it.positionInRoot() }.then(interaction)) {
                Canvas(Modifier.fillMaxSize()) {
                    val n = 80
                    val full = Path().apply { for (i in 0..n) g.point(i / n.toFloat(), rtl).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                    val dots = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx()))
                    drawPath(full, OnbLook.track, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots))
                    drawLine(OnbLook.track, g.point(0f, rtl), g.point(1f, rtl), 1.dp.toPx())
                    if (t > 0f) {
                        val k = maxOf(2, (n * t).toInt())
                        val done = Path().apply { for (i in 0..k) g.point(t * i / k, rtl).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                        drawPath(done, Gold, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    arc.marks.forEach { m ->
                        val p = g.point(m.fraction.toFloat(), rtl)
                        drawCircle(Color.Black.copy(alpha = 0.16f), 7.5.dp.toPx(), p)
                        drawCircle(if (m.fraction <= t) Gold else Ink, 4.5.dp.toPx(), p)
                    }
                }
                val gap = with(density) { 20.dp.toPx() }
                val discPx = with(density) { disc.toPx() }
                arc.marks.forEach { m ->
                    // Inside the arc, clear of its line; outside (past the disc) where the sun or the moon covers the mark.
                    val f = m.fraction.toFloat()
                    val covered = abs(f - t) * g.r * Math.toRadians(140.0).toFloat() < discPx + gap
                    ArcText(g.inside(f, rtl, if (covered) -(discPx + gap * 0.9f) else gap)) {
                        Text(t(prayerKey(m.event)), color = OnbLook.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = onSky())
                    }
                }
                listOf(Triple(0f, arc.startEvent, arc.start), Triple(1f, arc.endEvent, arc.end)).forEach { (f, e, time) ->
                    val p = g.point(f, rtl)
                    ArcText(Offset(p.x, p.y + with(density) { 22.dp.toPx() })) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t(prayerKey(e)), color = OnbLook.inkSoft, fontSize = 12.sp, maxLines = 1, style = onSky())
                            Text(
                                clockText(time, clock.zone), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                style = onSky(TextStyle(fontFeatureSettings = "tnum")),
                            )
                        }
                    }
                }
                if (scrub != null) {
                    ArcText(Offset(here.x, here.y - with(density) { 34.dp.toPx() })) {
                        Text(
                            bubble, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.glass(OnbLook.capsule).padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        if (interactive) {
            Spacer(Modifier.height(6.dp))
            OnbFoot(t(if (arc.isDay) "onb2_hint_sun" else "onb2_hint_moon"), align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.glass(OnbLook.capsule).padding(2.dp).selectableGroup()) {
                listOf(DayArc.Half.DAY to "onb2_day", DayArc.Half.NIGHT to "onb2_night").forEach { (h, l) ->
                    val on = arc.isDay == (h == DayArc.Half.DAY)
                    val source = remember { MutableInteractionSource() }
                    Box(
                        Modifier.heightIn(min = 34.dp).widthIn(min = 80.dp).focusRing(source).clip(OnbLook.capsule)
                            .background(if (on) OnbLook.chip else Color.Transparent)
                            .selectable(on, source, indication = null, role = Role.Tab) { s.scrub = null; s.half = if (h == currentHalf) null else h }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(t(l), color = if (on) Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Text centred on [at] (pixels from the box's top left, never mirrored), kept inside the box. */
@Composable
private fun ArcText(at: Offset, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().layout { m, c ->
            val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
            val x = (at.x - p.width / 2f).toInt().coerceIn(0, maxOf(0, c.maxWidth - p.width))
            val y = (at.y - p.height / 2f).toInt().coerceIn(0, maxOf(0, c.maxHeight - p.height))
            layout(c.maxWidth, c.maxHeight) { p.place(x, y) }
        },
    ) { content() }
}

/**
 * Your day: built from the answers. The arc (in one column here; in a wide window in the leading pane), the next
 * prayer counting down, the six times, and three cards: the Qibla, the Hijri date, and the real weather (when it is
 * on and fresh) or the length of daylight.
 */
@Composable
private fun DayStep(o: Onb) {
    val clock = o.clock ?: return
    val now = o.now
    val days = remember(clock, now.atZone(clock.zone).toLocalDate()) { days(clock, now) }
    val calc = remember { PrayerCalculator() }
    val next = remember(days, now.epochSecond) { calc.nextPrayer(days, now) }
    StepFrame(o, button = { OnbPrimaryButton(t("onb2_continue"), onClick = { o.go(o.next()) }) }) {
        OnbEyebrow(t("onb2_day_eyebrow"), Modifier.enter(0))
        Spacer(Modifier.height(6.dp))
        OnbTitle(t("onb2_day_title", clock.city.displayName(o.lang)), Modifier.enter(0), maxLines = 2)
        if (!o.wide) {
            Spacer(Modifier.height(8.dp))
            DayArcView(o, clock, interactive = true, modifier = Modifier.enter(1))
        }
        Spacer(Modifier.height(16.dp))
        if (next != null) {
            val name = t(prayerKey(next.event))
            val left = countdown(PrayerCalculator.remaining(now, next.at))
            val time = clockText(next.at, clock.zone)
            val spoken = t("countdown_a11y", name, time, left)
            Column(Modifier.enter(2).clearAndSetSemantics { contentDescription = spoken }) {
                Text(t("onb2_countdown", name, left), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, style = onSky(TextStyle(fontFeatureSettings = "tnum")))
                Text(t("onb2_at_time", time), color = onbSecondary(), fontSize = 15.sp, style = onSky(TextStyle(fontFeatureSettings = "tnum")))
            }
            Spacer(Modifier.height(14.dp))
        }
        TimesRow(clock, days, next, Modifier.enter(3))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.enter(4).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val card = Modifier.weight(1f).fillMaxHeight()
            val city = clock.city
            val atKaaba = Qibla.distanceMeters(city.location) < 1000
            val bearing = Qibla.bearing(city.location)
            MiniCard(t("qibla"), if (atKaaba) t("onb2_at_kaaba") else t("qibla_bearing", bearing.roundToInt().toString()), card) { Needle(if (atKaaba) null else bearing) }
            MiniCard(t("onb2_today"), hijriText(now, clock) ?: "—", card) {
                Icon(rememberVectorPainter(OnbIcons.date), contentDescription = null, tint = Gold, modifier = Modifier.size(28.dp))
            }
            val w = o.weather
            if (w != null) {
                MiniCard(t("onb2_weather"), t(w.symbol.condition.stringKey) + " · " + weatherTemperature(w, weatherUnits()), card) {
                    Icon(weatherIcon(w.symbol.condition, SkyPeriod.of(clock, now).hasStars), contentDescription = null, tint = Gold, modifier = Modifier.size(28.dp))
                }
            } else {
                val today = days[1]
                val light = today[PrayerEvent.SUNRISE]?.let { a -> today[PrayerEvent.MAGHRIB]?.let { b -> Duration.between(a, b) } }
                MiniCard(t("onb2_daylight"), light?.let { t("onb2_duration", weatherNumber(it.toHours().toInt()), weatherNumber(it.toMinutesPart())) } ?: "—", card) {
                    Icon(onbIcon("ic_p_sunrise"), contentDescription = null, tint = Gold, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/** Today's six times (tomorrow's after Isha, so the highlighted column and the countdown agree); the next in the accent. */
@Composable
private fun TimesRow(clock: Clock, days: List<DaySchedule>, next: sa.zood.nearmosque.core.Upcoming?, modifier: Modifier) {
    val row = if (next?.isTomorrow == true) days[2] else days[1]
    Row(modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), OnbLook.fill).padding(vertical = 8.dp, horizontal = 2.dp)) {
        PrayerEvent.entries.forEach { e ->
            val on = next?.event == e
            val (time, mark) = row[e]?.let { timeParts(it, clock.zone) } ?: ("—" to null)
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    t(prayerKey(e)), color = if (on) Gold else Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 1.dp),
                )
                Text(time, color = if (on) Gold else Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
                if (mark != null) Text(mark, color = if (on) Gold else Muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

/** A time and its AM/PM mark apart ("11:40" and "AM"), so six fit side by side; no mark where the clock has none. */
private fun timeParts(at: Instant, zone: java.time.ZoneId): Pair<String, String?> {
    val full = clockText(at, zone)
    val mark = DateTimeFormatter.ofPattern("a", Locale.forLanguageTag(Strings.language)).format(at.atZone(zone))
    return if (mark.isNotBlank() && full.contains(mark)) full.replace(mark, "").trim() to mark else full to null
}

@Composable
private fun MiniCard(label: String, value: String, modifier: Modifier, visual: @Composable () -> Unit) {
    Column(modifier.glass(OnbLook.card, OnbLook.fill).padding(12.dp).semantics(mergeDescendants = true) { }) {
        visual()
        Spacer(Modifier.height(8.dp))
        Text(label, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

/** A small compass needle pointing at [bearing] from north (null: at the Kaaba, faint and upright). */
@Composable
private fun Needle(bearing: Double?) {
    Canvas(Modifier.size(28.dp).clearAndSetSemantics { }) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        drawCircle(OnbLook.track, r - 1.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        rotate((bearing ?: 0.0).toFloat(), c) {
            val tip = Offset(c.x, c.y - r * 0.78f)
            val path = Path().apply {
                moveTo(tip.x, tip.y); lineTo(c.x + r * 0.22f, c.y + r * 0.1f); lineTo(c.x, c.y - r * 0.05f); lineTo(c.x - r * 0.22f, c.y + r * 0.1f); close()
            }
            drawPath(path, Gold.copy(alpha = if (bearing == null) 0.25f else 1f))
        }
    }
}

/** "25 Rabiʻ II 1448 AH" in the interface's language (Western digits, as the desktop's clock), with the Hijri adjustment. */
private fun hijriText(now: Instant, clock: Clock): String? = runCatching {
    val loc = Locale.forLanguageTag(Strings.language)
    val h = HijrahDate.from(now.atZone(clock.zone).toLocalDate().plusDays(clock.settings.hijriAdjustmentDays.toLong()))
    DateTimeFormatter.ofPattern("d MMMM y G", loc).withChronology(HijrahChronology.INSTANCE).format(h)
}.getOrNull()

/**
 * Exactly what leaves the PC: worked out here; alerts from the PC itself; only for mosques and weather, rounded. No
 * prompt to ask for on a PC, so the button is Finish; it still turns into a spinner and a check before the end.
 */
@Composable
private fun PrivacyStep(o: Onb) {
    val scope = rememberCoroutineScope()
    // The step's own: Back or Escape during the spinner leaves it, and the next visit starts at the label again.
    var allow by remember { mutableStateOf(AllowPhase.IDLE) }
    fun press() {
        if (allow != AllowPhase.IDLE) return
        allow = AllowPhase.WORKING
        scope.launch {
            delay(OnboardingTiming.ALLOW_SPINNER.toLong())
            allow = AllowPhase.DONE
            delay(OnboardingTiming.ALLOW_HOLD.toLong())
            o.go(OnboardingStep.DONE)
        }
    }
    // The whole timeline, its last sentence and the weather's on/off should show above the button without a scroll:
    // where they would not (the window's first size, 980 × 680, or a long language), the step sets itself a size
    // smaller, then one more. It still scrolls if even that does not fit.
    var tight by remember { mutableIntStateOf(0) }
    val type = PrivacyType.levels[tight]
    StepFrame(o, onOverflow = { if (tight < PrivacyType.levels.lastIndex) tight++ }, button = {
        Column {
            AllowButton(t("onb2_finish"), allow, onClick = ::press)
            Spacer(Modifier.height(if (tight > 0) 6.dp else 10.dp))
            OnbFoot(t("onb2_change_later"), Modifier.fillMaxWidth(), align = TextAlign.Center)
        }
    }) {
        OnbTitle(t("onb2_privacy_title_device"), Modifier.enter(0), size = type.title)
        Spacer(Modifier.height(type.gap))
        Column(Modifier.enter(1)) {
            TimelineNode(rememberVectorPainter(OnbIcons.computer), first = true, line = true, t("onb2_privacy_n1_title_device"), t("onb2_privacy_n1_body"), type)
            TimelineNode(onbIcon("ic_bell"), first = false, line = true, t("onb2_privacy_n2_title"), t("onb2_privacy_n2_body_device"), type)
            TimelineNode(onbIcon("ic_pin"), first = false, line = false, t("onb2_privacy_n3_title"), t("onb2_privacy_n3_body_desktop"), type) {
                val on = o.settings.skyWeather
                Spacer(Modifier.height(if (tight > 0) 6.dp else 8.dp))
                Text(
                    t(if (on) "onb2_privacy_weather_on" else "onb2_privacy_weather_off"), color = if (on) Gold else Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.glass(OnbLook.capsule, if (on) OnbLook.accentSoft else OnbLook.fill)
                        .padding(horizontal = 12.dp, vertical = if (tight > 0) 4.dp else 5.dp),
                )
            }
        }
    }
}

/** The privacy step's title, gaps and timeline type: as designed, a size smaller, and the tightest. */
private class PrivacyType(
    val title: Pair<TextUnit, TextUnit>?, val gap: Dp, val nodeTitle: TextUnit, val nodeTitleLine: TextUnit,
    val body: TextUnit, val bodyLine: TextUnit, val nodeGap: Dp, val indent: Dp,
) {
    companion object {
        val levels = listOf(
            PrivacyType(null, 20.dp, 17.sp, 22.sp, 14.sp, 20.sp, 16.dp, 14.dp),
            PrivacyType(24.sp to 30.sp, 12.dp, 16.sp, 20.sp, 13.sp, 18.sp, 10.dp, 14.dp),
            PrivacyType(21.sp to 26.sp, 10.dp, 15.sp, 19.sp, 12.5.sp, 17.sp, 8.dp, 10.dp),
        )
    }
}

/** One stop on the privacy timeline: its 32 dp node (the first filled), the line down to the next, the title and text. */
@Composable
private fun TimelineNode(
    icon: Painter, first: Boolean, line: Boolean, title: String, body: String, type: PrivacyType = PrivacyType.levels[0],
    extra: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) { }) {
        Column(Modifier.width(32.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).background(if (first) Gold else Card, CircleShape).border(2.dp, Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (first) Navy else Gold, modifier = Modifier.size(17.dp))
            }
            if (line) Box(Modifier.width(2.dp).weight(1f).background(Brush.verticalGradient(listOf(Gold, Gold.copy(alpha = 0.25f)))))
        }
        Spacer(Modifier.width(type.indent))
        Column(Modifier.weight(1f).padding(bottom = if (line) type.nodeGap else 0.dp)) {
            Text(
                title, color = Ink, fontSize = type.nodeTitle, lineHeight = type.nodeTitleLine, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            Text(body, color = Muted, fontSize = type.body, lineHeight = type.bodyLine)
            extra()
        }
    }
}

/** The main button turning into a 54 dp circle with a spinner, then the check popping in. */
@Composable
private fun AllowButton(text: String, phase: AllowPhase, onClick: () -> Unit) {
    val reduce = LocalOnbReduce.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width by animateDpAsState(if (phase == AllowPhase.IDLE) maxWidth else 54.dp, if (reduce) snap() else OnbMotion.spring(), label = "allow")
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier.width(width).heightIn(min = 54.dp).pressScale(source).focusRing(source).background(Gold, OnbLook.capsule)
                .clickable(source, indication = null, enabled = phase == AllowPhase.IDLE, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = text },
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                AllowPhase.IDLE -> Text(text, color = Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 20.dp))
                AllowPhase.WORKING -> CircularProgressIndicator(Modifier.size(22.dp), color = Navy, strokeWidth = 2.5.dp)
                AllowPhase.DONE -> Icon(rememberVectorPainter(OnbIcons.check), contentDescription = null, tint = Navy, modifier = Modifier.size(26.dp).pop())
            }
        }
    }
}

/** All set: the next prayer, its time and the city. The header has gone; the sun is where it really is. */
@Composable
private fun DoneStep(o: Onb) {
    val clock = o.clock ?: return
    val calc = remember { PrayerCalculator() }
    val next = remember(clock, o.now.epochSecond / 60) { calc.nextPrayer(days(clock, o.now), o.now) }
    val sub = next?.let { t("onb2_done_sub", t(prayerKey(it.event)), clockText(it.at, clock.zone), clock.city.displayName(o.lang)) + " " + t("onb2_done_notify") }
    StepFrame(o, hero = true) {
        OnbTitle(t("onb2_done_title"), Modifier.enter(0), large = true)
        if (sub != null) {
            Spacer(Modifier.height(10.dp))
            OnbBody(sub, Modifier.enter(1))
        }
        Spacer(Modifier.height(24.dp))
        OnbPrimaryButton(t("onb2_open_app"), onClick = { o.update(o.settings.copy(onboarded = true, weatherOffered = true)) }, modifier = Modifier.enter(2))
    }
}
