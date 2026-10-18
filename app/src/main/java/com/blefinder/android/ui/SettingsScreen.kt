package com.blefinder.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blefinder.android.BuildConfig
import com.blefinder.android.app
import com.blefinder.android.data.AppSettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val REPO_URL = "https://github.com/pachisiav11/ble-finder"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val settings by app.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun save(s: AppSettings) = scope.launch { app.settingsStore.save(s) }

    // Sliders keep a local value while dragging and write once on release.
    var step by remember { mutableFloatStateOf(settings.stepLength.toFloat()) }
    var range by remember { mutableStateOf(settings.gaugeCold.toFloat()..settings.gaugeHot.toFloat()) }
    LaunchedEffect(settings) {
        step = settings.stepLength.toFloat()
        range = settings.gaugeCold.toFloat()..settings.gaugeHot.toFloat()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Step length", "%.2f m".format(step)) {
                Slider(
                    value = step,
                    onValueChange = { step = (it * 20).roundToInt() / 20f },
                    onValueChangeFinished = { save(settings.copy(stepLength = step.toDouble())) },
                    valueRange = AppSettings.MIN_STEP.toFloat()..AppSettings.MAX_STEP.toFloat(),
                    steps = 11,
                )
                Hint("Walk 10 normal steps, measure the distance, divide by 10. The map's scale depends on it.")
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vibrate when the finder opens", style = MaterialTheme.typography.titleSmall)
                    Hint("You can still switch it per search from the finder's top bar.")
                }
                Switch(checked = settings.vibrateByDefault, onCheckedChange = { save(settings.copy(vibrateByDefault = it)) })
            }
            HorizontalDivider()
            Section("Gauge range", "cold ${range.start.roundToInt()} dBm · hot ${range.endInclusive.roundToInt()} dBm") {
                RangeSlider(
                    value = range,
                    onValueChange = { r ->
                        val cold = r.start.roundToInt()
                        val hot = r.endInclusive.roundToInt()
                        if (hot - cold >= AppSettings.MIN_GAUGE_SPAN) range = cold.toFloat()..hot.toFloat()
                    },
                    onValueChangeFinished = {
                        save(settings.copy(gaugeCold = range.start.roundToInt(), gaugeHot = range.endInclusive.roundToInt()))
                    },
                    valueRange = AppSettings.GAUGE_FLOOR.toFloat()..AppSettings.GAUGE_CEILING.toFloat(),
                )
                Hint("Signals at or below \"cold\" leave the gauge empty; at or above \"hot\" fill it. Also sets the colour of the list's bars.")
            }
            HorizontalDivider()
            OutlinedButton(onClick = { save(AppSettings()) }) { Text("Reset to defaults") }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("BLE Finder ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Hint("Everything stays on this phone. MIT licence.")
                TextButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Source code on GitHub") }
            }
        }
    }
}

@Composable
private fun Section(title: String, value: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
