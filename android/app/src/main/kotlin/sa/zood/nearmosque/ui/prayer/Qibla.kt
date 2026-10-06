package sa.zood.nearmosque.ui.prayer

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.Angles
import sa.zood.nearmosque.core.CompassState
import sa.zood.nearmosque.core.RankedMosque
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.glass.GlassIconButton
import sa.zood.nearmosque.ui.glass.SkyBackdrop
import sa.zood.nearmosque.ui.theme.Accent
import sa.zood.nearmosque.ui.theme.Ink
import sa.zood.nearmosque.ui.theme.LocalSky
import sa.zood.nearmosque.ui.theme.Tokens
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Which way to turn, in plain words (no angles); the angle from north only without a compass. */
@Composable
internal fun qiblaGuidance(compass: CompassState, bearing: Double): String {
    val context = LocalContext.current
    return when (compass) {
        is CompassState.BearingOnly -> stringResource(R.string.qibla_no_sensor, Format.degrees(context, bearing))
        is CompassState.Live -> {
            val rel = Angles.relativeToQibla(bearing, compass.headingTrue)
            when {
                compass.needsCalibration -> stringResource(R.string.qibla_calibrate)
                abs(rel) <= 5 -> stringResource(R.string.qibla_facing_short)
                abs(rel) < 25 -> stringResource(if (rel > 0) R.string.qibla_go_right_little else R.string.qibla_go_left_little)
                else -> stringResource(if (rel > 0) R.string.qibla_go_right else R.string.qibla_go_left)
            }
        }
    }
}

