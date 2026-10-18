package com.blefinder.android.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MobileOff
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blefinder.android.app
import com.blefinder.android.bluetooth.GattState
import com.blefinder.android.bluetooth.LinkType
import com.blefinder.android.map.MapSession
import com.blefinder.android.motion.MotionAvailability
import com.blefinder.android.signal.SignalMath
import com.blefinder.android.ui.components.Gauge
import com.blefinder.android.ui.components.rememberNowNanos
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderScreen(address: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val repo = app.bluetooth
    val devices by repo.known.devices.collectAsStateWithLifecycle()
    val signals by app.tracker.states.collectAsStateWithLifecycle()
    val saved by app.saved.collectAsStateWithLifecycle()
    val gattState by repo.gatt.state.collectAsStateWithLifecycle()
    val classicRunning by repo.classic.running.collectAsStateWithLifecycle()
    val now by rememberNowNanos(250)

    val device = devices.firstOrNull { it.address == address }
    val name = saved.nameFor(address, device?.name ?: address)
    val settings by app.settings.collectAsStateWithLifecycle()
    val signal = signals[address]
    val fresh = signal?.isFresh(now) == true
    val strength = signal?.let { SignalMath.strength(it.filtered, settings.gaugeCold, settings.gaugeHot) } ?: 0.0

    var vibrate by rememberSaveable { mutableStateOf(app.settings.value.vibrateByDefault) }
    val session: MapSession = viewModel(key = "map-$address") { MapSession(address, app.tracker, app.motion) }
    val frame by session.frame.collectAsStateWithLifecycle()
    var motion by remember { mutableStateOf(app.motion.availability()) }
    LifecycleStartEffect(motion) {
        if (motion == MotionAvailability.OK) app.motion.start()
        onStopOrDispose { app.motion.stop() }
    }
    val openedAt = remember { now }

    DisposableEffect(address) {
        repo.setTarget(address)
        onDispose { repo.setTarget(null) }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    VibrationLoop(enabled = vibrate, fresh = fresh, strength = strength)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = session::reset) { Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset map") }
                    IconToggleButton(checked = vibrate, onCheckedChange = { vibrate = it }) {
                        Icon(
                            if (vibrate) Icons.Outlined.Vibration else Icons.Outlined.MobileOff,
                            contentDescription = if (vibrate) "Vibration on" else "Vibration off",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Gauge(
                strength = strength,
                label = signal?.filtered?.roundToInt()?.toString() ?: "—",
                sublabel = if (signal != null) "dBm · ${SignalMath.formatDistance(signal.distanceMetres)}" else "dBm",
                active = fresh,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            Text(
                when {
                    signal == null -> "Waiting for a signal…"
                    fresh -> "${signal.lastSource.label()} · ${SignalMath.formatAge(signal.ageSeconds(now))}"
                    else -> "Signal lost · last heard ${SignalMath.formatAge(signal.ageSeconds(now))}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SourceStatus(gattState, classicRunning)

            val waitedSeconds = (now - openedAt) / 1e9
            if (device?.classicAudioOnly == true && !fresh) {
                Notice(
                    "Connected for audio",
                    "Android gives no signal reading for a classic audio link. Disconnect it in Bluetooth settings so it starts advertising, or try a classic search.",
                    "Bluetooth settings",
                ) { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }
            val offerClassic = device?.type == LinkType.CLASSIC || (!fresh && waitedSeconds > 10)
            if (offerClassic || classicRunning) {
                OutlinedButton(onClick = { if (classicRunning) repo.stopClassicSearch() else repo.startClassicSearch() }) {
                    Text(if (classicRunning) "Stop classic search" else "Classic search (slow, ~12 s per reading)")
                }
            }

            FinderMapArea(
                frame = frame,
                availability = motion,
                onMotionGranted = { motion = app.motion.availability() },
                modifier = Modifier.weight(1f),
            )
            Text(
                "Hold the phone in front of you and turn slowly — your body alone can cut the signal a lot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun SourceStatus(gatt: GattState, classicRunning: Boolean) {
    val text = when {
        classicRunning -> "Classic search running · BLE paused"
        gatt == GattState.CONNECTING -> "No adverts heard · connecting over GATT…"
        gatt == GattState.RETRYING -> "No adverts heard · GATT retrying…"
        gatt == GattState.CONNECTED -> "Reading signal over GATT"
        else -> null
    }
    if (text != null) Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Notice(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

/** Pulses faster as the signal gets stronger; silent when the signal is lost or the app is not resumed. */
@Composable
private fun VibrationLoop(enabled: Boolean, fresh: Boolean, strength: Double) {
    val context = LocalContext.current
    val vibrator = remember { vibrator(context) }
    val currentFresh by rememberUpdatedState(fresh)
    val currentStrength by rememberUpdatedState(strength)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(enabled) {
        if (!enabled || vibrator == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (currentFresh) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                    delay(SignalMath.vibrationIntervalMs(currentStrength))
                } else {
                    delay(500)
                }
            }
        }
    }
}

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }?.takeIf { it.hasVibrator() }
