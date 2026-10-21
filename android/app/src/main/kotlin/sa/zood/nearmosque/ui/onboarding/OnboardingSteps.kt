package sa.zood.nearmosque.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.zood.nearmosque.AppStyle
import sa.zood.nearmosque.Appearance
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.AdhanChoice
import sa.zood.nearmosque.core.AlertSettings
import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.FridayPreview
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.OnboardingStep
import sa.zood.nearmosque.core.TemperatureUnit
import sa.zood.nearmosque.core.Weather
import sa.zood.nearmosque.core.WeatherPreview
import sa.zood.nearmosque.core.WindUnit
import sa.zood.nearmosque.data.PrayerLocation
import sa.zood.nearmosque.platform.AudioCatalog
import sa.zood.nearmosque.platform.AudioPreview
import sa.zood.nearmosque.platform.ExternalActions
import sa.zood.nearmosque.platform.PrayerSilence
import sa.zood.nearmosque.platform.WidgetStyle
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.glass.loopingFloat
import sa.zood.nearmosque.ui.prayer.LocateState
import sa.zood.nearmosque.ui.prayer.weatherConditionName
import sa.zood.nearmosque.ui.prayer.weatherIcon
import sa.zood.nearmosque.ui.styleSwatch
import sa.zood.nearmosque.ui.theme.Accent
import sa.zood.nearmosque.ui.theme.Ink
import sa.zood.nearmosque.ui.theme.LocalSky
import sa.zood.nearmosque.ui.theme.Tokens

/* The promise and the questions: the place, the Asr school, the Adhan, Friday, the weather sky and the look. */

