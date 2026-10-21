package sa.zood.nearmosque.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.zood.nearmosque.AppContainer
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.AdhanChoice
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.DayArc
import sa.zood.nearmosque.core.OnboardingFlow
import sa.zood.nearmosque.core.OnboardingStep
import sa.zood.nearmosque.core.OnboardingTiming
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.WeatherCondition
import sa.zood.nearmosque.core.WeatherLook
import sa.zood.nearmosque.core.WeatherNow
import sa.zood.nearmosque.core.WeatherPreview
import sa.zood.nearmosque.ui.Adaptive
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.glass.GlassButton
import sa.zood.nearmosque.ui.glass.GlassButtonText
import sa.zood.nearmosque.ui.glass.SkyBackdrop
import sa.zood.nearmosque.ui.glass.drawSkyline
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.glass.reducedMotion
import sa.zood.nearmosque.ui.glass.under as underWeather
import sa.zood.nearmosque.ui.prayer.PrayerUi
import sa.zood.nearmosque.ui.prayer.PrayerViewModel
import sa.zood.nearmosque.ui.prayer.weatherIcon
import sa.zood.nearmosque.ui.settings.LANGUAGES
import sa.zood.nearmosque.ui.settings.setAppLanguage
import sa.zood.nearmosque.ui.theme.LocalDark
import sa.zood.nearmosque.ui.theme.LocalSky
import sa.zood.nearmosque.ui.theme.Sky
import sa.zood.nearmosque.ui.theme.SkyPeriod
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

/**
 * What the steps share with the screen: the sky's weather preview, the day arc's scrubbing (both change the sky behind
 * everything), where the sun is, and the answers that put chips in the header.
 */
@Stable
internal class OnbShared(preview: WeatherCondition) {
    /** The weather step's preview; [previewPicked] stops it cycling. */
    var preview by mutableStateOf(preview)
    var previewPicked by mutableStateOf(false)
    /** The day step: the half shown (null = the one in progress) and the scrub position on it (null = now). */
    var half by mutableStateOf<DayArc.Half?>(null)
    var scrub by mutableStateOf<Float?>(null)
    var dragging by mutableStateOf(false)
    /** The sky period at the scrub position. */
    var scrubPeriod by mutableStateOf<SkyPeriod?>(null)
    /** Whether the arc shown is the day (the sun) or the night (the moon). */
    var arcIsDay by mutableStateOf(true)
    /** Where the sun sits on the day arc, in root pixels. */
    var arcPoint by mutableStateOf<Offset?>(null)
    /** The header's sun slot, in root pixels. */
    var slot by mutableStateOf<Offset?>(null)
    /** Phones: the sky's box and the top of the step's area, in root pixels (the day step keeps its countdown legible). */
    var skyBox by mutableStateOf<Rect?>(null)
    var stepTop by mutableFloatStateOf(0f)
    /** Small bounces for chips chosen again. */
    var asrBump by mutableIntStateOf(0)
    var adhanBump by mutableIntStateOf(0)
    var fridayBump by mutableIntStateOf(0)
    var lookBump by mutableIntStateOf(0)
}

/** Everything a step needs: the real settings and actions, and how to move on. */
internal class OnbContext(
    val container: AppContainer,
    val vm: PrayerViewModel,
    val ui: PrayerUi,
    val flow: OnboardingFlow,
    val shared: OnbShared,
    val step: OnboardingStep,
    /** Two panes (a tablet or an unfolded Fold held sideways). */
    val wide: Boolean,
    /** "Your device" instead of "your phone" (tablets). */
    val device: Boolean,
    /** No timers, no auto-advance (tests and still frames). */
    val still: Boolean,
    val adhanVisited: Boolean,
    val setAdhanVisited: () -> Unit,
    val isAnswered: (OnboardingStep) -> Boolean,
    val answer: (OnboardingStep) -> Unit,
    val go: (OnboardingStep) -> Unit,
    /** Skip from a step (where [OnboardingFlow.skip] allows it). */
    val skip: (OnboardingStep) -> Unit,
    /** Moves on after the answer has been seen (650 ms), if still on [from]. */
    val advance: (from: OnboardingStep, to: OnboardingStep) -> Unit,
    val onPickCity: () -> Unit,
    val onFinish: () -> Unit,
) {
    fun next(): OnboardingStep = flow.next(step) ?: OnboardingStep.DONE
}

