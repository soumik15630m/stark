package com.soumik.stark.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.ui.theme.StarkTheme
import kotlinx.coroutines.launch

/** Lets the user set the home-screen widget's background transparency when adding or reconfiguring it. */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the user backs out, the widget host should not add the widget.
        setResult(Activity.RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val initial = Prefs.getInt(this, TodayWidget.ALPHA_PREFIX + widgetId, TodayWidget.DEFAULT_ALPHA)

        setContent {
            StarkTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigContent(initial) { confirm(it) }
                }
            }
        }
    }

    private fun confirm(alphaPct: Int) {
        Prefs.setInt(this, TodayWidget.ALPHA_PREFIX + widgetId, alphaPct)
        val mgr = AppWidgetManager.getInstance(this)
        lifecycleScope.launch {
            val text = TodayWidget.kmText(this@WidgetConfigActivity)
            TodayWidget.render(this@WidgetConfigActivity, mgr, widgetId, text)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}

@Composable
private fun ConfigContent(initialAlpha: Int, onConfirm: (Int) -> Unit) {
    var alpha by remember { mutableFloatStateOf(initialAlpha.toFloat()) }
    val pct = alpha.toInt()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Widget transparency", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        WidgetPreview(pct)

        Column {
            Text("Background opacity — $pct%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0f..100f)
        }

        Button(onClick = { onConfirm(pct) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }
}

@Composable
private fun WidgetPreview(alphaPct: Int) {
    // Mirrors widget_today.xml: dark card whose opacity follows the slider, over a neutral backdrop.
    val a = (alphaPct * 255 / 100).coerceIn(0, 255)
    val card = Color((a.toLong() shl 24 or 0x101821L).toInt())
    Box(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF3A3F45)), // stand-in for the home-screen wallpaper
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)).background(card).padding(12.dp),
        ) {
            Text("STARK • TODAY", color = Color(0xFF00E5A8), fontSize = 11.sp, letterSpacing = 1.sp)
            Text("12.4 km", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}