/** One promise, one button: the sun rises behind it (HeroLayer). */
@Composable
internal fun PromiseStep(ctx: OnbContext) {
    val content: @Composable () -> Unit = {
        Eyebrow(stringResource(R.string.app_name), Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        OnbTitle(stringResource(R.string.onb2_promise_title), Modifier.enter(0), large = true, align = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        OnbBody(stringResource(if (ctx.device) R.string.onb2_promise_body_device else R.string.onb2_promise_body), Modifier.enter(1), align = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        PrimaryButton(stringResource(R.string.onb2_begin), onClick = { ctx.go(OnboardingStep.PLACE) }, modifier = Modifier.enter(2))
        Spacer(Modifier.height(14.dp))
        OnbFoot(stringResource(R.string.onb2_promise_foot), Modifier.enter(3), align = TextAlign.Center)
    }
    if (ctx.wide) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

private object Unset

/**
 * Where do you pray: the device's location (the system asks at that moment) or the city picker. When a place is set
 * here, its row is ringed, the city and method chips fly in, and the flow moves on. On a replay with a place already
 * set, Continue keeps it.
 */
@Composable
internal fun PlaceStep(ctx: OnbContext) {
    val context = LocalContext.current
    val vm = ctx.vm
    val locate by vm.locate.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.useDeviceLocation(context.getString(R.string.location_current))
    }
    val loc = ctx.ui.location
    val loaded = ctx.ui.settings != null
    // The place when the step opened (once the settings have loaded), so only a place chosen here moves on by itself.
    var baseline by remember { mutableStateOf<Any?>(Unset) }
    var picked by remember { mutableStateOf(false) }
    var pulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(loaded, loc) {
        if (!loaded) return@LaunchedEffect
        if (baseline === Unset) { baseline = loc; return@LaunchedEffect }
        if (loc != null && loc != baseline) {
            picked = true
            pulse++
            ctx.answer(OnboardingStep.PLACE)
            ctx.advance(OnboardingStep.PLACE, OnboardingStep.ASR)
        }
    }
    val bySearch = loc?.source == PrayerLocation.Source.CITY
    val byDevice = loc?.source == PrayerLocation.Source.DEVICE
    StepScaffold(
        ctx,
        bottom = if (loc != null && !picked) ({ PrimaryButton(stringResource(R.string.onb2_continue), onClick = { ctx.answer(OnboardingStep.PLACE); ctx.go(OnboardingStep.ASR) }) }) else null,
    ) {
        OnbTitle(stringResource(R.string.onb2_where_title), Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(if (ctx.device) R.string.onb2_where_body_device else R.string.onb2_where_body), Modifier.enter(1))
        Spacer(Modifier.height(24.dp))
        Column(Modifier.enter(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val locating = locate == LocateState.Locating
            OnbRow(
                rememberVectorPainter(Icons.Filled.LocationOn), stringResource(R.string.use_my_location),
                when {
                    locating -> stringResource(R.string.locating)
                    byDevice -> stringResource(R.string.onb_location_set, loc.name)
                    else -> stringResource(R.string.onb2_where_location_sub)
                },
                onClick = {
                    vm.clearLocateState()
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                selected = byDevice && !locating, busy = locating, pulseKey = if (byDevice) pulse else 0,
            )
            OnbRow(
                rememberVectorPainter(Icons.Filled.Search), stringResource(R.string.search_city),
                if (bySearch) stringResource(R.string.onb_location_set, loc.name) else stringResource(R.string.onb2_where_search_sub),
                onClick = ctx.onPickCity, selected = bySearch, pulseKey = if (bySearch) pulse else 0,
            )
        }
        val error = when (locate) {
            LocateState.Denied -> R.string.location_denied
            LocateState.NoFix -> R.string.location_fix_failed
            LocateState.LocationOff -> R.string.location_off
            else -> null
        }
        if (error != null) {
            OnbFoot(stringResource(error), Modifier.padding(top = 12.dp), color = if (LocalSky.current.dark) Color(0xFFF2B8B5) else Color(0xFFB3261E))
        }
    }
}

/**
 * When does Asr begin: the two schools as cards with a stick, the sun and its shadow drawn to scale, and the real Asr
 * time for each today. The calculation method, suggested for the place, can be changed from the line above them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AsrStep(ctx: OnbContext) {
    val context = LocalContext.current
    val s = ctx.ui.settings ?: return
    val loc = ctx.ui.location
    val method = s.prayer.method
    val methodName = stringResource(Format.methodName(method))
    val named = loc != null && (loc.source == PrayerLocation.Source.CITY || loc.nearCity != null)
    val line = if (!s.methodChosenByUser && named) stringResource(R.string.onb2_method_line_city, methodName, loc.name) else stringResource(R.string.onb2_method_line, methodName)
    val today = loc?.let { ctx.ui.now.atZone(it.zoneId).toLocalDate() }
    val times = remember(loc, s.prayer, today) { loc?.let { ctx.container.calculator.asrTimes(it.location, today!!, it.zoneId, s.prayer) }.orEmpty() }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<AsrMadhab?>(null) }
    var pulse by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val selected = chosen ?: s.prayer.madhab
    fun pick(m: AsrMadhab) {
        val again = ctx.isAnswered(OnboardingStep.ASR)
        if (!again) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        chosen = m
        pulse++
        ctx.vm.setMadhab(m)
        ctx.answer(OnboardingStep.ASR)
        if (again) ctx.shared.asrBump++
        ctx.advance(OnboardingStep.ASR, OnboardingStep.ADHAN)
    }
    StepScaffold(ctx, bottom = null) {
        OnbTitle(stringResource(R.string.onb2_asr_title), Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(R.string.onb2_asr_body), Modifier.enter(1))
        Spacer(Modifier.height(18.dp))
        GlassBox(Modifier.fillMaxWidth().enter(2), RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(line, color = InkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                TextAction(stringResource(R.string.change), onClick = { sheet = true })
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).selectableGroup().enter(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AsrMadhab.SHAFI, AsrMadhab.HANAFI).forEach { m ->
                val hanafi = m == AsrMadhab.HANAFI
                AsrCard(
                    title = stringResource(if (hanafi) R.string.asr_hanafi else R.string.onb2_asr_standard),
                    sub = stringResource(if (hanafi) R.string.onb2_asr_hanafi_sub else R.string.onb2_asr_standard_sub),
                    time = times[m]?.let { stringResource(R.string.onb2_asr_at, Format.time(context, it, loc!!.zoneId)) } ?: "—",
                    hanafi = hanafi, selected = selected == m, pulseKey = if (chosen == m) pulse else 0,
                    onClick = { pick(m) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
                Text(stringResource(R.string.calculation), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
                Method.entries.forEach { m ->
                    val on = m == method
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                            .selectable(on, role = Role.RadioButton) {
                                ctx.vm.setMethod(m)
                                sheet = false
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Format.methodName(m)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (on) Icon(rememberVectorPainter(Icons.Filled.Check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * One school: the stick on the ground and its shadow at the start of Asr (as long as the stick, or twice), growing in
 * after the step appears, with the sun's ray through the stick's top. Drawn in a 150 × 96 box, never mirrored.
 */
@Composable
private fun AsrCard(title: String, sub: String, time: String, hanafi: Boolean, selected: Boolean, pulseKey: Int, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val reduce = LocalOnbReduce.current
    val sky = LocalSky.current
    val grow = remember { Animatable(if (reduce) 1f else 0f) }
    val ray = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            launch { delay(700); ray.animateTo(1f, tween(600)) }
            delay(260)
            grow.animateTo(1f, OnbMotion.soft())
        }
    }
    val ink = sky.ink
    val track = if (sky.dark) Color.White.copy(alpha = 0.26f) else Tokens.textLight.copy(alpha = 0.20f)
    val shadow = if (sky.dark) Color.Black.copy(alpha = 0.42f) else Tokens.textLight.copy(alpha = 0.30f)
    val gold = Tokens.gold
    Box(
        modifier.heightIn(min = 248.dp).pressScale(source)
            .selectionRing(selected, pulseKey, CardShape, gold)
            .glass(CardShape, onbGlassTint(), shadow = 8.dp)
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(96.dp).padding(top = 2.dp).clearAndSetSemantics { }) {
                val k = minOf(size.width / 150f, size.height / 96f)
                translate(left = (size.width - 150f * k) / 2, top = (size.height - 96f * k) / 2) {
                    scale(k, k, pivot = Offset.Zero) {
                        val sun = if (hanafi) Offset(14f, 28f) else Offset(20f, 16f)
                        val tip = if (hanafi) 122f else 86f
                        drawLine(track, Offset(6f, 82f), Offset(144f, 82f), 1.5f, StrokeCap.Round)
                        val end = 50f + (tip - 50f) * grow.value
                        drawLine(gold.copy(alpha = 0.75f * ray.value), sun, Offset(tip, 82f), 1.25f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(2f, 4f)))
                        if (grow.value > 0f) drawLine(shadow, Offset(50f, 82f), Offset(end, 82f), 5f, StrokeCap.Round)
                        drawLine(ink, Offset(50f, 82f), Offset(50f, 46f), 3f, StrokeCap.Round)
                        drawCircle(Brush.radialGradient(listOf(HeroColors.sunMid.copy(alpha = 0.8f), HeroColors.sunMid.copy(alpha = 0f)), sun, 12f), 12f, sun)
                        drawCircle(HeroColors.sunMid, 6f, sun)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, color = Ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp))
            Spacer(Modifier.height(4.dp))
            Text(sub, color = InkSoft, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f).heightIn(min = 10.dp))
            Text(time, color = Accent, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
        }
        // Top right in either direction: the drawing (sun at the left) is never mirrored.
        if (selected) CheckBadge(Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopRight).padding(10.dp))
    }
}

