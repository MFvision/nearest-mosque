package sa.zood.nearmosque.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sa.zood.nearmosque.R
import sa.zood.nearmosque.platform.PrayerWidgets
import sa.zood.nearmosque.platform.WidgetStyle
import sa.zood.nearmosque.ui.theme.NearMosqueTheme

/** Shown when a prayer widget is added (or reconfigured): pick its look. */
class WidgetStyleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val current = PrayerWidgets.style(this, id)
        setContent {
            NearMosqueTheme {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.widget_style_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    WidgetStyle.entries.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .border(if (s == current) 2.dp else 1.dp, if (s == current) Color(0xFFD4A843) else Color(0x33888888), RoundedCornerShape(18.dp))
                                .clickable(role = Role.Button) { choose(id, s) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val (a, b) = when (s) {
                                WidgetStyle.CREAM -> Color(0xFFFFF8EC) to Color(0xFFF3E3C3)
                                WidgetStyle.GREEN -> Color(0xFF0F3D2E) to Color(0xFF1D6B47)
                                WidgetStyle.NIGHT -> Color(0xFF081B29) to Color(0xFF1A4D6E)
                            }
                            Box(Modifier.size(48.dp).background(Brush.verticalGradient(listOf(a, b)), RoundedCornerShape(12.dp)))
                            Spacer(Modifier.width(14.dp))
                            Text(stringResource(s.label), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    private fun choose(id: Int, s: WidgetStyle) {
        PrayerWidgets.setStyle(this, id, s)
        PrayerWidgets.refresh(this)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
