package sa.zood.nearmosque.ui.mosques

import sa.zood.nearmosque.ui.theme.Accent

import sa.zood.nearmosque.ui.theme.Ink

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.Angles
import sa.zood.nearmosque.core.CompassState
import sa.zood.nearmosque.core.Geo
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.MosqueCategory
import sa.zood.nearmosque.core.OnlineMosques
import sa.zood.nearmosque.core.RankedMosque
import sa.zood.nearmosque.data.MosqueResult
import sa.zood.nearmosque.platform.ExternalActions
import sa.zood.nearmosque.ui.Format
import sa.zood.nearmosque.ui.glass.GlassButton
import sa.zood.nearmosque.ui.glass.GlassButtonText
import sa.zood.nearmosque.ui.glass.GlassCard
import sa.zood.nearmosque.ui.glass.GlassIconButton
import sa.zood.nearmosque.ui.glass.GlassSegmented
import sa.zood.nearmosque.ui.glass.MosquePin
import sa.zood.nearmosque.ui.glass.YouDot
import sa.zood.nearmosque.ui.glass.bottomBarPadding
import sa.zood.nearmosque.ui.glass.glass
import sa.zood.nearmosque.ui.glass.reducedMotion
import sa.zood.nearmosque.ui.theme.DataDirection
import sa.zood.nearmosque.ui.theme.Tokens
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MosquesScreen(vm: MosquesViewModel, compass: CompassState, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lang = Format.languageCode(context)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    var fullMap by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RankedMosque?>(null) }
    // A widget tap opens that mosque's page once it is in the list.
    val requested by vm.requestedId.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(requested, ui.items) {
        val id = requested ?: return@LaunchedEffect
        if (ui.items.isEmpty()) return@LaunchedEffect
        // Opened if it is in the first list shown; otherwise dropped, so it cannot pop up later.
        ui.items.firstOrNull { it.mosque.sourceId == id }?.let { selected = it }
        vm.requestedId.value = null
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.useDevice(lang) }
    val requestLocation = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    LaunchedEffect(Unit) { vm.start(lang) }
    val heading = (compass as? CompassState.Live)?.headingTrue

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                painterResource(if (ui.favoritesOnly) R.drawable.ic_star_filled else R.drawable.ic_star_outline), stringResource(R.string.favorites),
                onClick = { vm.setFavoritesOnly(!ui.favoritesOnly) }, tint = if (ui.favoritesOnly) Accent else Ink,
            )
            Text(
                stringResource(R.string.tab_mosques), Modifier.weight(1f).semantics { heading() }, color = Ink,
                style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            GlassIconButton(rememberVectorPainter(Icons.Filled.Settings), stringResource(R.string.settings), onOpenSettings)
        }
        Spacer(Modifier.height(12.dp))
        val center = ui.center
        if (center != null && !ui.favoritesOnly) {
            GlassSegmented(ui.mode, listOf(stringResource(R.string.view_compass) to R.drawable.ic_compass, stringResource(R.string.view_map) to R.drawable.ic_map), vm::setMode)
            Spacer(Modifier.height(12.dp))
            if (ui.items.isNotEmpty()) {
                Crossfade(ui.mode, label = "mode") { m ->
                    if (m == 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MosqueRadar(ui.items, center, heading, Modifier.fillMaxWidth(0.82f)) { selected = it }
                            guidance(ui.items, center, heading)?.let { g ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    g, color = if (g == stringResource(R.string.mosque_ahead)) Accent else Ink,
                                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                    } else {
                        Box {
                            MosqueMap(
                                ui.items, center, device?.location, onSelect = { selected = it }, onSearchHere = { vm.searchAt(it, lang) },
                                modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(Tokens.cardRadius.dp))
                                    .border(1.dp, Ink.copy(alpha = 0.25f), RoundedCornerShape(Tokens.cardRadius.dp)),
                            )
                            sa.zood.nearmosque.ui.glass.GlassIconButton(
                                androidx.compose.ui.res.painterResource(R.drawable.ic_fullscreen), stringResource(R.string.map_full_screen), { fullMap = true },
                                Modifier.align(Alignment.TopEnd).padding(8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = bottomBarPadding(12.dp).calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("status") { StatusRow(ui, onUseDevice = requestLocation) }
            when {
                ui.favoritesOnly -> items(ui.favoriteMosques, key = { "fav-" + it.sourceId }) { m ->
                    val d = ui.center?.let { Geo.distanceMeters(it, m.location) }
                    MosqueCard(RankedMosque(m, d ?: 0.0), nearest = false, favorite = true, showDistance = d != null) { selected = RankedMosque(m, d ?: 0.0) }
                }
                center == null -> item("need") {
                    NeedLocation(ui.permissionDenied, ui.locating, onAllow = requestLocation, onUseCity = { vm.usePrayerCity(lang) })
                }
                ui.loading && ui.items.isEmpty() -> item("loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink) }
                }
                ui.items.isNotEmpty() -> {
                    itemsIndexed(ui.items.take(40), key = { _, r -> r.mosque.sourceId }) { i, r ->
                        MosqueCard(r, nearest = i == 0, favorite = r.mosque.sourceId in ui.favorites, showDistance = true) { selected = r }
                    }
                    item("attribution") {
                        val online = ui.items.any { it.mosque.packId == OnlineMosques.PACK_ID }
                        Text(
                            stringResource(R.string.data_attribution_osm) + (if (online) " · " + stringResource(R.string.source_online_osm) else "") +
                                (if (ui.mode == 1) " · " + stringResource(R.string.map_attribution_ofm) else ""),
                            color = Ink.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                ui.online != OnlineState.SEARCHING -> item("empty") {
                    when (val r = ui.result) {
                        is MosqueResult.NoRecordsInCoverage -> EmptyState(
                            stringResource(R.string.no_records_title),
                            stringResource(R.string.no_records_body, r.coverage, Format.distance(context, r.radiusMeters)),
                        )
                        is MosqueResult.AreaNotDownloaded -> EmptyState(
                            stringResource(R.string.region_not_downloaded_title),
                            stringResource(R.string.region_not_downloaded_body, r.installed.ifEmpty { listOf(stringResource(R.string.region_none_installed)) }.joinToString()),
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
    val mapCenter = ui.center
    if (fullMap && mapCenter != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullMap = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            var next by remember { mutableStateOf<Pair<String, String>?>(null) }
            LaunchedEffect(Unit) {
                val zone = vm.prayerZone()
                next = vm.nextPrayer()?.let { (e, at) -> context.getString(Format.prayerName(e)) to Format.time(context, at, zone ?: java.time.ZoneId.systemDefault()) }
            }
            FullScreenMosqueMap(ui.items, mapCenter, device?.location, next, onSelect = { selected = it }, onSearchHere = { vm.searchAt(it, lang) }, onClose = { fullMap = false })
        }
    }
    selected?.let { s ->
        MosqueDetailSheet(s, s.mosque.sourceId in ui.favorites, onToggleFavorite = { vm.toggleFavorite(s.mosque.sourceId) }, onDismiss = { selected = null })
    }
}


@Composable
private fun guidance(items: List<RankedMosque>, center: LatLng, heading: Double?): String? {
    val context = LocalContext.current
    val h = heading ?: return null
    val first = items.firstOrNull() ?: return null
    val rel = Angles.normalize180(Geo.initialBearing(center, first.mosque.location) - h)
    return when {
        abs(rel) <= 12 -> stringResource(R.string.mosque_ahead)
        rel > 0 -> stringResource(R.string.qibla_turn_right, Format.degrees(context, abs(rel)))
        else -> stringResource(R.string.qibla_turn_left, Format.degrees(context, abs(rel)))
    }
}

@Composable
private fun StatusRow(ui: MosquesUi, onUseDevice: () -> Unit) {
    if (ui.favoritesOnly) return
    val text = when (val o = ui.origin) {
        SearchOrigin.Device -> stringResource(R.string.searching_from_you)
        is SearchOrigin.PrayerCity -> stringResource(R.string.searching_from_city, o.name)
        SearchOrigin.SelectedPoint -> stringResource(R.string.searching_from_point)
        null -> return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink)
            when (ui.online) {
                OnlineState.SEARCHING -> Text(stringResource(R.string.online_searching), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
                OnlineState.UNAVAILABLE -> Text(stringResource(R.string.online_offline_note), style = MaterialTheme.typography.bodySmall, color = Accent)
                else -> Unit
            }
        }
        if (ui.origin != SearchOrigin.Device) {
            Spacer(Modifier.width(8.dp))
            GlassButton(onClick = onUseDevice) { GlassButtonText(stringResource(R.string.recenter), painterResource(R.drawable.ic_pin)) }
        }
    }
}

@Composable
private fun NeedLocation(denied: Boolean, locating: Boolean, onAllow: () -> Unit, onUseCity: () -> Unit) {
    GlassCard(padding = 20.dp) {
        Text(stringResource(R.string.mosques_need_location_title), style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(if (denied) R.string.location_denied else R.string.mosques_need_location_body), style = MaterialTheme.typography.bodyLarge, color = Ink.copy(alpha = 0.85f))
        Spacer(Modifier.height(14.dp))
        GlassButton(onClick = onAllow, prominent = true, enabled = !locating, modifier = Modifier.fillMaxWidth()) {
            GlassButtonText(stringResource(if (locating) R.string.locating else R.string.allow_location), painterResource(R.drawable.ic_pin))
        }
        Spacer(Modifier.height(10.dp))
        GlassButton(onClick = onUseCity, modifier = Modifier.fillMaxWidth()) { GlassButtonText(stringResource(R.string.choose_city_title)) }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    GlassCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.85f))
    }
}

/** Glass mosque card: name, straight-line distance, one Directions button (gold for the nearest). */
@Composable
private fun MosqueCard(r: RankedMosque, nearest: Boolean, favorite: Boolean, showDistance: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val lang = Format.languageCode(context)
    val m = r.mosque
    val name = m.displayName(lang) ?: stringResource(R.string.mosque_unnamed)
    val distance = Format.distance(context, r.distanceMeters)
    // One sentence for TalkBack: nearest, name and distance, favorite, and where the entry comes from.
    val a11y = listOfNotNull(
        if (nearest) stringResource(R.string.nearest_known_mosque) else null,
        stringResource(R.string.mosque_detail_a11y, name, distance),
        if (favorite) stringResource(R.string.favorite_state) else null,
        when {
            m.packId == OnlineMosques.PACK_ID -> stringResource(R.string.source_online_osm)
            m.category == MosqueCategory.PRAYER_SPACE -> stringResource(R.string.category_prayer_space)
            else -> null
        },
    ).joinToString(". ")
    GlassCard(padding = 14.dp, tint = if (nearest) Tokens.gold.copy(alpha = 0.12f) else null) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).semantics { contentDescription = a11y },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MosquePin(nearest, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (nearest) Text(stringResource(R.string.nearest_known_mosque), color = Accent, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(name, color = Ink, style = MaterialTheme.typography.titleMedium.copy(textDirection = DataDirection), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showDistance) Text(stringResource(R.string.straight_line, distance), color = Ink.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                when {
                    m.packId == OnlineMosques.PACK_ID -> Text(stringResource(R.string.source_online_osm), color = Ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    m.category == MosqueCategory.PRAYER_SPACE -> Text(stringResource(R.string.category_prayer_space), color = Ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (favorite) Icon(painterResource(R.drawable.ic_star_filled), contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
            Icon(rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowRight), contentDescription = null, tint = Ink.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(10.dp))
        GlassButton(onClick = { ExternalActions.directions(context, m.location, name) }, prominent = nearest, modifier = Modifier.fillMaxWidth()) {
            GlassButtonText(stringResource(R.string.get_directions), painterResource(R.drawable.ic_directions))
        }
    }
}

/**
 * Compass view of nearby mosques: you in the centre, each mosque at its true bearing and (square-root
 * scaled) distance. With a live heading the dial turns so the top is where the phone points; without
 * heading it is north-up. Geometry never mirrors.
 */
@Composable
private fun MosqueRadar(items: List<RankedMosque>, center: LatLng, heading: Double?, modifier: Modifier = Modifier, onSelect: (RankedMosque) -> Unit) {
    val reduced = reducedMotion()
    val rotation = remember { Animatable(-(heading ?: 0.0).toFloat()) }
    LaunchedEffect(heading) {
        val target = Angles.shortestTarget(rotation.value.toDouble(), -(heading ?: 0.0)).toFloat()
        if (reduced) rotation.snapTo(target) else rotation.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 180f))
    }
    val shown = items.take(10)
    val maxD = maxOf(300.0, shown.maxOfOrNull { it.distanceMeters } ?: 1000.0)
    val north = stringResource(R.string.compass_north)
    val context = LocalContext.current
    val lang = Format.languageCode(context)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val summary = shown.firstOrNull()?.let { f ->
            stringResource(R.string.radar_a11y, stringResource(R.string.mosque_detail_a11y, f.mosque.displayName(lang) ?: stringResource(R.string.mosque_unnamed), Format.distance(context, f.distanceMeters)))
        }
        BoxWithConstraints(
            modifier.aspectRatio(1f).glass(CircleShape).semantics { if (summary != null) contentDescription = summary },
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val s = with(density) { maxWidth.toPx() }
            val usable = s / 2 - with(density) { 28.dp.toPx() }
            fun pos(r: RankedMosque): Offset {
                val b = Math.toRadians(Geo.initialBearing(center, r.mosque.location) + rotation.value)
                val rad = sqrt(r.distanceMeters / maxD).toFloat() * usable
                return Offset(rad * sin(b).toFloat(), -rad * cos(b).toFloat())
            }
            val ink = Ink
            Canvas(Modifier.fillMaxSize()) {
                val c = this.center
                for (k in 1..2) drawCircle(ink.copy(alpha = 0.16f), size.minDimension / 2 * k / 3f, c, style = Stroke(1.dp.toPx()))
                rotate(rotation.value, c) {
                    for (i in 0 until 36) {
                        val major = i % 9 == 0
                        rotate(i * 10f, c) {
                            drawLine(
                                ink.copy(alpha = if (major) 0.8f else 0.3f), Offset(c.x, 4.dp.toPx()),
                                Offset(c.x, (if (major) 14 else 9).dp.toPx()), (if (major) 2 else 1).dp.toPx(),
                            )
                        }
                    }
                }
                shown.firstOrNull()?.let { first ->
                    val p = pos(first)
                    drawLine(Tokens.gold, c, c + p, 3.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(14f, 14f)))
                }
                if (heading != null) drawLine(ink, Offset(c.x, -2.dp.toPx()), Offset(c.x, 12.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
            }
            val nr = Math.toRadians(rotation.value.toDouble())
            val nDist = s / 2 - with(density) { 26.dp.toPx() }
            Text(
                north, color = Color(0xFFF2B8B5), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.offset { IntOffset((nDist * sin(nr)).roundToInt(), (-nDist * cos(nr)).roundToInt()) }.clearAndSetSemantics {},
            )
            shown.forEachIndexed { i, r ->
                val p = pos(r)
                val label = stringResource(R.string.mosque_detail_a11y, r.mosque.displayName(lang) ?: stringResource(R.string.mosque_unnamed), Format.distance(context, r.distanceMeters))
                Box(
                    Modifier.offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }.size(48.dp)
                        .clickable(role = Role.Button) { onSelect(r) }.semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { MosquePin(i == 0, size = 34.dp) }
            }
            YouDot()
        }
    }
}

/**
 * The map full screen, as on nearmosque.net: a card per mosque along the bottom (nearest first). Swiping the
 * cards flies the map to that mosque; tapping a pin brings its card. Each card has the name, address,
 * distance, an estimated walking time and the next prayer, with Go, Call and Website; tapping it opens
 * the full page.
 */
@Composable
private fun FullScreenMosqueMap(
    items: List<RankedMosque>, center: LatLng, device: LatLng?, next: Pair<String, String>?,
    onSelect: (RankedMosque) -> Unit, onSearchHere: (LatLng) -> Unit, onClose: () -> Unit,
) {
    val shown = items.take(20)
    val pager = androidx.compose.foundation.pager.rememberPagerState { shown.size }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val focus = shown.getOrNull(pager.settledPage)
    Box(Modifier.fillMaxSize()) {
        MosqueMap(
            items, center, device,
            // A tapped pin brings its card.
            onSelect = { r -> shown.indexOf(r).takeIf { it >= 0 }?.let { scope.launch { pager.animateScrollToPage(it) } } ?: onSelect(r) },
            onSearchHere = onSearchHere, modifier = Modifier.fillMaxSize(), focus = focus, bottomInset = 250.dp,
        )
        sa.zood.nearmosque.ui.glass.GlassIconButton(
            androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.Close), stringResource(R.string.close), onClose,
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
        )
        androidx.compose.foundation.pager.HorizontalPager(
            pager,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp,
        ) { i ->
            MosqueMapCard(shown[i], nearest = i == 0, next = next) { onSelect(shown[i]) }
        }
    }
}

/** One mosque on the full-screen map: who, where and how far, the next prayer, and what to do. */
@Composable
internal fun MosqueMapCard(r: RankedMosque, nearest: Boolean, next: Pair<String, String>?, onOpen: () -> Unit) {
    val context = LocalContext.current
    val lang = Format.languageCode(context)
    val m = r.mosque
    val name = m.displayName(lang) ?: stringResource(R.string.mosque_unnamed)
    // Estimated from the straight line (≈): walking within 4 km, driving beyond.
    val road = r.distanceMeters * 1.3
    val walk = road < 4_000
    val minutes = maxOf(1, Math.round(road / (if (walk) 80.0 else 600.0)).toInt())
    val onCard = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), RoundedCornerShape(28.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (nearest) Text(stringResource(R.string.nearest_known_mosque), color = Accent, style = MaterialTheme.typography.labelMedium)
                Text(name, color = onCard, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 2)
                m.address?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = onCard.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            // Opens the full page: a chevron pointing forward (left in right-to-left languages).
            val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
            Icon(painterResource(R.drawable.ic_chevron_down), null, tint = onCard.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp).size(18.dp).rotate(if (rtl) 90f else -90f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MapChip(R.drawable.ic_pin, Format.distance(context, r.distanceMeters), onCard)
            MapChip(if (walk) R.drawable.ic_walk else R.drawable.ic_car, "≈ " + stringResource(R.string.minutes_short, minutes), onCard)
            next?.let { MapChip(R.drawable.ic_timer, "${it.first} ${it.second}", onCard, gold = true) }
        }
        Row(Modifier.fillMaxWidth()) {
            MapAction(R.drawable.ic_directions, stringResource(R.string.directions), prominent = true, enabled = true, Modifier.weight(1f)) {
                ExternalActions.directions(context, m.location, name)
            }
            MapAction(R.drawable.ic_call, stringResource(R.string.call), prominent = false, enabled = m.phone != null, Modifier.weight(1f)) {
                m.phone?.let { ExternalActions.call(context, it) }
            }
            MapAction(R.drawable.ic_globe, stringResource(R.string.website), prominent = false, enabled = m.website != null, Modifier.weight(1f)) {
                m.website?.let { ExternalActions.open(context, it) }
            }
        }
    }
}

@Composable
private fun MapChip(icon: Int, text: String, onCard: Color, gold: Boolean = false) {
    val color = if (gold) Accent else onCard
    Row(
        Modifier.background((if (gold) sa.zood.nearmosque.ui.theme.Tokens.gold else onCard).copy(alpha = if (gold) 0.16f else 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun MapAction(icon: Int, label: String, prominent: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val onCard = MaterialTheme.colorScheme.onSurface
    Column(
        modifier.alpha(if (enabled) 1f else 0.35f).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier.size(50.dp).background(if (prominent) sa.zood.nearmosque.ui.theme.Tokens.gold else onCard.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = if (prominent) Color.White else onCard, modifier = Modifier.size(22.dp))
        }
        Text(label, color = onCard, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
