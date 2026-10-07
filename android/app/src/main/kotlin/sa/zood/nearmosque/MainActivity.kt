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
        if (savedInstanceState == null) route.value = intent?.data?.toString()
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
        route.value = intent.data?.toString()
    }

    override fun onResume() {
        super.onResume()
        // Language or time-zone changes while away: redraw the widgets.
        sa.zood.nearmosque.platform.PrayerWidgets.refresh(this)
    }
}
