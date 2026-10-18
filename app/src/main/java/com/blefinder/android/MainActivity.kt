package com.blefinder.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.blefinder.android.ui.DeviceListScreen
import com.blefinder.android.ui.DiagnosticsScreen
import com.blefinder.android.ui.FinderScreen
import com.blefinder.android.ui.PermissionScreen
import com.blefinder.android.ui.SettingsScreen
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

                    // Scanning follows visibility: started on ON_START (or as soon as permission
                    // arrives, since the permission dialog only pauses the activity) and stopped on ON_STOP.
                    LifecycleStartEffect(hasBluetooth) {
                        if (hasBluetooth) context.app.bluetooth.start()
                        onStopOrDispose { if (hasBluetooth) context.app.bluetooth.stop() }
                    }

                    if (!hasBluetooth) {
                        PermissionScreen(onChanged = { hasBluetooth = Permissions.hasBluetooth(context) })
                    } else {
                        AppNavHost()
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.DEVICES) {
        composable(Routes.DEVICES) {
            DeviceListScreen(
                onOpen = { address -> nav.navigate(Routes.finder(address)) },
                onDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(
            Routes.FINDER,
            arguments = listOf(navArgument("address") { type = NavType.StringType }),
            // Only reachable in debug builds, whose manifest adds the matching intent filter (used for emulator smoke tests).
            deepLinks = listOf(navDeepLink { uriPattern = "blefinder-debug://finder/{address}" }),
        ) { entry ->
            FinderScreen(address = Uri.decode(entry.arguments?.getString("address").orEmpty()), onBack = { nav.popBackStack() })
        }
    }
}

object Routes {
    const val DEVICES = "devices"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
    const val FINDER = "finder/{address}"

    fun finder(address: String) = "finder/${Uri.encode(address)}"
}
