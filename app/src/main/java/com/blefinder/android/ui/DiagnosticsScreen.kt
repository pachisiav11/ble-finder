package com.blefinder.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blefinder.android.BuildConfig
import com.blefinder.android.Permissions
import com.blefinder.android.app
import com.blefinder.android.bluetooth.KnownDevice
import com.blefinder.android.bluetooth.ScanStatus
import com.blefinder.android.bluetooth.Source
import com.blefinder.android.bluetooth.SourceTest
import com.blefinder.android.bluetooth.SourceTestResult
import com.blefinder.android.diagnostics.DiagnosticsInput
import com.blefinder.android.diagnostics.DiagnosticsReport
import com.blefinder.android.signal.SignalMath.formatAge
import com.blefinder.android.signal.SignalState
import com.blefinder.android.ui.components.icon
import com.blefinder.android.ui.components.rememberNowNanos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val repo = context.app.bluetooth
    val devices by repo.known.devices.collectAsStateWithLifecycle()
    val bluetoothOn by repo.known.adapterOn.collectAsStateWithLifecycle()
    val signals by context.app.tracker.states.collectAsStateWithLifecycle()
    val unmatched by repo.unmatched.collectAsStateWithLifecycle()
    val scan by repo.scanStatus.collectAsStateWithLifecycle()
    val gattState by repo.gatt.state.collectAsStateWithLifecycle()
    val classicRunning by repo.classic.running.collectAsStateWithLifecycle()
    val test by repo.test.collectAsStateWithLifecycle()
    val testResults by repo.testResults.collectAsStateWithLifecycle()
    val now by rememberNowNanos()
    val sensors = remember { sensorList(context) }

    fun input() = DiagnosticsInput(
        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        phone = "${Build.MANUFACTURER} ${Build.MODEL}",
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        bluetoothOn = bluetoothOn,
        locationOn = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
        } else {
            null
        },
        permissions = (Permissions.bluetooth() + Permissions.MOTION).map { it to Permissions.granted(context, it) },
        sensors = sensors,
        scanStatus = scan.label(),
        gattState = gattState.name,
        gattLastError = repo.gatt.lastError,
        classicRunning = classicRunning,
        devices = devices,
        signals = signals,
        tests = testResults,
        unmatched = unmatched.values,
        nowNanos = now,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = {
                    TextButton(onClick = { copy(context, DiagnosticsReport.build(input())) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Copy report")
                    }
                },
            )
        },
    ) { padding ->
        val matches = DiagnosticsReport.nameMatches(devices, unmatched.values)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard {
                    val i = input()
                    Line("Phone", "${i.phone}, ${i.android}")
                    Line("Bluetooth", if (bluetoothOn) "on" else "off")
                    i.locationOn?.let { Line("Location services", if (it) "on" else "off — scans return nothing") }
                    Line("Permissions", i.permissions.joinToString { (p, g) -> "${p.substringAfterLast('.')} ${if (g) "✓" else "✗"}" })
                    Line("Sensors", sensors.joinToString { (s, g) -> "$s ${if (g) "✓" else "✗"}" })
                    Line("BLE scan", i.scanStatus)
                    Line("GATT", gattState.name + if (repo.gatt.lastError != 0) " (last error ${repo.gatt.lastError})" else "")
                    Line("Classic inquiry", if (classicRunning) "running" else "off")
                }
            }
            item {
                Text(
                    "Known devices (${devices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Run \"Test all sources\" on each device you want to find, then tap Copy report and include the text when you report a problem.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(devices, key = { it.address }) { d ->
                DeviceDiagnostics(d, signals[d.address], now, test, testResults[d.address], onTest = { repo.testSources(d.address) })
            }
            item {
                Text(
                    "Known names from a different address (${matches.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    if (matches.isEmpty()) {
                        "None — good. Each known device is heard at its own address."
                    } else {
                        "These adverts use a known device's name but another address. If one is yours, the app needs the name-matching fallback."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(matches, key = { "u" + it.address }) { u ->
                InfoCard {
                    Line(u.name, "${u.address} · ${u.rssi} dBm · ${formatAge((now - u.timeNanos) / 1e9)}")
                }
            }
        }
    }
}

@Composable
private fun DeviceDiagnostics(d: KnownDevice, s: SignalState?, now: Long, test: SourceTest?, result: SourceTestResult?, onTest: () -> Unit) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(d.kind.icon(), contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleSmall)
                Text(d.address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        d.ownName?.let { Line("Own name", it) }
        Line("Type", "${d.type} · ${d.kind} · ${if (d.bonded) "bonded" else "not bonded"}")
        Line("Connected", if (d.connected.isEmpty()) "no" else d.connected.joinToString(" + "))
        if (s == null) {
            Line("Signal", "never heard")
        } else {
            Line("Signal", "last ${s.last} dBm · filtered %.1f · %s via %s".format(s.filtered, formatAge(s.ageSeconds(now)), s.lastSource))
            Line("Samples", Source.entries.joinToString(" · ") { "$it ${s.countBySource[it] ?: 0}" })
        }
        result?.let { Line("Last test", DiagnosticsReport.testSummary(it)) }
        val running = test?.takeIf { it.address == d.address }
        when {
            running != null && running.step != SourceTest.Step.DONE -> {
                Text("Testing ${running.step}…", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            else -> FilledTonalButton(onClick = onTest, enabled = test == null || test.step == SourceTest.Step.DONE) {
                Text(if (running?.step == SourceTest.Step.DONE) "Test again" else "Test all sources")
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.65f))
    }
}

private fun ScanStatus.label(): String = when (this) {
    ScanStatus.Idle -> "idle"
    ScanStatus.Scanning -> "scanning"
    ScanStatus.Throttled -> "waiting (Android scan limit)"
    ScanStatus.BluetoothOff -> "Bluetooth off"
    is ScanStatus.Failed -> "failed (code $code)"
}

private fun sensorList(context: Context): List<Pair<String, Boolean>> {
    val sm = context.getSystemService(SensorManager::class.java)
    fun has(type: Int) = sm?.getDefaultSensor(type) != null
    return listOf(
        "step detector" to has(Sensor.TYPE_STEP_DETECTOR),
        "game rotation" to has(Sensor.TYPE_GAME_ROTATION_VECTOR),
        "rotation" to has(Sensor.TYPE_ROTATION_VECTOR),
    )
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("BLE Finder diagnostics", text))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
}
