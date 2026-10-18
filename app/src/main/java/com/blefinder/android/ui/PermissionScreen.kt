package com.blefinder.android.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.blefinder.android.Permissions

/**
 * Shown until the Bluetooth permissions are granted. Motion is asked for in the same request but
 * is optional: refusing it only disables the map.
 */
@Composable
fun PermissionScreen(onChanged: () -> Unit) {
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        onChanged()
    }
    val bluetooth = Permissions.bluetooth()
    // After one refusal Android stops showing the dialog for a permission the user denied twice;
    // the only way forward then is the app's settings page.
    val blocked = asked && bluetooth.any { p ->
        !Permissions.granted(context, p) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, p)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("BLE Finder", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Finds your own paired Bluetooth devices by their signal strength. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionCard(
            icon = Icons.Outlined.Bluetooth,
            title = "Nearby devices (required)",
            body = if (bluetooth.size == 2) {
                "Lets the app list your paired devices and hear their Bluetooth signal."
            } else {
                "Android 10 and 11 file Bluetooth scanning under location. The app does not use your location; it only listens for your paired devices."
            },
            granted = Permissions.hasBluetooth(context),
        )
        PermissionCard(
            icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
            title = "Physical activity (optional)",
            body = "Lets the step sensor draw the 2D signal map as you walk. Without it the gauge and vibration still work.",
            granted = Permissions.hasMotion(context),
        )
        Spacer(Modifier.height(8.dp))
        if (blocked) {
            Text(
                "Android will not ask again. Allow \"Nearby devices\" in the app's settings.",
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open app settings") }
        } else {
            Button(
                onClick = { launcher.launch((bluetooth + Permissions.MOTION).toTypedArray()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow") }
        }
        if (Permissions.hasBluetooth(context)) {
            OutlinedButton(onClick = onChanged, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, body: String, granted: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}
