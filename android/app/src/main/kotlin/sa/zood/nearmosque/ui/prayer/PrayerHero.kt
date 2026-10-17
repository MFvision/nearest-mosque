package sa.zood.nearmosque.ui.prayer

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.semantics.role

import sa.zood.nearmosque.ui.glass.drawLightRim

import sa.zood.nearmosque.ui.glass.drawLightSheen

import sa.zood.nearmosque.ui.theme.SkyLight

import sa.zood.nearmosque.ui.theme.LocalSky

import sa.zood.nearmosque.ui.theme.Accent

import sa.zood.nearmosque.ui.theme.Ink

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.Angles
import sa.zood.nearmosque.core.CompassState
import sa.zood.nearmosque.core.PrayerCalculator
import sa.zood.nearmosque.core.PrayerMoment
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.glass.GlassButton
import sa.zood.nearmosque.ui.glass.GlassButtonText
import sa.zood.nearmosque.ui.glass.GlassIconButton
import sa.zood.nearmosque.ui.glass.LocationPill
import sa.zood.nearmosque.ui.glass.LogoDisc
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.glass.reducedMotion
import sa.zood.nearmosque.ui.theme.Tokens
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ArcMode { LIVE, NORTH_UP, NONE }

/**
 * Top of Prayer & Qibla, the middle of the three views: glass location pill and gear, the Qibla arc with
 * the logo at the top and the next prayer inside, lit by the time of day (the glass reflects the sun or
 * the moon from where it is). The logo's arrow and the gold dot on the arc point to the Qibla relative to
 * the top of the phone; when the phone faces it the logo glows. Without a live heading both are
 * north-up. Tapping anywhere on the arc opens the full Qibla view. Below: the nearest mosque.
 */
@Composable
fun PrayerHero(
    ui: PrayerUi, compass: CompassState, aligned: Boolean,
    onLocation: () -> Unit, onSettings: () -> Unit, onQibla: () -> Unit, onNearest: (sa.zood.nearmosque.core.RankedMosque) -> Unit = {},
) {
    val context = LocalContext.current
    val bearing = ui.qiblaBearing
    val live = compass as? CompassState.Live
    val mode = when {
        bearing == null -> ArcMode.NONE
        live != null -> ArcMode.LIVE
        else -> ArcMode.NORTH_UP
    }
    val dot = when {
        bearing == null -> 0.0
        live != null -> Angles.relativeToQibla(bearing, live.headingTrue)
        else -> bearing
    }
    val sky = LocalSky.current
    // Under clouds, rain or fog the glass catches less light (the shared WeatherLook glow).
    val weatherLight = sa.zood.nearmosque.core.WeatherLook.of(ui.weather?.symbol).glowStrength.toFloat()
    val light = SkyLight.of(ui.arc, ui.sky, sky, LocalLayoutDirection.current == LayoutDirection.Rtl, weatherLight)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                LocationPill(ui.location?.name ?: stringResource(R.string.choose_city_title), onClick = onLocation)
            }
            GlassIconButton(rememberVectorPainter(Icons.Filled.Settings), stringResource(R.string.settings), onSettings)
        }
        // Sky follows the weather: one quiet line under the city, the temperature and the wind only.
        androidx.compose.animation.AnimatedVisibility(
            ui.weather != null, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(),
        ) {
            val shown = remember { arrayOfNulls<sa.zood.nearmosque.core.WeatherNow>(1) }
            ui.weather?.let { shown[0] = it }
            shown[0]?.let { WeatherLine(it, ui.location?.name.orEmpty(), night = ui.sky.hasStars) }
        }
        val n = ui.nearestMosque
        QiblaArc(
            dot, mode, aligned, light = light,
            kaabaDistance = ui.qiblaDistance?.let { stringResource(R.string.qibla_distance, Format.distance(context, it)) },
            nearest = n?.let { stringResource(R.string.nearest_mosque_chip, Format.distance(context, it.distanceMeters)) },
            onNearest = n?.let { { onNearest(it) } },
            modifier = Modifier.widthIn(max = 520.dp).clickable(enabled = bearing != null, role = Role.Button, onClickLabel = stringResource(R.string.qibla_open_compass), onClick = onQibla),
        ) {
            ArcContent(ui, compass, aligned)
        }
    }
}