/**
 * "First Light": the first-launch flow on the living sky. A promise, then the place, the Asr school, the Adhan, Friday,
 * the weather sky and the look, each answer flying up into the "YOUR DAY" header; then the day built from them, the
 * rest of the app, exactly what leaves the phone (and the notification ask) and the sun landing where it really is.
 * Every answer is saved through the real setting at once. Wide windows show two panes. Back steps back one step (with
 * the predictive gesture); with animations off everything is a crossfade.
 */
@Composable
fun OnboardingScreen(
    container: AppContainer, vm: PrayerViewModel, ui: PrayerUi, onPickCity: () -> Unit, onFinish: () -> Unit,
    startStep: OnboardingStep = OnboardingStep.PROMISE, still: Boolean = false, preview: WeatherCondition? = null,
) {
    val flow = remember { OnboardingFlow(hasLook = true) }
    var stepKey by rememberSaveable { mutableStateOf(startStep.key) }
    val step = OnboardingStep.of(stepKey) ?: OnboardingStep.PROMISE
    // Starting later (tests, debug) counts the earlier steps as answered.
    var furthest by rememberSaveable { mutableIntStateOf(flow.index(startStep)) }
    var answeredKeys by rememberSaveable { mutableStateOf("") }
    var adhanVisited by rememberSaveable { mutableStateOf(flow.index(startStep) > flow.index(OnboardingStep.ADHAN)) }
    val shared = remember { OnbShared(preview ?: WeatherCondition.CLEAR) }
    val reduce = reducedMotion() || still
    val scope = rememberCoroutineScope()
    var advanceJob by remember { mutableStateOf<Job?>(null) }

    fun go(to: OnboardingStep) {
        advanceJob?.cancel()
        stepKey = to.key
        furthest = maxOf(furthest, flow.index(to))
        if (to != OnboardingStep.DAY) { shared.scrub = null; shared.half = null; shared.dragging = false }
    }
    fun answered(s: OnboardingStep) = s.key in answeredKeys.split(',') || furthest > flow.index(s)
    val context = LocalContext.current
    // Skip changes nothing on the steps it passes, except on the Adhan step: someone who has set nothing sees Makkah
    // selected there, and Skip keeps what they saw, as Continue does (a tap has already saved its choice).
    fun skip(from: OnboardingStep) {
        val to = flow.skip(from) ?: return
        val alerts = ui.settings?.alerts
        if (from == OnboardingStep.ADHAN && OnboardingStep.ADHAN.key !in answeredKeys.split(',') && alerts == AlertSettings()) {
            vm.setAlerts(alerts.with(alerts.onboardingChoice))
        }
        go(to)
    }

    // Predictive back, one step at a time: the content leans back as the gesture goes, then the previous step enters.
    var backProgress by remember { mutableFloatStateOf(0f) }
    val backTo = flow.back(step)
    PredictiveBackHandler(enabled = backTo != null) { events ->
        try {
            events.collect { backProgress = it.progress }
            backProgress = 0f
            flow.back(OnboardingStep.of(stepKey) ?: step)?.let(::go)
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }
    // Building and done have no Back button, and the system gesture must not leave the tour half-finished either
    // (Android 8 to 11 would close the app with `onboarded` still false). Only the promise lets Back leave.
    BackHandler(enabled = backTo == null && step != OnboardingStep.PROMISE) {}

    // The weather preview changes condition every 2.6 s until one is chosen (never with still frames).
    LaunchedEffect(step, shared.previewPicked, still) {
        if (step != OnboardingStep.WEATHER || shared.previewPicked || still) return@LaunchedEffect
        while (true) {
            delay(OnboardingTiming.PREVIEW_CYCLE.toLong())
            val order = WeatherPreview.order
            shared.preview = order[(order.indexOf(shared.preview) + 1) % order.size]
        }
    }

    // Tablets say "your device" (the smallest width does not change when the tablet turns).
    val device = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val ctxFor: (Boolean) -> OnbContext = { wide ->
        OnbContext(
            container, vm, ui, flow, shared, step, wide,
            device = device, still = still,
            adhanVisited = adhanVisited, setAdhanVisited = { adhanVisited = true },
            isAnswered = ::answered,
            answer = { s -> if (s.key !in answeredKeys.split(',')) answeredKeys = (answeredKeys.split(',').filter { it.isNotEmpty() } + s.key).joinToString(",") },
            go = ::go,
            skip = ::skip,
            advance = { from, to ->
                advanceJob?.cancel()
                if (!still) advanceJob = scope.launch { delay(OnboardingTiming.ADVANCE.toLong()); if (stepKey == from.key) go(to) }
            },
            onPickCity = onPickCity, onFinish = onFinish,
        )
    }

    // Before any place: the sky of the most populous city in this device's time zone, never saved or shown.
    val previewPeriod by produceState<SkyPeriod?>(null, ui.location == null) {
        if (ui.location != null) return@produceState
        value = runCatching {
            val zone = ZoneId.systemDefault()
            val city = container.cities.index().largest(zone.id) ?: return@runCatching null
            val now = container.clock()
            val today = container.calculator.schedule(city.location, now.atZone(zone).toLocalDate(), zone, sa.zood.nearmosque.core.PrayerSettings())
            val current = PrayerEvent.entries.lastOrNull { e -> today[e]?.let { !it.isAfter(now) } == true }
            SkyPeriod.at(now, today[PrayerEvent.SUNRISE], current, hasLocation = current != null)
        }.getOrNull()
    }

    val dark = LocalDark.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Two panes need height as well: a phone turned sideways (about 393 dp tall) keeps the scrolling column, where
        // the panel would leave each step a sliver.
        val wide = Adaptive.twoPane(maxWidth, maxHeight) && maxHeight >= 480.dp
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val period = when {
            step == OnboardingStep.DAY && shared.scrubPeriod != null -> shared.scrubPeriod!!
            wide && step == OnboardingStep.FRIDAY -> SkyPeriod.DAY
            ui.location != null -> ui.sky
            else -> previewPeriod ?: SkyPeriod.NIGHT
        }
        // The weather step's whole sky is the preview; after it, only real weather (when turned on).
        val weather: WeatherNow? = if (step == OnboardingStep.WEATHER) WeatherPreview.now(shared.preview, ui.now) else ui.weather
        val sky = Sky.of(period, dark).underWeather(weather)
        val glowStrength = WeatherLook.of(weather?.symbol, night = period.hasStars).glowStrength.toFloat()
        val horizonTarget = onbHorizon(step, wide)
        val horizon by animateFloatAsState(horizonTarget, if (reduce) tween(OnbMotion.FADE_MS) else OnbMotion.soft(), label = "horizon")

        CompositionLocalProvider(LocalSky provides sky, LocalOnbReduce provides reduce) {
            Box(Modifier.fillMaxSize().background(sky.low).onGloballyPositioned { shared.skyBox = it.boundsInRoot() }) {
                SkyBackdrop(sky, Modifier.fillMaxSize(), horizon = horizon, animate = !reduce, weather = weather)
                val leadFraction = 0.58f
                // Drawing order: the sky, the sun or moon when done (behind the mosque, as the ridge hides it), the
                // skyline and the scrim, the step, then the sun or moon on every other step (in front of the header).
                if (step == OnboardingStep.DONE || (wide && step == OnboardingStep.FRIDAY)) {
                    Box(Modifier.fillMaxSize().zIndex(1f)) { PaneSkyline(if (wide) leadFraction else 1f, horizon, rtl, large = step == OnboardingStep.FRIDAY) }
                }
                Box(Modifier.fillMaxSize().zIndex(1f)) { Readability(step, period, glowStrength, wide) }
                val backLean = Modifier.graphicsLayer {
                    val p = backProgress
                    if (p > 0f) {
                        if (reduce) {
                            alpha = 1f - 0.3f * p
                        } else {
                            val s = 1f - 0.06f * p
                            scaleX = s; scaleY = s
                            translationX = (if (rtl) -1 else 1) * 16.dp.toPx() * p
                        }
                    }
                }
                val header: @Composable (Modifier) -> Unit = { m ->
                    DayHeader(
                        chipSlots(ui, flow, step, shared, adhanVisited, ::answered), wide,
                        visible = OnboardingFlow.showsHeader(step), onSlot = { shared.slot = it }, modifier = m,
                    )
                }
                Box(Modifier.fillMaxSize().zIndex(2f)) {
                    if (wide) {
                        TwoPane(ctxFor(true), leadFraction, header, backLean)
                    } else {
                        PhoneLayout(ctxFor(false), header, backLean)
                    }
                }
                Box(Modifier.fillMaxSize().zIndex(if (step == OnboardingStep.DONE) 0.5f else 3f)) {
                    HeroLayer(step, ui, shared, wide, leadFraction, horizon, glowStrength, period)
                }
            }
        }
    }
}

