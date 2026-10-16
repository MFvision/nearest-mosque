package sa.zood.nearmosque.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sa.zood.nearmosque.core.LatLng
import sa.zood.nearmosque.core.Mosque
import sa.zood.nearmosque.core.MosqueRanking
import sa.zood.nearmosque.core.MosqueRecord
import sa.zood.nearmosque.core.OnlineMosques
import sa.zood.nearmosque.core.PackJson
import sa.zood.nearmosque.core.RankedMosque
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Mosques near the chosen city: the mosque data that comes with the app (Cairo, London, Cape Town) and, when
 * "Search online for mosques" is on, OpenStreetMap through the Overpass API. The PC has no GPS, so the search
 * is around the city's centre, rounded to about 1 km before it is sent; nothing else goes with it.
 */
object MosqueFinder {
    private const val RADIUS = 25_000.0
    private const val ONLINE_RADIUS = 10_000.0
    private val packs = listOf("eg-cairo", "gb-london", "za-cape-town")

    private val offline: List<Mosque> by lazy {
        packs.flatMap { pack ->
            Strings.resource("mosques/$pack.jsonl").lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
                runCatching { PackJson.decodeFromString(MosqueRecord.serializer(), line) }.getOrNull()?.toMosque("mosques.$pack")
            }.toList()
        }
    }

    data class Result(val mosques: List<RankedMosque>, val online: Boolean, val offlineOnly: Boolean)

    suspend fun near(center: LatLng, searchOnline: Boolean): Result = withContext(Dispatchers.IO) {
        val local = MosqueRanking.rank(center, offline, RADIUS)
        if (!searchOnline) return@withContext Result(local, online = false, offlineOnly = false)
        val live = overpass(OnlineMosques.privacyRound(center))
        if (live == null) Result(local, online = false, offlineOnly = true)
        else Result(OnlineMosques.merge(center, local, live, RADIUS), online = true, offlineOnly = false)
    }

    private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }

    private fun overpass(center: LatLng): List<Mosque>? = runCatching {
        val body = "data=" + URLEncoder.encode(OnlineMosques.overpassQuery(center, ONLINE_RADIUS), "UTF-8")
        val req = HttpRequest.newBuilder(URI("https://overpass-api.de/api/interpreter"))
            .timeout(Duration.ofSeconds(25))
            .header("User-Agent", "NearMosque/0.5 (${if (Platform.isLinux) "Linux" else "Windows"})")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) null else OnlineMosques.parseOverpass(res.body())
    }.getOrNull()

    /** Walking directions in the browser (only the mosque's position is in the link). */
    fun directionsUrl(m: Mosque) = "https://www.google.com/maps/dir/?api=1&destination=${m.location.latitude},${m.location.longitude}&travelmode=walking"
}