/**
 * "18°C · Wind 12 km/h" in the locale's digits and units, read out as one phrase with the city and the
 * condition. Each value is isolated, so a minus sign and the degree sign keep their order in RTL text. A small
 * condition icon leads the line (the night one when the sky has stars), since a clear sky barely changes the sky.
 */
@Composable
fun WeatherLine(w: sa.zood.nearmosque.core.WeatherNow, city: String, night: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val units = remember(config) { sa.zood.nearmosque.data.WeatherDisplay.units() }
    val temp = stringResource(
        if (units.temperature == sa.zood.nearmosque.core.TemperatureUnit.CELSIUS) R.string.weather_temp_c else R.string.weather_temp_f,
        Format.number(context, units.temperature(w.temperatureC).toDouble(), 0),
    )
    val wind = stringResource(
        if (units.wind == sa.zood.nearmosque.core.WindUnit.KMH) R.string.weather_wind_kmh else R.string.weather_wind_mph,
        Format.number(context, units.wind(w.windMs).toDouble(), 0),
    )
    val a11y = stringResource(R.string.weather_a11y, city, stringResource(weatherConditionName(w.symbol.condition)), temp, wind)
    val line = stringResource(R.string.weather_line, sa.zood.nearmosque.core.Weather.isolate(temp), sa.zood.nearmosque.core.Weather.isolate(wind))
    Row(modifier.padding(top = 4.dp).clearAndSetSemantics { contentDescription = a11y }, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(weatherIcon(w.symbol.condition, night)), contentDescription = null, tint = Ink.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            line, color = Ink.copy(alpha = 0.85f), maxLines = 1, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** The condition's icon (Material Symbols, res/drawable/ic_wx_*), with the moon for a clear or partly cloudy night. */
fun weatherIcon(c: sa.zood.nearmosque.core.WeatherCondition, night: Boolean): Int = when (c) {
    sa.zood.nearmosque.core.WeatherCondition.CLEAR -> if (night) R.drawable.ic_wx_clear_night else R.drawable.ic_wx_clear_day
    sa.zood.nearmosque.core.WeatherCondition.PARTLY_CLOUDY -> if (night) R.drawable.ic_wx_partly_cloudy_night else R.drawable.ic_wx_partly_cloudy_day
    sa.zood.nearmosque.core.WeatherCondition.CLOUDY -> R.drawable.ic_wx_cloudy
    sa.zood.nearmosque.core.WeatherCondition.RAIN -> R.drawable.ic_wx_rain
    sa.zood.nearmosque.core.WeatherCondition.THUNDERSTORM -> R.drawable.ic_wx_thunderstorm
    sa.zood.nearmosque.core.WeatherCondition.FOG -> R.drawable.ic_wx_fog
    sa.zood.nearmosque.core.WeatherCondition.SNOW -> R.drawable.ic_wx_snow
}

/** weather_clear, weather_partly_cloudy, ... */
fun weatherConditionName(c: sa.zood.nearmosque.core.WeatherCondition): Int = when (c) {
    sa.zood.nearmosque.core.WeatherCondition.CLEAR -> R.string.weather_clear
    sa.zood.nearmosque.core.WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    sa.zood.nearmosque.core.WeatherCondition.CLOUDY -> R.string.weather_cloudy
    sa.zood.nearmosque.core.WeatherCondition.RAIN -> R.string.weather_rain
    sa.zood.nearmosque.core.WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    sa.zood.nearmosque.core.WeatherCondition.FOG -> R.string.weather_fog
    sa.zood.nearmosque.core.WeatherCondition.SNOW -> R.string.weather_snow
}

/** Text size at 130% or more (Settings → Display → Font size). */
@Composable
fun largeText(): Boolean = LocalDensity.current.fontScale >= 1.3f

@Composable
private fun ArcContent(ui: PrayerUi, compass: CompassState, aligned: Boolean) {
    val context = LocalContext.current
    val zone = ui.location?.zoneId
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val moment = ui.moment
        if (moment != null && zone != null) {
            val name = stringResource(Format.prayerName(moment.event))
            // The big time grows with the text size up to 115%, so it still fits inside the arc.
            val fs = LocalDensity.current.fontScale
            val bigSize = (58f * minOf(fs, 1.15f) / fs).sp
            val remaining = PrayerCalculator.remaining(ui.now, moment.until)
            when (moment.stage) {
                PrayerMoment.Stage.UPCOMING -> {
                    val time = Format.time(context, moment.adhan, zone)
                    val a11y = stringResource(R.string.countdown_a11y, name, time, Format.countdown(context, remaining))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { contentDescription = a11y; heading() }) {
                        Text(name, color = Ink.copy(alpha = 0.92f), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
                        // A long AM/PM word (Assamese, Kannada, Marathi...) at half the size, and the whole time
                        // smaller still if it does not fit on one line inside the arc.
                        val (_, mark) = Format.timeWithLongMark(context, moment.adhan, zone)
                        val big = androidx.compose.ui.text.buildAnnotatedString {
                            append(time)
                            if (mark != null) addStyle(androidx.compose.ui.text.SpanStyle(fontSize = 0.5.em), mark.first, mark.last + 1)
                        }
                        Text(
                            big, color = Ink, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false,
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = bigSize, stepSize = 2.sp),
                            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            stringResource(R.string.remaining_long, Format.remainingLong(context, remaining)), color = Ink.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        )
                        // Its iqama, quietly, when the text is small enough to leave room for it.
                        val iqama = moment.iqama
                        if (iqama != null && iqama.isAfter(moment.adhan) && !largeText()) {
                            Text(
                                stringResource(R.string.iqama_at, Format.time(context, iqama, zone)), color = Ink.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                }
                PrayerMoment.Stage.TO_IQAMA -> {
                    // The Adhan has been called: "Time for Asr", then the countdown to the iqama.
                    val title = stringResource(R.string.reminder_title, name)
                    val a11y = title + ", " + stringResource(R.string.iqama_left, Format.remainingLong(context, remaining))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { contentDescription = a11y; heading() }) {
                        Text(title, color = Ink.copy(alpha = 0.92f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
                        Text(stringResource(R.string.iqama_in), color = Ink.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            Format.countdown(context, remaining), color = Ink, fontSize = bigSize, fontWeight = FontWeight.Light, maxLines = 1,
                            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        )
                        moment.iqama?.let {
                            Text(
                                stringResource(R.string.iqama_at, Format.time(context, it, zone)), color = Ink.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                }
                PrayerMoment.Stage.PRAYER -> {
                    // The prayer itself: no countdown, and the next prayer quietly below.
                    val title = stringResource(R.string.prayer_started, name)
                    val nextName = stringResource(Format.prayerName(moment.next.event))
                    val nextTime = Format.time(context, moment.next.at, zone)
                    val a11y = title + ". " + stringResource(
                        R.string.countdown_a11y, nextName, nextTime, Format.countdown(context, PrayerCalculator.remaining(ui.now, moment.next.at)),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { contentDescription = a11y; heading() }) {
                        Text(title, color = Ink.copy(alpha = 0.92f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "$nextName  $nextTime", color = Ink.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                }
            }
        } else if (ui.location == null) {
            Text(stringResource(R.string.app_name), color = Ink, style = MaterialTheme.typography.headlineMedium)
        }
        val bearing = ui.qiblaBearing
        if (bearing != null) {
            // Which way to turn (the logo's arrow shows it too); the angle from north only without a compass.
            Spacer(Modifier.size(12.dp))
            Text(
                qiblaGuidance(compass, bearing), color = if (aligned) Accent else Ink, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                stringResource(R.string.sky_card_hint), color = Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Large circle whose top is the phone's forward direction (live) or north (north-up). The brand disc
 * sits at the top and the gold dot at [angle] degrees clockwise from the top, reached by the shortest
 * path. The lower half fades out so content can sit inside. Geometry never mirrors with RTL.
 */
@Composable
fun QiblaArc(
    angle: Double, mode: ArcMode, aligned: Boolean, modifier: Modifier = Modifier, springs: Boolean = true,
    light: SkyLight? = null, kaabaDistance: String? = null, nearest: String? = null, onNearest: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduced = reducedMotion()
    val shown = remember { Animatable(angle.toFloat()) }
    LaunchedEffect(angle) {
        val target = Angles.shortestTarget(shown.value.toDouble(), angle).toFloat()
        if (reduced || !springs) shown.snapTo(target) else shown.animateTo(target, spring(dampingRatio = 0.75f, stiffness = 140f))
    }
    val outer = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // At least square; taller when the text inside needs more lines (longer languages, large text),
        // so it never runs into the card below. The ring itself depends on the width only.
        BoxWithConstraints(modifier.fillMaxWidth()) {
          val side = maxWidth
          Box(Modifier.fillMaxWidth().heightIn(min = side)) {
            val density = LocalDensity.current
            val wPx = with(density) { side.toPx() }
            val r = wPx * 0.43f
            val discPx = with(density) { Tokens.discSize.dp.toPx() }
            val discY = discPx / 2 + with(density) { 6.dp.toPx() }
            val c = Offset(wPx / 2, discY + r)
            val ink = Ink
            Box(Modifier.size(side)) {
                Canvas(Modifier.fillMaxSize()) {
                    if (light != null) {
                        // The glass catching the sun (or the moon): a soft glow on that side and a bright stretch of rim.
                        drawLightSheen(light, c, r)
                        drawLightRim(light, c, r, 6.dp.toPx())
                    }
                    drawCircle(
                        Brush.verticalGradient(0f to ink.copy(alpha = 0.55f), 0.45f to ink.copy(alpha = 0.22f), 0.8f to ink.copy(alpha = 0f), startY = c.y - r, endY = c.y + r),
                        radius = r, center = c, style = Stroke(1.5.dp.toPx()),
                    )
                    val box = Offset(c.x - r, c.y - r)
                    val sz = Size(2 * r, 2 * r)
                    if (aligned) {
                        drawArc(Tokens.gold.copy(alpha = 0.9f), -90f, 38f, false, box, sz, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(Tokens.gold.copy(alpha = 0.9f), -90f, -38f, false, box, sz, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    if (mode != ArcMode.NONE) {
                        val rel = Angles.normalize180(shown.value.toDouble()).toFloat()
                        drawArc(ink.copy(alpha = 0.75f), -90f, rel, false, box, sz, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        val rad = Math.toRadians(shown.value.toDouble())
                        val m = Offset(c.x + r * sin(rad).toFloat(), c.y - r * cos(rad).toFloat())
                        drawCircle(Brush.radialGradient(listOf(Tokens.gold.copy(alpha = 0.6f), Color.Transparent), center = m, radius = 22.dp.toPx()), 22.dp.toPx(), m)
                        drawCircle(Tokens.gold, 10.dp.toPx(), m)
                        drawCircle(ink.copy(alpha = 0.85f), 10.dp.toPx(), m, style = Stroke(2.dp.toPx()))
                    }
                }
                LogoDisc(aligned, Modifier.align(Alignment.TopCenter).padding(top = 6.dp), arrow = if (mode == ArcMode.NONE) null else shown.value, light = light)
                if (mode == ArcMode.NORTH_UP) {
                    Text(
                        stringResource(R.string.compass_north), color = Ink.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = (Tokens.discSize + 12).dp),
                    )
                }
                CompositionLocalProvider(LocalLayoutDirection provides outer) {
                    // Small labels along the sides of the ring: the distance to the Kaaba and the nearest mosque (tappable).
                    val labelR = r + with(density) { 10.dp.toPx() }
                    kaabaDistance?.let {
                        CurvedSideLabel(it, Ink.copy(alpha = 0.8f), c, labelR, 250f, icon = painterResource(R.drawable.logo_body), size = 11.sp)
                    }
                    if (nearest != null && onNearest != null) {
                        CurvedSideLabel(nearest, Accent, c, labelR, 110f, icon = painterResource(R.drawable.ic_tab_mosque), bold = true, onClick = onNearest)
                    }
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides outer) {
                // Content starts below the disc (never overlapping it), centred horizontally inside the circle.
                Box(
                    Modifier.width(with(density) { (r * 1.7f).toDp() }).align(Alignment.TopCenter)
                        .padding(top = (Tokens.discSize + 6 + 26).dp),
                    contentAlignment = Alignment.TopCenter,
                    content = content,
                )
            }
          }
        }
    }
}

/**
 * The first of the three views: when the big header scrolls away, this bar takes its place at the top
 * (full width, on the sky, so the page never shows through). The logo's arrow keeps pointing to the
 * Qibla. Tapping it scrolls back up to the big header.
 */
@Composable
fun CompactPrayerBar(ui: PrayerUi, compass: CompassState, aligned: Boolean, onExpand: () -> Unit) {
    val context = LocalContext.current
    val moment = ui.moment ?: return
    val zone = ui.location?.zoneId ?: return
    val sky = LocalSky.current
    val bearing = ui.qiblaBearing
    val live = compass as? CompassState.Live
    val target = when {
        bearing == null -> null
        live != null -> Angles.relativeToQibla(bearing, live.headingTrue)
        else -> bearing
    }
    val shown = remember { Animatable((target ?: 0.0).toFloat()) }
    val reduced = reducedMotion()
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        val t = Angles.shortestTarget(shown.value.toDouble(), target).toFloat()
        if (reduced) shown.snapTo(t) else shown.animateTo(t, spring(dampingRatio = 0.75f, stiffness = 140f))
    }
    Row(
        Modifier.fillMaxWidth()
            // The top of the same sky, opaque, reaching up under the status bar.
            .shadow(8.dp, RectangleShape)
            .background(Brush.verticalGradient(listOf(sky.top, androidx.compose.ui.graphics.lerp(sky.top, sky.mid, 0.35f))))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.expand), onClick = onExpand)
            .statusBarsPadding()
            .heightIn(min = Tokens.compactHeight.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoDisc(aligned, size = 46.dp, arrow = if (target == null) null else shown.value)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val name = stringResource(Format.prayerName(moment.event))
            val (title, line) = when (moment.stage) {
                PrayerMoment.Stage.UPCOMING -> name + "  " + Format.time(context, moment.adhan, zone) to
                    stringResource(R.string.remaining_long, Format.remainingLong(context, PrayerCalculator.remaining(ui.now, moment.until)))
                PrayerMoment.Stage.TO_IQAMA -> stringResource(R.string.reminder_title, name) to
                    stringResource(R.string.iqama_left, Format.remainingLong(context, PrayerCalculator.remaining(ui.now, moment.until)))
                PrayerMoment.Stage.PRAYER -> stringResource(R.string.prayer_started, name) to
                    stringResource(Format.prayerName(moment.next.event)) + "  " + Format.time(context, moment.next.at, zone)
            }
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Text(line, color = Accent, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
        }
        Icon(rememberVectorPainter(Icons.Filled.KeyboardArrowDown), contentDescription = null, tint = Ink)
    }
}

@Composable
internal fun MiniQiblaIndicator(bearing: Double, compass: CompassState) {
    val live = compass as? CompassState.Live
    val angle = if (live != null) Angles.relativeToQibla(bearing, live.headingTrue) else bearing
    val label = stringResource(R.string.qibla) + ", " + stringResource(R.string.qibla_bearing, Format.degrees(LocalContext.current, bearing))
    Box(Modifier.size(40.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        val ink = Ink
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(ink.copy(alpha = 0.6f), size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            if (live == null) drawCircle(ink, 2.dp.toPx(), Offset(center.x, 4.dp.toPx()))
        }
        Icon(painterResource(R.drawable.ic_directions), contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp).rotate(angle.toFloat()))
    }
}