private fun reducedMotion(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Degrees clockwise from the top of the phone to the Qibla (the north-up bearing without a compass). */
internal fun qiblaRelative(bearing: Double, compass: CompassState): Double =
    (compass as? CompassState.Live)?.let { Angles.relativeToQibla(bearing, it.headingTrue) } ?: bearing

/** [target] followed by the shortest way round, springy unless animations are off. */
@Composable
internal fun turning(target: Double): Float {
    val context = LocalContext.current
    val shown = remember { Animatable(target.toFloat()) }
    val instant = reducedMotion(context)
    LaunchedEffect(target) {
        val t = Angles.shortestTarget(shown.value.toDouble(), target).toFloat()
        if (instant) shown.snapTo(t) else shown.animateTo(t, spring(dampingRatio = 0.75f, stiffness = 140f))
    }
    return shown.value
}

/** The logo's arrow (shared/brand/emblem/options.py ARROW), tip up, centred on [c], [h] tall. */
internal fun DrawScope.drawBrandArrow(c: Offset, h: Float, body: Color, facet: Color) {
    val k = h / 112f
    fun p(x: Float, y: Float) = Offset(c.x + x * k, c.y + (y + 4f) * k)
    val whole = Path().apply {
        p(0f, -60f).let { moveTo(it.x, it.y) }; p(44f, 52f).let { lineTo(it.x, it.y) }
        p(0f, 30f).let { lineTo(it.x, it.y) }; p(-44f, 52f).let { lineTo(it.x, it.y) }; close()
    }
    val lit = Path().apply {
        p(0f, -60f).let { moveTo(it.x, it.y) }; p(44f, 52f).let { lineTo(it.x, it.y) }; p(0f, 30f).let { lineTo(it.x, it.y) }; close()
    }
    drawPath(whole, body)
    drawPath(lit, facet)
}

/**
 * The Qibla ring, after the reference: a thin glass ring with the Kaaba (the logo's cube) fixed at the
 * top and the logo's arrow in the middle pointing to the Qibla. A gold dot on the ring marks the Qibla
 * with a dotted line towards the middle, and a glowing stretch of ring runs from the dot to the Kaaba:
 * how far to turn. Plain words below the arrow; the distance to the Kaaba small along the left side,
 * the nearest mosque along the right (it opens the mosque). No north, no angles. Never mirrors.
 */
@Composable
fun QiblaRing(
    angle: Double, aligned: Boolean, guidance: String, modifier: Modifier = Modifier,
    kaabaDistance: String? = null, nearest: String? = null, onNearest: (() -> Unit)? = null,
    /** Short guidance (with a live compass) curves along the ring; the longer no-compass note stays straight. */
    curveGuidance: Boolean = true,
) {
    val shown = turning(angle)
    val dark = LocalSky.current.dark
    val ink = Ink
    val outer = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
            val density = LocalDensity.current
            val s = with(density) { maxWidth.toPx() }
            val r = s / 2 - with(density) { 34.dp.toPx() }
            val c = Offset(s / 2, s / 2)
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(ink.copy(alpha = if (dark) 0.06f else 0.1f), r, c)
                drawCircle(ink.copy(alpha = 0.45f), r, c, style = Stroke(1.5.dp.toPx()))
                val rel = Angles.normalize180(shown.toDouble()).toFloat()
                val box = Offset(c.x - r, c.y - r)
                val sz = androidx.compose.ui.geometry.Size(2 * r, 2 * r)
                drawArc(Color.White.copy(alpha = 0.35f), -90f, rel, false, box, sz, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Color.White, -90f, rel, false, box, sz, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                val rad = Math.toRadians(shown.toDouble())
                val dot = Offset(c.x + r * sin(rad).toFloat(), c.y - r * cos(rad).toFloat())
                val inner = Offset(c.x + r * 0.55f * sin(rad).toFloat(), c.y - r * 0.55f * cos(rad).toFloat())
                drawLine(ink.copy(alpha = 0.5f), dot, inner, 2.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())))
                drawCircle(Tokens.gold, 9.dp.toPx(), dot)
                drawCircle(Color.White, 9.dp.toPx(), dot, style = Stroke(2.5.dp.toPx()))
                // The logo's arrow, pointing to the Qibla.
                rotate(shown, c) { drawBrandArrow(c, s * 0.24f, if (dark) Color.White else Tokens.navy, Tokens.gold) }
            }
            // The Kaaba, fixed at the top: the phone faces the Qibla when the dot reaches it.
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = with(density) { (c.y - r).toDp() } - 25.dp).size(50.dp)
                    .shadow(if (aligned) 16.dp else 6.dp, CircleShape, spotColor = if (aligned) Tokens.gold else Color.Black)
                    .background(Color.White, CircleShape)
                    .border(if (aligned) 3.dp else 1.5.dp, if (aligned) Tokens.gold else Color.White, CircleShape)
                    .padding(9.dp),
            ) { Image(painterResource(R.drawable.logo_body), contentDescription = null) }
            val accent = Accent
            if (curveGuidance) {
                // Plain words along the inside of the ring, below the arrow.
                val gp = curvedPaint(18.sp, bold = true, color = accent)
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = guidance; liveRegion = LiveRegionMode.Polite }) {
                    drawCurvedText(guidance, gp, c, r * 0.74f, 180f, outside = false)
                }
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides outer) {
                    Text(
                        guidance, color = accent, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = with(density) { (c.y + r * 0.52f).toDp() })
                            .widthIn(max = with(density) { (r * 1.4f).toDp() }).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            val labelR = r + with(density) { 12.dp.toPx() }
            kaabaDistance?.let {
                CurvedSideLabel(it, Ink.copy(alpha = 0.85f), c, labelR, 250f, icon = painterResource(R.drawable.logo_body))
            }
            if (nearest != null && onNearest != null) {
                CurvedSideLabel(nearest, accent, c, labelR, 110f, icon = painterResource(R.drawable.ic_tab_mosque), bold = true, onClick = onNearest)
            }
        }
    }
}

