package com.blefinder.android.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blefinder.android.app
import com.blefinder.android.signal.SignalMath
import com.blefinder.android.ui.components.SignalBars
import com.blefinder.android.ui.components.icon
import com.blefinder.android.ui.components.rememberNowNanos
import com.blefinder.android.ui.components.signalColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onOpen: (address: String) -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val devices by app.bluetooth.known.devices.collectAsStateWithLifecycle()
    val bluetoothOn by app.bluetooth.known.adapterOn.collectAsStateWithLifecycle()
    val signals by app.tracker.states.collectAsStateWithLifecycle()
    val saved by app.saved.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    val now by rememberNowNanos()
    var filter by rememberSaveable { mutableStateOf(DeviceFilter.ALL) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<DeviceRow?>(null) }
    val scope = rememberCoroutineScope()

    // Location services only gate scanning on Android 10–11; re-check whenever the user comes back.
    var locationOff by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        locationOff = Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled == false
    }

    val rows = DeviceRows.build(devices, saved, signals, now, filter)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Finder") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Diagnostics") }, onClick = { menuOpen = false; onDiagnostics() })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onSettings() })
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; app.bluetooth.refresh() })
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeviceFilter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            if (!bluetoothOn) {
                item {
                    Banner(Icons.Outlined.BluetoothDisabled, "Bluetooth is off", "Turn it on to hear your devices.", "Turn on") {
                        runCatching { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                    }
                }
            } else if (locationOff) {
                item {
                    Banner(Icons.Outlined.LocationOff, "Location services are off", "Android 10 and 11 return no scan results without them.", "Open settings") {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                }
            }
            if (bluetoothOn && rows.isEmpty()) {
                item {
                    EmptyState(
                        when {
                            devices.isEmpty() -> "No paired devices. Pair your earbuds, watch or tag in Bluetooth settings first."
                            filter == DeviceFilter.SAVED -> "No saved devices yet. Long-press a device to pin it."
                            else -> "Nothing heard in the last few seconds."
                        },
                        if (devices.isEmpty()) "Bluetooth settings" else null,
                    ) { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }
            }
            items(rows, key = { it.device.address }) { row ->
                DeviceRowItem(
                    row = row,
                    now = now,
                    strength = { SignalMath.strength(it, settings.gaugeCold, settings.gaugeHot) },
                    onOpen = { onOpen(row.device.address) },
                    onPin = { scope.launch { app.savedDevices.setPinned(row.device.address, !row.pinned) } },
                    onRename = { renaming = row },
                )
            }
        }
    }

    renaming?.let { row ->
        RenameDialog(
            current = saved.names[row.device.address].orEmpty(),
            systemName = row.device.name,
            onDismiss = { renaming = null },
            onSave = { name ->
                scope.launch { app.savedDevices.rename(row.device.address, name) }
                renaming = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRowItem(row: DeviceRow, now: Long, strength: (Double) -> Double, onOpen: () -> Unit, onPin: () -> Unit, onRename: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(row.device.kind.icon(), contentDescription = null) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.pinned) {
                        Icon(Icons.Outlined.PushPin, contentDescription = "Saved", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    DeviceRows.subtitle(row, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val s = row.signal
            if (s != null) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.alpha(if (row.fresh) 1f else 0.4f)) {
                    SignalBars(
                        filled = if (row.fresh) SignalMath.bars(s.filtered) else 0,
                        color = signalColor(strength(s.filtered)),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${s.filtered.roundToInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Find") }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text(if (row.pinned) "Unpin" else "Pin") }, onClick = { menu = false; onPin() })
            DropdownMenuItem(text = { Text("Rename…") }, onClick = { menu = false; onRename() })
        }
    }
}

@Composable
private fun RenameDialog(current: String, systemName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text(systemName) },
                supportingText = { Text("Leave empty to use \"$systemName\".") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Banner(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyState(text: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) FilledTonalButton(onClick = onAction) { Text(action) }
    }
}