/**
 * How should we call you: Makkah, Madinah, the short Adhan or a banner only, each with a preview of the real recording,
 * and the quiet during prayer (Do Not Disturb, with the permission Android asks for). Saved as soon as it is tapped.
 */
@Composable
internal fun AdhanStep(ctx: OnbContext) {
    val context = LocalContext.current
    val s = ctx.ui.settings ?: return
    val alerts = s.alerts
    LaunchedEffect(Unit) { ctx.setAdhanVisited() }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var pulse by remember { mutableIntStateOf(0) }
    val selected = chosen?.let { AdhanChoice.valueOf(it) } ?: alerts.onboardingChoice
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val restore = AudioPreview.volumeKeysForAdhan(context)
        onDispose { AudioPreview.stop(); restore() }
    }
    val haptics = LocalHapticFeedback.current
    fun pick(c: AdhanChoice) {
        val again = ctx.isAnswered(OnboardingStep.ADHAN)
        chosen = c.name
        pulse++
        ctx.vm.setAlerts(alerts.with(c))
        ctx.answer(OnboardingStep.ADHAN)
        if (again) ctx.shared.adhanBump++ else haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    // Do Not Disturb access is granted in system settings: checked again when the flow comes back, and the silences
    // planned at once when it changed (as Prayer & Audio does), since nothing else rebuilds them then.
    val scope = rememberCoroutineScope()
    var silenceAllowed by remember { mutableStateOf(PrayerSilence(context).granted()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        val now = PrayerSilence(context).granted()
        if (now != silenceAllowed) {
            silenceAllowed = now
            scope.launch(Dispatchers.Default) { PrayerSilence(context).sync(ctx.container.settings.settings.first()) }
        }
        onPauseOrDispose { }
    }
    fun openSilencePermission() {
        try {
            context.startActivity(PrayerSilence.permissionIntent())
        } catch (_: android.content.ActivityNotFoundException) {
        }
    }
    StepScaffold(
        ctx,
        bottom = {
            PrimaryButton(stringResource(R.string.onb2_continue), onClick = {
                // Someone who has set nothing sees Makkah selected: Continue saves what they saw.
                if (chosen == null && alerts == AlertSettings()) ctx.vm.setAlerts(alerts.with(selected))
                ctx.answer(OnboardingStep.ADHAN)
                ctx.go(ctx.next())
            })
        },
    ) {
        OnbTitle(stringResource(R.string.onb2_adhan_title), Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(R.string.onb2_adhan_body), Modifier.enter(1))
        Spacer(Modifier.height(18.dp))
        Column(Modifier.selectableGroup().enter(2), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val short = AudioCatalog.shortAdhan(alerts.adhanSource)
            listOf(
                Triple(AdhanChoice.MAKKAH, R.string.onb2_adhan_makkah, "adhan_makkah"),
                Triple(AdhanChoice.MADINAH, R.string.onb2_adhan_madinah, "adhan_madinah"),
                Triple(AdhanChoice.SHORT, R.string.onb2_adhan_short, short),
                Triple(AdhanChoice.BANNER_ONLY, R.string.onb2_adhan_banner, null),
            ).forEach { (c, label, recording) ->
                val name = stringResource(label)
                val playing = tick >= 0 && recording != null && AudioPreview.playing == recording
                AdhanRow(
                    name, if (c == AdhanChoice.SHORT) stringResource(R.string.onb2_adhan_short_sub) else null,
                    selected = selected == c, pulseKey = if (chosen == c.name) pulse else 0, flat = recording == null, playing = playing,
                    onClick = { pick(c) },
                    onPreview = recording?.let { r -> { AudioPreview.toggle(context, r) { tick++ } } },
                    previewLabel = if (playing) stringResource(R.string.alarm_stop) else stringResource(R.string.preview_play, name),
                )
            }
        }
        OnbFoot(stringResource(R.string.adhan_alarm_note_android), Modifier.padding(top = 10.dp).enter(3))
        Spacer(Modifier.height(10.dp))
        Column(Modifier.enter(3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwitchRow(
                painterResource(R.drawable.ic_bell_off), stringResource(R.string.silence_section), stringResource(R.string.onb2_quiet_sub_friday_android),
                checked = s.silence.enabled,
                onChange = { on ->
                    ctx.vm.setSilence(context, s.silence.copy(enabled = on))
                    if (on && !PrayerSilence(context).granted()) openSilencePermission()
                },
            )
            if (s.silence.enabled && !silenceAllowed) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CardShape).clickable(role = Role.Button) { openSilencePermission() }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.silence_permission), color = Accent, style = skyText(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)))
                        Text(stringResource(R.string.silence_permission_note), color = InkSoft, style = skyText(MaterialTheme.typography.bodySmall))
                    }
                    Icon(rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowRight), contentDescription = null, tint = Accent)
                }
            }
        }
    }
}