@Composable
internal fun NearestLabel(text: String, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_tab_mosque), contentDescription = null, tint = Accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Accent, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** Warm light falling across the screen from the Qibla's side, turning with the phone; brighter when facing it. */
@Composable
private fun QiblaLightBeam(angle: Double, aligned: Boolean, modifier: Modifier = Modifier) {
    val shown = turning(angle)
    val strength by animateFloatAsState(if (aligned) 1f else 0f, label = "beam")
    Canvas(modifier.blur(24.dp).clearAndSetSemantics {}) {
        val pivot = Offset(size.width / 2, size.height * 0.36f)
        val h = maxOf(size.width, size.height) * 1.6f
        val w = size.width * (0.5f + 0.25f * strength)
        rotate(shown, pivot) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(Tokens.gold.copy(alpha = 0f), Tokens.gold.copy(alpha = 0.3f + 0.2f * strength), Tokens.gold.copy(alpha = 0f)),
                    startX = pivot.x - w / 2, endX = pivot.x + w / 2,
                ),
                topLeft = Offset(pivot.x - w / 2, pivot.y - h), size = androidx.compose.ui.geometry.Size(w, h),
                alpha = 0.9f,
            )
        }
    }
}

/**
 * The full Qibla view (opened by tapping the big header): city and calculation method, the Qibla ring
 * in warm light, then today's date and prayer times. Sensors run only while it is visible.
 */
@Composable
fun QiblaCompassScreen(ui: PrayerUi, compass: CompassState, aligned: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val sky = LocalSky.current
    var mosque by remember { androidx.compose.runtime.mutableStateOf<RankedMosque?>(null) }
    mosque?.let { NearestMosqueSheet(it, onDismiss = { mosque = null }) }
    Box(Modifier.fillMaxSize()) {
        SkyBackdrop(sky, Modifier.fillMaxSize(), horizon = 0.82f)
        val bearing = ui.qiblaBearing
        if (bearing != null) QiblaLightBeam(qiblaRelative(bearing, compass), aligned, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.location?.name ?: stringResource(R.string.qibla), style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.semantics { heading() })
                    ui.settings?.prayer?.method?.let { Text(stringResource(Format.methodName(it)), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.75f)) }
                }
                GlassIconButton(rememberVectorPainter(Icons.Filled.Close), stringResource(R.string.close), onClose, Modifier.align(Alignment.CenterEnd))
            }
            Spacer(Modifier.height(12.dp))
            if (bearing != null) {
                val n = ui.nearestMosque
                QiblaRing(
                    qiblaRelative(bearing, compass), aligned, qiblaGuidance(compass, bearing), Modifier.widthIn(max = 420.dp),
                    kaabaDistance = ui.qiblaDistance?.let { stringResource(R.string.qibla_distance, Format.distance(context, it)) },
                    nearest = n?.let { stringResource(R.string.nearest_mosque_chip, Format.distance(context, it.distanceMeters)) },
                    onNearest = n?.let { { mosque = it } },
                    curveGuidance = compass is CompassState.Live,
                )
                Text(
                    stringResource(R.string.qibla_hold_flat) + " · " + stringResource(R.string.qibla_approximate),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = Ink.copy(alpha = 0.7f),
                )
            } else {
                Text(stringResource(R.string.qibla_location_needed), color = Ink, modifier = Modifier.padding(top = 60.dp))
            }
            ui.location?.let { loc ->
                val date = ui.now.atZone(loc.zoneId).toLocalDate()
                Spacer(Modifier.height(16.dp))
                Text(Format.gregorian(context, date), style = MaterialTheme.typography.titleMedium, color = Ink)
                Format.hijri(context, date, ui.settings?.prayer?.hijriAdjustmentDays ?: 0)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Accent) }
            }
            Spacer(Modifier.height(12.dp))
            ScheduleCard(ui, ui.settings?.reminders.orEmpty(), onToggleReminder = null)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The nearest mosque's details (directions in a maps app, call, website, favourite). */
@Composable
internal fun NearestMosqueSheet(r: RankedMosque, onDismiss: () -> Unit) {
    val c = sa.zood.nearmosque.appContainer()
    val favorites by c.mosques.favorites.collectAsState(initial = emptySet())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val id = r.mosque.sourceId
    sa.zood.nearmosque.ui.mosques.MosqueDetailSheet(
        r, id in favorites,
        onToggleFavorite = { scope.launch { c.mosques.setFavorite(id, id !in favorites) } },
        onDismiss = onDismiss,
    )
}