/** The chips: city, method, Asr, Adhan, Friday, weather, look, each once its answer is given (or its step passed). */
@Composable
private fun chipSlots(
    ui: PrayerUi, flow: OnboardingFlow, step: OnboardingStep, shared: OnbShared, adhanVisited: Boolean, answered: (OnboardingStep) -> Boolean,
): List<ChipSlot> {
    val s = ui.settings
    val loc = ui.location
    val method = s?.prayer?.method
    val alerts = s?.alerts
    val city = loc?.let { DayChip("city", rememberVectorPainter(Icons.Filled.LocationOn), it.name) }
    val methodChip = if (loc != null && method != null) DayChip("method", painterResource(R.drawable.ic_onb_method), stringResource(Format.methodShortName(method))) else null
    val asr = if (s != null && answered(OnboardingStep.ASR)) {
        DayChip("asr", painterResource(R.drawable.ic_onb_asr), stringResource(if (s.prayer.madhab == AsrMadhab.HANAFI) R.string.asr_hanafi else R.string.onb2_asr_standard), shared.asrBump)
    } else null
    // Passed by Skip without being shown, with nothing set yet: still a placeholder, not "Banner only".
    val adhan = if (alerts != null && answered(OnboardingStep.ADHAN) && (adhanVisited || alerts != AlertSettings())) {
        val label = when (alerts.adhanChoice) {
            AdhanChoice.MAKKAH -> R.string.onb2_chip_makkah
            AdhanChoice.MADINAH -> R.string.onb2_chip_madinah
            AdhanChoice.SHORT -> R.string.onb2_adhan_short
            AdhanChoice.BANNER_ONLY -> R.string.onb2_chip_banner
        }
        DayChip("adhan", painterResource(R.drawable.ic_onb_wave), stringResource(label), shared.adhanBump)
    } else null
    val friday = if (alerts != null && answered(OnboardingStep.FRIDAY) && (alerts.friday || alerts.kahf)) {
        DayChip("friday", painterResource(R.drawable.ic_onb_friday), stringResource(R.string.onb2_chip_friday), shared.fridayBump)
    } else null
    val weather = if (s?.skyWeather == true) {
        DayChip("weather", painterResource(weatherIcon(WeatherCondition.PARTLY_CLOUDY, ui.sky.hasStars)), stringResource(R.string.onb2_chip_weather))
    } else null
    val look = if (flow.hasLook && answered(OnboardingStep.LOOK)) {
        val style = sa.zood.nearmosque.platform.WidgetStyle.of(sa.zood.nearmosque.AppStyle.current) ?: sa.zood.nearmosque.platform.WidgetStyle.CREAM
        DayChip("look", painterResource(R.drawable.ic_palette), stringResource(style.label), shared.lookBump)
    } else null
    return listOf(
        ChipSlot("city", city, 64.dp), ChipSlot("method", methodChip, 80.dp), ChipSlot("asr", asr, 70.dp), ChipSlot("adhan", adhan, 76.dp),
        ChipSlot("friday", friday, null), ChipSlot("weather", weather, null), ChipSlot("look", look, if (flow.hasLook) 60.dp else null),
    )
}

