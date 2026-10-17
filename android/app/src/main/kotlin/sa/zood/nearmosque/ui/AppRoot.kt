package sa.zood.nearmosque.ui

import sa.zood.nearmosque.ui.theme.Accent

import sa.zood.nearmosque.ui.theme.Ink

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.flowOf
import sa.zood.nearmosque.AppContainer
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.AlignmentDetector
import sa.zood.nearmosque.core.Angles
import sa.zood.nearmosque.core.CompassState
import sa.zood.nearmosque.ui.ask.AskScreen
import sa.zood.nearmosque.ui.ask.AskViewModel
import sa.zood.nearmosque.ui.glass.SkyBackdrop
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.glass.under as underWeather
import androidx.lifecycle.repeatOnLifecycle
import sa.zood.nearmosque.ui.mosques.MosquesScreen
import sa.zood.nearmosque.ui.mosques.MosquesViewModel
import sa.zood.nearmosque.ui.onboarding.OnboardingScreen
import sa.zood.nearmosque.core.OnboardingStep
import sa.zood.nearmosque.core.WeatherCondition
import sa.zood.nearmosque.ui.prayer.CalculationSheet
import sa.zood.nearmosque.ui.prayer.CityPickerSheet
import sa.zood.nearmosque.ui.prayer.PrayerScreen
import sa.zood.nearmosque.ui.prayer.PrayerViewModel
import sa.zood.nearmosque.ui.prayer.QiblaCompassScreen
import sa.zood.nearmosque.ui.settings.SettingsScreen
import sa.zood.nearmosque.ui.theme.LocalSky
import sa.zood.nearmosque.ui.theme.Sky
import sa.zood.nearmosque.ui.theme.Tokens

enum class Tab(val label: Int, val icon: Int, val horizon: Float) {
    PRAYER(R.string.tab_prayer, R.drawable.ic_tab_prayer, 0.64f),
    MOSQUES(R.string.tab_mosques, R.drawable.ic_tab_mosque, 0.42f),
    ASK(R.string.tab_ask_short, R.drawable.ic_tab_ask, 0.30f),
}

/**
 * Two tabs (Prayer & Qibla, Nearest Mosque) on a floating glass bar with Ask AI as a separate gold pill
 * beside it that opens the chat full screen, all over one animated sky that follows the prayer period
 * (the horizon glides between screens). Settings, downloads and sources sit behind the gear on each
 * tab. First launch shows the welcome tour (First Light); Settings can replay it.
 */
@Composable
fun AppRoot(
    container: AppContainer, initialTab: Tab = Tab.PRAYER, showOnboarding: Boolean? = null,
    onboardingStep: OnboardingStep = OnboardingStep.PROMISE, onboardingStill: Boolean = false, onboardingPreview: WeatherCondition? = null,
    route: kotlinx.coroutines.flow.MutableStateFlow<String?>? = null,
) {
    androidx.compose.runtime.CompositionLocalProvider(sa.zood.nearmosque.LocalAppContainer provides container) {
        AppRootContent(container, initialTab, showOnboarding, OnboardingStart(onboardingStep, onboardingStill, onboardingPreview), route)
    }
}

/** Where the welcome tour starts (tests and screenshots start later, with no timers, and fix the weather preview). */
private data class OnboardingStart(val step: OnboardingStep, val still: Boolean, val preview: WeatherCondition?)

