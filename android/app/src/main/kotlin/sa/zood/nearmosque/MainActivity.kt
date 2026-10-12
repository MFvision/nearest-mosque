package sa.zood.nearmosque

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.flow.MutableStateFlow
import sa.zood.nearmosque.ui.AppRoot
import sa.zood.nearmosque.ui.theme.NearMosqueTheme

/** AppCompatActivity so the per-app language override also applies on Android 12 and below. */
class MainActivity : AppCompatActivity() {
    /** Where a widget or an app-icon shortcut asked to go: a nearmosque:// link (prayer, qibla, mosques, mosque?id=…, ask?q=…). */
    private val route = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) route.value = routeOf(intent)
        // Status and navigation bar icons follow light and dark mode (dark icons on the light skies).
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            NearMosqueTheme {
                AppRoot(container, route = route)
            }
        }
    }

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

    override fun onResume() {
        super.onResume()
        // Language or time-zone changes while away: redraw the widgets.
        sa.zood.nearmosque.platform.PrayerWidgets.refresh(this)
    }
}