/**
 * Text over the sky stays legible (phones; two panes keep their text in the glass panel): a scrim at the bottom on the
 * promise and done steps, and in dark mode a soft band behind the middle of the screen when the horizon glows bright
 * (day, sunrise and Asr most, Maghrib and Fajr a little).
 */
@Composable
private fun Readability(step: OnboardingStep, period: SkyPeriod, glow: Float, wide: Boolean) {
    if (wide) return
    val sky = LocalSky.current
    val scrim by animateFloatAsState(if (step == OnboardingStep.PROMISE || step == OnboardingStep.DONE) 1f else 0f, tween(700), label = "scrim")
    val bandTarget = if (!sky.dark || !OnboardingFlow.showsHeader(step)) 0f else glowBand(period) * glow
    val band by animateFloatAsState(bandTarget, tween(1100), label = "band")
    val shade = lerp(sky.low, Color.Black, 0.45f)
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        if (scrim > 0f) {
            val stops = if (sky.dark) {
                arrayOf(0f to shade.copy(alpha = 0f), 0.5f to shade.copy(alpha = 0f), 0.7f to shade.copy(alpha = 0.55f * scrim), 1f to shade.copy(alpha = 0.85f * scrim))
            } else {
                arrayOf(0f to Color.White.copy(alpha = 0f), 0.5f to Color.White.copy(alpha = 0f), 0.7f to Color.White.copy(alpha = 0.5f * scrim), 1f to Color.White.copy(alpha = 0.78f * scrim))
            }
            drawRect(Brush.verticalGradient(*stops))
        }
        if (band > 0f) {
            drawRect(
                Brush.verticalGradient(
                    0f to shade.copy(alpha = 0f), 0.38f to shade.copy(alpha = 0f), 0.45f to shade.copy(alpha = band), 0.56f to shade.copy(alpha = band),
                    0.63f to shade.copy(alpha = 0f), 1f to shade.copy(alpha = 0f),
                ),
            )
        }
    }
}

