package com.blefinder.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.blefinder.android.ui.PermissionScreen
import com.blefinder.android.ui.theme.BleFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BleFinderTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val context = LocalContext.current
                    var hasBluetooth by remember { mutableStateOf(Permissions.hasBluetooth(context)) }
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasBluetooth = Permissions.hasBluetooth(context) }

                    if (!hasBluetooth) {
                        PermissionScreen(onChanged = { hasBluetooth = Permissions.hasBluetooth(context) })
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Devices") }
                    }
                }
            }
        }
    }
}
