package sa.zood.nearmosque.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import sa.zood.nearmosque.R
import sa.zood.nearmosque.core.LatLng
import java.util.Locale

/** Hand-offs to other apps. Their behaviour (and connectivity needs) is outside this app's guarantee. */
object ExternalActions {
    /**
     * Directions: always asks which maps app to use (Google Maps, Waze or any other installed app that
     * handles geo: links), even when one is set as the default. Falls back to Google Maps on the web.
     */
    fun directions(context: Context, to: LatLng, label: String?) {
        val lat = String.format(Locale.ROOT, "%.6f", to.latitude)
        val lng = String.format(Locale.ROOT, "%.6f", to.longitude)
        val q = Uri.encode("$lat,$lng" + (label?.let { "($it)" } ?: ""))
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$q"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking"))
        val handlers = context.packageManager.queryIntentActivities(geo, 0)
        if (handlers.isEmpty()) { start(context, web); return }
        start(context, Intent.createChooser(geo, context.getString(R.string.directions_choose)))
    }

    fun call(context: Context, phone: String) {
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })))
    }

    fun open(context: Context, url: String) {
        val u = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