/** How strongly the dark-mode band shades the text over the horizon glow: most by day, a little at Maghrib and Fajr. */
internal fun glowBand(period: SkyPeriod): Float = when (period) {
    SkyPeriod.DAY, SkyPeriod.SUNRISE, SkyPeriod.ASR -> 0.5f
    SkyPeriod.MAGHRIB, SkyPeriod.FAJR -> 0.2f
    else -> 0f
}

/** The horizon as a fraction of the window: low on the promise and done, higher on the other steps (two panes: lower). */
internal fun onbHorizon(step: OnboardingStep, wide: Boolean): Float {
    val bright = step == OnboardingStep.PROMISE || step == OnboardingStep.DONE
    return if (wide) (if (bright) 0.80f else 0.70f) else (if (bright) 0.78f else 0.64f)
}

/**
 * A mosque on the horizon of the sky (the whole window, or the leading [lead] of it when wide): small on the far ridge
 * when done, as on the prototype, and large in the middle of the midday sky on a two-pane Friday step. It fades in;
 * never mirrored.
 */
@Composable
private fun PaneSkyline(lead: Float, horizon: Float, rtl: Boolean, large: Boolean) {
    val sky = LocalSky.current
    val reduce = LocalOnbReduce.current
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, if (reduce) tween(OnbMotion.FADE_MS) else tween(1400, delayMillis = 500)) }
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val paneW = size.width * lead
        val left = if (rtl && lead < 1f) size.width - paneW else 0f
        val w = paneW * (if (large) 0.5f else 0.16f)
        val h = w * (if (large) 0.3f else 0.5f)
        val y0 = size.height * horizon
        val cx = left + paneW * (if (large) 0.5f else 0.74f)
        val base = if (large) y0 + size.height * 0.02f else y0 - size.height * 0.045f
        val shade = if (sky.dark) 1f else 0.35f
        drawSkyline(Offset(cx - w / 2, base - h), Size(w, h), lerp(sky.low, Color.Black, 0.3f * shade).copy(alpha = shown.value))
    }
}