/** An Adhan choice: a five-bar wave (moving while its preview plays), the label, the radio, and the preview button. */
@Composable
private fun AdhanRow(
    label: String, sub: String?, selected: Boolean, pulseKey: Int, flat: Boolean, playing: Boolean,
    onClick: () -> Unit, onPreview: (() -> Unit)?, previewLabel: String,
) {
    val source = remember { MutableInteractionSource() }
    val sky = LocalSky.current
    val reduce = LocalOnbReduce.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).pressScale(source)
            .selectionRing(selected, pulseKey, CardShape, Tokens.gold)
            .glass(CardShape, onbGlassTint(), shadow = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 58.dp).selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wave(flat, playing && !reduce)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                sub?.let { Text(it, color = InkSoft, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(22.dp).border(2.dp, if (selected) Tokens.gold else InkSoft.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(12.dp).pop().background(Tokens.gold, CircleShape))
            }
        }
        if (onPreview != null) {
            val chip = if (sky.dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.62f)
            Box(
                Modifier.padding(end = 8.dp).size(44.dp).clip(CircleShape).background(if (playing) Tokens.gold.copy(alpha = 0.22f) else chip)
                    .clickable(role = Role.Button, onClickLabel = previewLabel, onClick = onPreview).semantics { contentDescription = previewLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) painterResource(R.drawable.ic_audio_stop) else rememberVectorPainter(Icons.Filled.PlayArrow),
                    contentDescription = null, tint = if (playing) Accent else Ink, modifier = Modifier.size(20.dp),
                )
            }
        } else {
            // Keeps the radios of all four rows in one column.
            Spacer(Modifier.width(52.dp))
        }
    }
}

