package sa.zood.nearmosque.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import sa.zood.nearmosque.core.LatLng
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** "You are here". Never substituted with a default city. */
data class DevicePosition(val location: LatLng, val accuracyMeters: Float?, val timeMillis: Long, val altitudeMeters: Double?)

enum class LocationPermission { GRANTED_PRECISE, GRANTED_APPROXIMATE, DENIED }

/**
 * Foreground location through the platform LocationManager (GNSS works without mobile data;
 * no Google Play services dependency). Requested only at the point of use.
 */
class LocationService(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val executor = Executors.newSingleThreadExecutor()

    fun permission(): LocationPermission = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermission.GRANTED_PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationPermission.GRANTED_APPROXIMATE
        else -> LocationPermission.DENIED
    }

    fun isLocationEnabled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled
        else manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** A recent last-known fix, if any (age is reported to the user, never hidden). */
    @SuppressLint("MissingPermission")
    fun lastKnown(maxAgeMillis: Long = 10 * 60_000L): DevicePosition? {
        if (permission() == LocationPermission.DENIED) return null
        // Age by the time since boot, which a wrong phone clock doesn't change.
        val now = SystemClock.elapsedRealtimeNanos()
        return providers().mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { (now - it.elapsedRealtimeNanos) / 1_000_000 <= maxAgeMillis }
            .minByOrNull { it.accuracy }
            ?.toPosition()
    }

    /**
     * One fresh fix (or one from the last 30 seconds, at once), or null on timeout/no provider. Returns as soon as a fix
     * is within [goodEnoughMeters]; otherwise the most accurate one when every provider has answered or time is up.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentPosition(timeoutMillis: Long = 20_000, goodEnoughMeters: Float = Float.MAX_VALUE): DevicePosition? {
        if (permission() == LocationPermission.DENIED) return null
        fun accuracy(p: DevicePosition) = p.accuracyMeters ?: Float.MAX_VALUE
        lastKnown(30_000L)?.takeIf { accuracy(it) <= goodEnoughMeters }?.let { return it }
        // Every provider at once: GPS alone often finds nothing indoors, and a city-level fix is enough for prayer times.
        val asked = providers() - LocationManager.PASSIVE_PROVIDER
        if (asked.isEmpty()) return null
        // The most accurate fix so far, written on the executor or main thread and read here after a timeout.
        val best = AtomicReference<DevicePosition?>(null)
        fun keep(p: DevicePosition) { if (best.get().let { it == null || accuracy(p) < accuracy(it) }) best.set(p) }
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val signals = asked.map { CancellationSignal() }
                    cont.invokeOnCancellation { signals.forEach { it.cancel() } }
                    // Answers come one at a time on the executor; a provider without a fix answers null.
                    var waiting = asked.size
                    asked.forEachIndexed { i, provider ->
                        // A fix that already came back cancelled the rest, and asking with a cancelled signal throws.
                        if (signals[i].isCanceled) return@forEachIndexed
                        val asking = runCatching {
                            manager.getCurrentLocation(provider, signals[i], executor) { loc ->
                                val pos = loc?.toPosition()
                                waiting--
                                if (!cont.isActive) return@getCurrentLocation
                                if (pos != null && accuracy(pos) <= goodEnoughMeters) {
                                    signals.forEach { it.cancel() }
                                    cont.resume(pos)
                                } else {
                                    if (pos != null) keep(pos)
                                    if (waiting == 0) cont.resume(best.get())
                                }
                            }
                        }
                        // A provider that couldn't be asked counts as answered (on the executor, like the answers).
                        if (asking.isFailure) executor.execute {
                            waiting--
                            if (waiting == 0 && cont.isActive) cont.resume(best.get())
                        }
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val pos = location.toPosition() ?: return
                            if (accuracy(pos) > goodEnoughMeters) { keep(pos); return }
                            manager.removeUpdates(this)
                            if (cont.isActive) cont.resume(pos)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                    asked.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
                }
            }
        } ?: best.get()
    }

    /** The phone's location switch (Settings → Location), when someone asked for their location with it off. */
    fun openLocationSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun providers(): List<String> {
        val enabled = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val order = buildList {
            if (permission() == LocationPermission.GRANTED_PRECISE) add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return order.filter { it in enabled }
    }

    private fun Location.toPosition(): DevicePosition? = LatLng.orNull(latitude, longitude)?.let {
        DevicePosition(it, if (hasAccuracy()) accuracy else null, time, if (hasAltitude()) altitude else null)
    }
}