/** Phones (and narrow windows): the header, Back and Skip, then the step in a column at most 520 dp wide. */
@Composable
private fun PhoneLayout(ctx: OnbContext, header: @Composable (Modifier) -> Unit, backLean: Modifier) {
    val step = ctx.step
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(2.dp + 46.dp + 10.dp + 44.dp)) {
            header(Modifier.padding(top = 2.dp, start = 16.dp, end = 16.dp))
            if (step == OnboardingStep.PROMISE) {
                Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 16.dp)) { LanguageMenu() }
            }
            TopBar(
                onBack = ctx.flow.back(step)?.let { { ctx.go(it) } },
                onSkip = ctx.flow.skip(step)?.let { { ctx.skip(step) } },
                modifier = Modifier.padding(top = 58.dp, start = 16.dp, end = 16.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { ctx.shared.stepTop = it.positionInRoot().y }.then(backLean), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 520.dp).fillMaxSize()) {
                StepHost(ctx) { s -> StepContent(ctx.copyFor(s), s) }
            }
        }
    }
}

/**
 * Two panes: the sky, the sun and the header on the leading 58% (with the day arc on the day step), the step in a
 * glass panel on the trailing 42%. In right-to-left languages the panes swap sides; the landscape is never mirrored.
 */
@Composable
private fun TwoPane(ctx: OnbContext, lead: Float, header: @Composable (Modifier) -> Unit, backLean: Modifier) {
    val step = ctx.step
    Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LeadingPane(ctx, header, Modifier.weight(lead).fillMaxHeight())
        BoxWithConstraints(Modifier.weight(1f - lead).fillMaxHeight().padding(vertical = 24.dp).then(backLean), contentAlignment = Alignment.Center) {
            val noBar = step == OnboardingStep.DONE || step == OnboardingStep.BUILDING
            Column(
                Modifier.padding(end = 24.dp, start = 8.dp).widthIn(max = 440.dp).fillMaxWidth()
                    .heightIn(min = 360.dp, max = maxHeight)
                    .glass(RoundedCornerShape(22.dp), onbGlassTint(), shadow = 16.dp)
                    .padding(top = if (noBar) 28.dp else 16.dp, start = 26.dp, end = 26.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (!noBar) {
                    TopBar(
                        onBack = ctx.flow.back(step)?.let { { ctx.go(it) } },
                        onSkip = ctx.flow.skip(step)?.let { { ctx.skip(step) } },
                        trailing = if (step == OnboardingStep.PROMISE) ({ LanguageMenu() }) else null,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                StepHost(ctx) { s -> StepContent(ctx.copyFor(s), s) }
            }
        }
    }
}

/** The leading pane: the header at its top and, on the day step, the arc to walk along. */
@Composable
private fun LeadingPane(ctx: OnbContext, header: @Composable (Modifier) -> Unit, modifier: Modifier) {
    Box(modifier) {
        header(Modifier.padding(top = 18.dp, start = 24.dp, end = 24.dp))
        AnimatedVisibility(ctx.step == OnboardingStep.DAY, Modifier.fillMaxSize(), enter = fadeIn(tween(400)), exit = fadeOut(tween(OnboardingTiming.LEAVE))) {
            Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                DayArcPane(ctx, Modifier.widthIn(max = 560.dp))
            }
        }
    }
}

private fun OnbContext.copyFor(s: OnboardingStep): OnbContext = if (s == step) this else OnbContext(
    container, vm, ui, flow, shared, s, wide, device, still, adhanVisited, setAdhanVisited, isAnswered, answer, go, skip, advance, onPickCity, onFinish,
)

/**
 * The step change: the old step fades up 12 dp (blurred, from Android 12) in 200 ms, the new one enters from 24 dp lower
 * with the spring, its parts 40 ms apart. Under Reduce Motion a 200 ms crossfade.
 */
@Composable
private fun StepHost(ctx: OnbContext, content: @Composable (OnboardingStep) -> Unit) {
    val reduce = LocalOnbReduce.current
    val enterPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val leavePx = with(LocalDensity.current) { 12.dp.roundToPx() }
    AnimatedContent(
        targetState = ctx.step,
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            val leave = tween<Float>(OnboardingTiming.LEAVE, easing = FastOutLinearInEasing)
            (
                if (reduce) {
                    fadeIn(tween(OnbMotion.FADE_MS)) togetherWith fadeOut(tween(OnbMotion.FADE_MS))
                } else {
                    (fadeIn(OnbMotion.spring()) + slideInVertically(OnbMotion.spring()) { enterPx }) togetherWith
                        (fadeOut(leave) + slideOutVertically(tween(OnboardingTiming.LEAVE, easing = FastOutLinearInEasing)) { -leavePx })
                }
            ) using SizeTransform(clip = false)
        },
        label = "step",
    ) { s ->
        val blur by transition.animateDp(label = "leave") { if (it == EnterExitState.PostExit && !reduce) 6.dp else 0.dp }
        Box(Modifier.fillMaxWidth().blur(blur)) { content(s) }
    }
}