@Composable
private fun AppRootContent(container: AppContainer, initialTab: Tab, showOnboarding: Boolean?, onboarding: OnboardingStart, route: kotlinx.coroutines.flow.MutableStateFlow<String?>?) {
    val factory = remember(container) {
        viewModelFactory {
            initializer { PrayerViewModel(container) }
            initializer { MosquesViewModel(container) }
            initializer { AskViewModel(container) }
        }
    }
    val prayerVm: PrayerViewModel = viewModel(factory = factory)
    val mosquesVm: MosquesViewModel = viewModel(factory = factory)
    val askVm: AskViewModel = viewModel(factory = factory)
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    // The tab under Ask, to return to when it closes.
    var under by rememberSaveable { mutableStateOf(if (initialTab == Tab.ASK) Tab.PRAYER else initialTab) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCity by rememberSaveable { mutableStateOf(false) }
    var showCalc by rememberSaveable { mutableStateOf(false) }
    var showCompass by rememberSaveable { mutableStateOf(false) }
    val prayer by prayerVm.ui.collectAsStateWithLifecycle()
    // Widgets and app-icon shortcuts.
    val asked = route?.collectAsStateWithLifecycle()?.value
    val askLang = sa.zood.nearmosque.ui.Format.languageCode(androidx.compose.ui.platform.LocalContext.current)
    LaunchedEffect(asked) {
        val uri = asked?.let { android.net.Uri.parse(if (it.contains("://")) it else "nearmosque://$it") }
        when (uri?.host) {
            "qibla" -> { tab = Tab.PRAYER; under = Tab.PRAYER; showCompass = true }
            "mosques" -> { tab = Tab.MOSQUES; under = Tab.MOSQUES }
            "mosque" -> { tab = Tab.MOSQUES; under = Tab.MOSQUES; mosquesVm.requestedId.value = uri.getQueryParameter("id") }
            "ask" -> {
                tab = Tab.ASK
                // A question from a widget or shortcut starts its own conversation (earlier turns would be mixed in).
                uri.getQueryParameter("q")?.takeIf { it.isNotBlank() }?.let { askVm.newConversation(); askVm.ask(it, askLang) }
                uri.getQueryParameter("draft")?.takeIf { it.isNotBlank() }?.let { askVm.draft.value = it }
            }
            "prayer" -> { tab = Tab.PRAYER; under = Tab.PRAYER }
        }
        if (asked != null) route.value = null
    }

    // Heading sensors run only while a compass-bearing screen is visible (and the app is in the foreground).
    val loc = prayer.location?.location
    val headingFlow = remember(loc, tab, showCompass, showSettings) {
        if (loc != null && (tab != Tab.ASK || showCompass) && !showSettings) container.heading.headings(loc) else flowOf(CompassState.BearingOnly)
    }
    val compass by headingFlow.collectAsStateWithLifecycle<CompassState>(CompassState.BearingOnly)
    val detector = remember { AlignmentDetector() }
    val haptics = LocalHapticFeedback.current
    var aligned by remember { mutableStateOf(false) }
    LaunchedEffect(compass, prayer.qiblaBearing) {
        val live = compass as? CompassState.Live
        val bearing = prayer.qiblaBearing
        if (live == null || bearing == null || live.accuracyDeg == null) { aligned = false; detector.update(180.0, null); return@LaunchedEffect }
        if (detector.update(Angles.relativeToQibla(bearing, live.headingTrue), live.accuracyDeg)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        aligned = detector.aligned
    }

    BackHandler(enabled = tab != Tab.PRAYER && !showSettings) { tab = if (tab == Tab.ASK) under else Tab.PRAYER }

    // Sky follows the weather (opt-in): the prayer city's weather veils the sky, and LocalSky carries the veiled
    // colours, so the glass, the ink and the compact bar follow. Silent fallback to the normal sky.
    val sky = Sky.of(prayer.sky, sa.zood.nearmosque.ui.theme.LocalDark.current).underWeather(prayer.weather)
    // Asked only while the app is on screen (MET's terms for mobile apps: no background fetching), at once when
    // the switch is turned on or the city changes (the core keeps 60 s between requests), then when the cache
    // allows. Turning the switch off forgets the forecast. Unknown settings (still loading) change nothing. Only a
    // city's coordinates are sent: with "Use my location", the city near the device, and none without one.
    val weatherOn = prayer.settings?.skyWeather
    val weatherPlace = prayer.location?.weatherLocation
    val weatherKey = weatherPlace?.let(sa.zood.nearmosque.core.Weather::key)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(weatherOn, weatherKey) {
        when {
            weatherOn == null -> Unit
            !weatherOn -> container.weather.clear()
            weatherPlace != null -> lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    container.weather.refreshIfDue(weatherPlace)
                    kotlinx.coroutines.delay(container.weather.millisUntilDue(weatherPlace))
                }
            }
        }
    }
    val horizon by animateFloatAsState(tab.horizon, spring(dampingRatio = 0.86f, stiffness = 120f), label = "horizon")
    val onboard = showOnboarding ?: (prayer.settings?.onboarded == false)
    // Swiping between the two pages, only while one of them is what is on screen.
    val swipeOn = tab != Tab.ASK && !showSettings && !onboard
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    CompositionLocalProvider(LocalSky provides sky) {
        Box(Modifier.fillMaxSize().background(sky.low).pageSwipe(tab, swipeOn, rtl) { tab = it; under = it }) {
            SkyBackdrop(sky, Modifier.fillMaxSize(), horizon = horizon, skyline = tab == Tab.ASK, weather = prayer.weather)
            when (tab) {
                Tab.PRAYER -> PrayerScreen(
                    prayerVm, prayer, compass, aligned,
                    onOpenSettings = { showSettings = true }, onPickCity = { showCity = true },
                    onOpenCalculation = { showCalc = true }, onOpenCompass = { showCompass = true },
                    onOpenMosques = { tab = Tab.MOSQUES; under = Tab.MOSQUES },
                )
                Tab.MOSQUES -> MosquesScreen(mosquesVm, compass, onOpenSettings = { showSettings = true })
                Tab.ASK -> AskScreen(askVm, onClose = { tab = under })
            }
            if (tab != Tab.ASK) {
                GlassTabBar(tab, onSelect = { tab = it; if (it != Tab.ASK) under = it }, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }

        if (showSettings) {
            SettingsScreen(
                container, prayer, onPickCity = { showCity = true }, onOpenCalculation = { showCalc = true },
                onReplayTour = { showSettings = false; prayerVm.setOnboarded(false) },
                onOnlineSearch = prayerVm::setOnlineSearch,
                onSkyWeather = prayerVm::setSkyWeather,
                onClose = { showSettings = false },
            )
        }
        if (showCompass) {
            Dialog(onDismissRequest = { showCompass = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                QiblaCompassScreen(prayer, compass, aligned, onClose = { showCompass = false })
            }
        }
        AnimatedVisibility(onboard, enter = fadeIn(tween(300)), exit = fadeOut(tween(400))) {
            OnboardingScreen(
                container, prayerVm, prayer, onPickCity = { showCity = true }, onFinish = { prayerVm.setOnboarded(true) },
                startStep = onboarding.step, still = onboarding.still, preview = onboarding.preview,
            )
        }
        if (showCity) CityPickerSheet(container, prayerVm, onDismiss = { showCity = false })
        if (showCalc) prayer.settings?.prayer?.let { CalculationSheet(it, prayerVm, onDismiss = { showCalc = false }) }
    }
}

/**
 * A sideways swipe between Prayer and Mosques, watched before the page sees it and never consumed, so the
 * page's own scrolling and the map's panning and pinching carry on as usual. On Prayer a clear sideways
 * swipe (far enough, and mostly sideways) goes forward to Mosques; on Mosques only a swipe that starts at
 * the edge it comes back from returns to Prayer, so dragging the map never changes page. Forward follows
 * the reading direction: right to left in LTR, left to right in RTL. Sheets and dialogs are their own
 * windows and never reach it.
 */
private fun Modifier.pageSwipe(tab: Tab, enabled: Boolean, rtl: Boolean, onSwipe: (Tab) -> Unit): Modifier =
    if (!enabled) this else pointerInput(tab, rtl) {
        val far = 90.dp.toPx()
        val edge = 40.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Back to Prayer starts at the leading edge (left in LTR, right in RTL).
            val atEdge = if (rtl) down.position.x >= size.width - edge else down.position.x <= edge
            if (tab == Tab.MOSQUES && !atEdge) return@awaitEachGesture
            var last = down.position
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // A second finger is a pinch or a zoom, not a page swipe.
                if (event.changes.size > 1) return@awaitEachGesture
                val c = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                last = c.position
                if (!c.pressed) break
            }
            val dx = last.x - down.position.x
            val dy = last.y - down.position.y
            if (abs(dx) <= far || abs(dx) <= 2 * abs(dy)) return@awaitEachGesture
            val forward = if (rtl) dx > 0 else dx < 0
            when {
                tab == Tab.PRAYER && forward -> onSwipe(Tab.MOSQUES)
                tab == Tab.MOSQUES && !forward -> onSwipe(Tab.PRAYER)
            }
        }
    }

/**
 * Floating glass capsule with the two tabs (the selected one sits in a gold glass bubble) and, beside
 * it, the gold "Ask AI" pill.
 */
@Composable
private fun GlassTabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val askLabel = stringResource(R.string.tab_ask)
    // Nearly opaque, so the list scrolling underneath never shows through the labels.
    val sky = LocalSky.current
    val barTint = if (sky.dark) sky.low.copy(alpha = 0.96f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.96f)
    Row(
        modifier.navigationBarsPadding().padding(bottom = 10.dp).widthIn(max = 460.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).glass(RoundedCornerShape(50), tint = barTint).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(Tab.PRAYER, Tab.MOSQUES).forEach { t ->
                val on = t == selected
                val bg by animateFloatAsState(if (on) 1f else 0f, tween(250), label = "tab")
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .background(Tokens.gold.copy(alpha = 0.22f * bg), RoundedCornerShape(50))
                        .clickable(role = Role.Tab) { onSelect(t) }
                        .semantics { this.selected = on }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(t.icon), contentDescription = null, tint = if (on) Accent else Ink, modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(t.label), color = if (on) Accent else Ink.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.sp),
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        Row(
            Modifier.heightIn(min = 68.dp)
                .glass(RoundedCornerShape(50), tint = androidx.compose.ui.graphics.lerp(barTint, Tokens.gold, if (sky.dark) 0.35f else 0.45f).copy(alpha = 0.94f))
                .clickable(role = Role.Button, onClickLabel = askLabel) { onSelect(Tab.ASK) }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Text(askLabel, color = Ink, maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}
