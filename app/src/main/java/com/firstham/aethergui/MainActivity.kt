package com.firstham.aethergui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firstham.aethergui.ui.components.FoxyTopBar
import com.firstham.aethergui.ui.screens.*
import com.firstham.aethergui.ui.theme.*

enum class AppDestination(val label: String, val icon: ImageVector) {
    CONNECT("Connect", Icons.Default.Bolt),
    CONFIGURATIONS("Configurations", Icons.Default.Tune),
    SETTINGS("Settings", Icons.Default.Settings),
    ABOUT("About", Icons.Default.Info)
}

class MainActivity : ComponentActivity() {

    private var vpnReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FoxyVPNTheme {
                val viewModel: MainViewModel = viewModel()
                var currentDestination by remember { mutableStateOf(AppDestination.CONNECT) }

                DisposableEffect(Unit) {
                    val filter = IntentFilter().apply {
                        addAction(AetherVpnService.ACTION_STATUS)
                        addAction(AetherVpnService.ACTION_STATS)
                    }
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            when (intent?.action) {
                                AetherVpnService.ACTION_STATUS -> {
                                    val state = intent.getStringExtra("state") ?: ""
                                    val message = intent.getStringExtra("message") ?: state
                                    viewModel.appendLog("Service status: $message")
                                }
                                AetherVpnService.ACTION_STATS -> {
                                    val tx = intent.getLongExtra("uploadBytes", 0L)
                                    val rx = intent.getLongExtra("downloadBytes", 0L)
                                    val ping = intent.getIntExtra("ping", 32)
                                    // Live telemetry is also updated from service broadcast
                                }
                            }
                        }
                    }
                    vpnReceiver = receiver
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        registerReceiver(receiver, filter)
                    }
                    onDispose {
                        try { unregisterReceiver(receiver) } catch (_: Exception) {}
                    }
                }

                // Android guidelines requirement: BackHandler inside sub-screens to return to Connect/Home
                BackHandler(enabled = currentDestination != AppDestination.CONNECT) {
                    currentDestination = AppDestination.CONNECT
                }

                Scaffold(
                    topBar = {
                        FoxyTopBar()
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = SurfaceDark,
                            tonalElevation = 4.dp,
                            modifier = Modifier.testTag("bottom_navigation_bar")
                        ) {
                            AppDestination.entries.forEach { destination ->
                                val selected = destination == currentDestination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.label
                                        )
                                    },
                                    label = { Text(destination.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BgDark,
                                        selectedTextColor = MintGreen,
                                        indicatorColor = MintGreen,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_${destination.name.lowercase()}")
                                )
                            }
                        }
                    },
                    containerColor = BgDark
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentDestination) {
                            AppDestination.CONNECT -> ConnectScreen(viewModel = viewModel)
                            AppDestination.CONFIGURATIONS -> ConfigurationsScreen(viewModel = viewModel)
                            AppDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            AppDestination.ABOUT -> AboutScreen()
                        }
                    }
                }
            }
        }
    }
}