@Composable
private fun StepContent(ctx: OnbContext, step: OnboardingStep) {
    when (step) {
        OnboardingStep.PROMISE -> PromiseStep(ctx)
        OnboardingStep.PLACE -> PlaceStep(ctx)
        OnboardingStep.ASR -> AsrStep(ctx)
        OnboardingStep.ADHAN -> AdhanStep(ctx)
        OnboardingStep.FRIDAY -> FridayStep(ctx)
        OnboardingStep.WEATHER -> WeatherStep(ctx)
        OnboardingStep.LOOK -> LookStep(ctx)
        OnboardingStep.BUILDING -> BuildingStep(ctx)
        OnboardingStep.DAY -> DayStep(ctx)
        OnboardingStep.EXTRAS -> ExtrasStep(ctx)
        OnboardingStep.MORE -> MoreStep(ctx)
        OnboardingStep.PRIVACY -> PrivacyStep(ctx)
        OnboardingStep.DONE -> DoneStep(ctx)
    }
}

/**
 * A question step's frame: the content scrolls when it does not fit and the main button stays pinned at the bottom
 * (phones); in the panel the button follows the content.
 */
@Composable
internal fun StepScaffold(ctx: OnbContext, bottom: (@Composable ColumnScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    // The padding sits inside the scrolling area, so a selected row's ring and its pulse are never cut at the sides.
    if (ctx.wide) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp)) { content() }
            if (bottom != null) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 6.dp)) { bottom() }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)) { content() }
            if (bottom != null) Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)) { bottom() }
        }
    }
}

/**
 * The one sun (the moon at night) of the whole flow, above everything in root coordinates: it rises behind the ridge
 * on the promise, sits in the header's slot through the questions, rides the arc on the day and lands where it really
 * is when done. It glides with the soft spring; under Reduce Motion it fades out and back in at its new place.
 */