@Composable
private fun Wave(flat: Boolean, playing: Boolean) {
    val phase = loopingFloat(playing, 720, reverse = false, label = "wave", still = 0f)
    val accent = Accent
    Canvas(Modifier.size(26.dp, 24.dp).clearAndSetSemantics { }) {
        val bars = floatArrayOf(0.35f, 0.7f, 1f, 0.6f, 0.4f)
        val bw = 3.dp.toPx()
        val gap = 2.5.dp.toPx()
        val total = bars.size * bw + (bars.size - 1) * gap
        val full = 22.dp.toPx()
        bars.forEachIndexed { i, b ->
            val k = when {
                flat -> 0.16f
                playing -> 0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin((phase + i * 0.17f) * Math.PI.toFloat()))
                else -> b
            }
            val h = full * k
            val x = (size.width - total) / 2 + i * (bw + gap) + bw / 2
            drawLine(accent.copy(alpha = if (flat) 0.6f else 1f), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), bw, StrokeCap.Round)
        }
    }
}

/**
 * Get ready for Jumu'ah: the Friday reminder (45 minutes before Dhuhr) and Surat Al-Kahf, two independent switches
 * saved at once, and when the reminder is on its real time for the coming Friday.
 */
@Composable
internal fun FridayStep(ctx: OnbContext) {
    val context = LocalContext.current
    val s = ctx.ui.settings ?: return
    val alerts = s.alerts
    val haptics = LocalHapticFeedback.current
    // Kept here as well, so two quick taps never save a stale copy of the other switch.
    var friday by remember { mutableStateOf(alerts.friday) }
    var kahf by remember { mutableStateOf(alerts.kahf) }
    fun save(f: Boolean, k: Boolean) {
        val wasOn = friday || kahf
        val bothNow = f && k && !(friday && kahf)
        friday = f; kahf = k
        ctx.vm.setAlerts(alerts.copy(friday = f, kahf = k))
        ctx.answer(OnboardingStep.FRIDAY)
        if (!wasOn && (f || k)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (bothNow) ctx.shared.fridayBump++
    }
    val loc = ctx.ui.location
    val next = remember(loc, s.prayer, friday, ctx.ui.now.epochSecond / 60) {
        if (!friday || loc == null) null else FridayPreview.next(loc.location, loc.zoneId, ctx.ui.now, s.prayer, ctx.container.calculator)
    }
    StepScaffold(ctx, bottom = { PrimaryButton(stringResource(R.string.onb2_continue), onClick = { ctx.answer(OnboardingStep.FRIDAY); ctx.go(ctx.next()) }) }) {
        if (!ctx.wide) {
            FridayArt(Modifier.enter(0))
            Spacer(Modifier.height(18.dp))
        }
        OnbTitle(stringResource(R.string.onb2_friday_title), Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(R.string.onb2_friday_body), Modifier.enter(1))
        Spacer(Modifier.height(20.dp))
        Column(Modifier.enter(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow(painterResource(R.drawable.ic_onb_friday), stringResource(R.string.alerts_friday), stringResource(R.string.alerts_friday_note), friday, { save(it, kahf) })
            SwitchRow(painterResource(R.drawable.ic_book), stringResource(R.string.alerts_kahf), stringResource(R.string.alerts_kahf_note), kahf, { save(friday, it) })
        }
        if (next != null && loc != null) {
            OnbFoot(
                stringResource(R.string.onb2_friday_next, Format.time(context, next.reminder, loc.zoneId), Format.time(context, next.dhuhr, loc.zoneId)),
                Modifier.padding(top = 12.dp).enter(3), color = Ink,
            )
        }
    }
}

/**
 * Sky follows the weather, the showpiece: the whole sky behind becomes the preview (sample weather, labelled as such;
 * nothing is fetched), cycling until a condition is tapped. Turn on and Not now are equal choices.
 */
@Composable
internal fun WeatherStep(ctx: OnbContext) {
    val context = LocalContext.current
    val shared = ctx.shared
    val night = ctx.ui.sky.hasStars || (ctx.ui.location == null && LocalSky.current.period.hasStars)
    val c = shared.preview
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val units = remember(config) { sa.zood.nearmosque.data.WeatherDisplay.units() }
    val (tempC, kmh) = WeatherPreview.sample(c)
    val temp = stringResource(
        if (units.temperature == TemperatureUnit.CELSIUS) R.string.weather_temp_c else R.string.weather_temp_f,
        Format.number(context, units.temperature(tempC).toDouble(), 0),
    )
    val wind = stringResource(
        if (units.wind == WindUnit.KMH) R.string.weather_wind_kmh else R.string.weather_wind_mph,
        Format.number(context, units.wind(kmh / 3.6).toDouble(), 0),
    )
    // Not now changes nothing: off for someone new, still on for someone seeing the tour again.
    fun finish(on: Boolean) {
        if (on) ctx.vm.setSkyWeather(true)
        ctx.answer(OnboardingStep.WEATHER)
        if (on) ctx.advance(OnboardingStep.WEATHER, ctx.next()) else ctx.go(ctx.next())
    }
    StepScaffold(
        ctx,
        bottom = {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(stringResource(R.string.weather_offer_turn_on), onClick = { finish(true) }, modifier = Modifier.weight(1f).fillMaxHeight())
                GlassWideButton(stringResource(R.string.weather_offer_not_now), onClick = { finish(false) }, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        },
    ) {
        Badge(stringResource(R.string.onb2_new), Modifier.enter(0))
        Spacer(Modifier.height(10.dp))
        OnbTitle(stringResource(R.string.sky_weather), Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(R.string.onb_weather_body), Modifier.enter(1))
        Spacer(Modifier.height(18.dp))
        val group = stringResource(R.string.onb2_weather_conditions)
        Row(
            Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = group }.enter(2),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            WeatherPreview.order.forEach { cond ->
                val on = cond == c
                val name = stringResource(weatherConditionName(cond))
                Box(
                    Modifier.size(44.dp).selectionRing(on, 0, CircleShape, Tokens.gold).glass(CircleShape, onbGlassTint(), shadow = 4.dp)
                        .selectable(on, role = Role.RadioButton) { shared.preview = cond; shared.previewPicked = true }
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(weatherIcon(cond, night)), contentDescription = null, tint = if (on) Accent else Ink, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassBox(Modifier.fillMaxWidth().enter(3)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.onb2_preview), color = Accent, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(stringResource(weatherConditionName(c)), color = Ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp))
                    Text(
                        stringResource(R.string.weather_line, Weather.isolate(temp), Weather.isolate(wind)), color = InkSoft,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                Icon(painterResource(weatherIcon(c, night)), contentDescription = null, tint = Accent, modifier = Modifier.size(42.dp))
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).enter(3), contentAlignment = Alignment.Center) {
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button) { ExternalActions.open(context, Weather.ATTRIBUTION_URL) }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.sky_weather_attribution), color = InkSoft, textAlign = TextAlign.Center,
                    style = skyText(MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline)),
                )
            }
        }
    }
}

