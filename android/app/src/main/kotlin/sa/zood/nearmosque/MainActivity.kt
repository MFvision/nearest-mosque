package sa.zood.nearmosque

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sa.zood.nearmosque.ui.AppRoot
import sa.zood.nearmosque.ui.theme.NearMosqueTheme

/** AppCompatActivity so the per-app language override also applies on Android 12 and below. */
class MainActivity : AppCompatActivity() {
    /** Where a widget or an app-icon shortcut asked to go: a nearmosque:// link (prayer, qibla, mosques, mosque?id=…, ask?q=…). */
    private val route = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) route.value = routeOf(intent)
        edgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NearMosqueTheme {
                AppRoot(container, route = route)
            }
        }
    }

    /**
     * Light or Dark picked in the app: the activity handles uiMode itself (so the phone's own switch doesn't restart it
     * and close open readers), and AppCompat only updates the resources; pass them on so Compose redraws in the new mode.
     */
    override fun onNightModeChanged(mode: Int) {
        super.onNightModeChanged(mode)
        window.decorView.dispatchConfigurationChanged(resources.configuration)
        // The bar icons and the window behind Compose were set for the old mode.
        edgeToEdge()
        window.setBackgroundDrawableResource(R.color.window_background)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // The phone's own light/dark switch (handled here, not by a restart) needs the bar icons redone as well.
        edgeToEdge()
    }

    /** Status and navigation bar icons follow light and dark mode (dark icons on the light skies). */
    private fun edgeToEdge() = enableEdgeToEdge(
        statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
    )

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        route.value = routeOf(intent)
    }

    /**
     * Only nearmosque:// links. A question (ask?q=…) runs at once only from the app's own widgets (they carry this
     * install's private token); from anywhere else it is only put in the box, for the user to send or not.
     */
    private fun routeOf(intent: android.content.Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "nearmosque") return null
        if (uri.host != "ask" || uri.getQueryParameter("q") == null) return uri.toString()
        val q = uri.getQueryParameter("q").orEmpty().take(500)
        val trusted = intent.getStringExtra(sa.zood.nearmosque.platform.LinkToken.EXTRA) == sa.zood.nearmosque.platform.LinkToken.get(this)
        return "nearmosque://ask?" + (if (trusted) "q=" else "draft=") + android.net.Uri.encode(q)
    }

    override fun onStart() {
        super.onStart()
        // "Use my location": back on the screen after a trip, the place follows (at most every 30 minutes).
        val here = getString(R.string.location_current)
        container.scope.launch { runCatching { container.refreshFollowedPlace(here) } }
    }

    override fun onResume() {
        super.onResume()
        // Back from the system settings (notifications, exact alarms allowed), a force stop, a language or time-zone
        // change while away: the alarms, the silences and the widgets are rebuilt.
        sa.zood.nearmosque.platform.RescheduleReceiver.rescheduleAsync(this, null)
    }
}