@Composable
private fun HeroLayer(step: OnboardingStep, ui: PrayerUi, shared: OnbShared, wide: Boolean, lead: Float, horizon: Float, glow: Float, period: SkyPeriod) {
    val reduce = LocalOnbReduce.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val sky = LocalSky.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.clearAndSetSemantics { }) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val paneW = if (wide) w * lead else w
        val paneLeft = if (wide && rtl) w - paneW else 0f
        val bright = step == OnboardingStep.PROMISE || step == OnboardingStep.DONE
        val moon = when (step) {
            OnboardingStep.DAY -> !shared.arcIsDay
            OnboardingStep.PRIVACY, OnboardingStep.DONE -> ui.arc?.isDay == false
            else -> period.hasStars
        }
        val target: Pair<Offset, Dp>? = when (step) {
            OnboardingStep.PROMISE -> Offset(paneLeft + paneW / 2, h * (if (wide) 0.42f else 0.34f)) to 92.dp
            OnboardingStep.DONE -> {
                val f = (ui.arc?.fraction ?: 0.5).toFloat()
                val fx = if (rtl) 1 - f else f
                val m = with(density) { 40.dp.toPx() }
                Offset(paneLeft + m + (paneW - 2 * m) * fx, 0.773f * h - 0.39f * h * sin(PI.toFloat() * f)) to (92.dp * 1.1f)
            }
            OnboardingStep.DAY -> (shared.arcPoint?.let { it - origin } ?: shared.slot?.let { it - origin })?.let { it to (if (shared.arcPoint != null) 34.dp else 22.dp) }
            else -> shared.slot?.let { (it - origin) to 22.dp }
        }
        if (target != null) {
            val (center, disc) = target
            val pos = remember { Animatable(if (step == OnboardingStep.PROMISE && !reduce) center + Offset(0f, with(density) { 120.dp.toPx() }) else center, Offset.VectorConverter) }
            val radius = remember { Animatable(with(density) { disc.toPx() / 2 }) }
            val fade = remember { Animatable(if (step == OnboardingStep.PROMISE && !reduce) 0f else 1f) }
            val immediate = step == OnboardingStep.DAY && shared.dragging
            LaunchedEffect(center, disc, immediate) {
                val r = with(density) { disc.toPx() / 2 }
                when {
                    immediate -> { pos.snapTo(center); radius.snapTo(r) }
                    reduce -> {
                        if ((pos.value - center).getDistance() > 1f || kotlin.math.abs(radius.value - r) > 0.5f) {
                            fade.animateTo(0f, tween(OnbMotion.FADE_MS / 2))
                            pos.snapTo(center); radius.snapTo(r)
                        }
                        fade.animateTo(1f, tween(OnbMotion.FADE_MS / 2))
                    }
                    else -> {
                        if (fade.value < 1f) launch { delay(250); fade.animateTo(1f, tween(300)) }
                        launch { radius.animateTo(r, OnbMotion.soft()) }
                        if (step == OnboardingStep.PROMISE && pos.value.y > center.y + 1f) delay(250)
                        pos.animateTo(center, OnbMotion.soft())
                    }
                }
            }
            val halo = haloScale(!reduce && bright)
            val alpha = (if (bright) 0.55f + 0.45f * glow.coerceIn(0f, 1f) else 1f) * fade.value
            val clipY = if (bright) h * horizon - 0.02f * h else Float.MAX_VALUE
            Canvas(Modifier.fillMaxSize()) {
                clipRect(bottom = minOf(clipY, size.height)) {
                    drawHero(pos.value, radius.value, moon, !sky.dark, halo = bright, haloScale = halo, alpha = alpha)
                }
            }
        }
    }
}

/** Language choice on the promise: the device language or any of the app's languages. */
@Composable
private fun LanguageMenu() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = Format.languageCode(context)
    val followsDevice = AppCompatDelegate.getApplicationLocales().isEmpty
    val label = stringResource(R.string.language)
    Box {
        GlassButton(onClick = { open = true }, modifier = Modifier.heightIn(max = 44.dp).semantics { contentDescription = label }) {
            GlassButtonText(LANGUAGES.firstOrNull { it.first == current }?.second ?: current, painterResource(R.drawable.ic_globe))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.use_device_language)) },
                trailingIcon = { if (followsDevice) Text("✓") },
                onClick = { open = false; AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) },
            )
            LANGUAGES.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    trailingIcon = { if (!followsDevice && code == current) Text("✓") },
                    onClick = { open = false; setAppLanguage(code) },
                )
            }
        }
    }
}