/**
 * Choose your look: the app's five styles as large swatches and the appearance. The choice applies at once: this step's
 * accent, its buttons and the header's chips change with it, and the style flies into the header.
 */
@Composable
internal fun LookStep(ctx: OnbContext) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(Appearance.current(context)) }
    val haptics = LocalHapticFeedback.current
    fun pick(key: String) {
        val again = ctx.isAnswered(OnboardingStep.LOOK)
        AppStyle.set(context, key)
        ctx.answer(OnboardingStep.LOOK)
        if (again) ctx.shared.lookBump++ else haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    StepScaffold(ctx, bottom = { PrimaryButton(stringResource(R.string.onb2_continue), onClick = { ctx.answer(OnboardingStep.LOOK); ctx.go(ctx.next()) }) }) {
        OnbTitle(stringResource(R.string.onb2_look_title), Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(R.string.onb2_look_body), Modifier.enter(1))
        Spacer(Modifier.height(18.dp))
        val styleLabel = stringResource(R.string.app_style_title)
        Text(styleLabel, color = InkSoft, style = skyText(MaterialTheme.typography.titleSmall), modifier = Modifier.enter(2).semantics { heading() })
        Spacer(Modifier.height(8.dp))
        val styles = AppStyle.all.mapNotNull { WidgetStyle.of(it) }
        Column(Modifier.selectableGroup().enter(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            styles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val pad = (3 - row.size) / 2f
                    if (pad > 0f) Spacer(Modifier.weight(pad))
                    row.forEach { st -> StyleCard(st, AppStyle.current == st.key, { pick(st.key) }, Modifier.weight(1f).fillMaxHeight()) }
                    if (pad > 0f) Spacer(Modifier.weight(pad))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        val appearance = stringResource(R.string.appearance)
        Text(appearance, color = InkSoft, style = skyText(MaterialTheme.typography.titleSmall), modifier = Modifier.enter(3).semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(50), onbGlassTint(), shadow = 6.dp).padding(4.dp).selectableGroup().enter(3),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(Appearance.SYSTEM to R.string.appearance_system, Appearance.LIGHT to R.string.appearance_light, Appearance.DARK to R.string.appearance_dark).forEach { (m, label) ->
                val on = mode == m
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(50)).background(if (on) Tokens.gold else Color.Transparent)
                        .selectable(on, role = Role.RadioButton) { mode = m; Appearance.set(context, m) }.padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(label), color = if (on) Tokens.navyNight else Ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

/** A style: its widget background as a 56 dp swatch with its accent in the middle, ringed and checked in that accent when chosen. */
@Composable
private fun StyleCard(st: WidgetStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val accent = AppStyle.dark(st.key)
    val (top, bottom) = styleSwatch(st)
    var pulse by remember { mutableIntStateOf(0) }
    Box(
        modifier.heightIn(min = 112.dp).widthIn(min = 96.dp).pressScale(source)
            .selectionRing(selected, pulse, CardShape, accent)
            .glass(CardShape, onbGlassTint(), shadow = 6.dp)
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton) { pulse++; onClick() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(56.dp).background(Brush.verticalGradient(listOf(top, bottom)), CircleShape)
                    .border(1.dp, if (LocalSky.current.dark) Color.White.copy(alpha = 0.3f) else Tokens.textLight.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(22.dp).background(accent, CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(st.label), color = Ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            )
        }
        if (selected) CheckBadge(Modifier.align(Alignment.TopEnd).padding(6.dp), size = 20.dp, color = accent)
    }
}
