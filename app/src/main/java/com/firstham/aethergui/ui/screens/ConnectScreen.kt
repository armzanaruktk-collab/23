package com.firstham.aethergui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.firstham.aethergui.MainViewModel
import com.firstham.aethergui.ui.components.*

@Composable
fun ConnectScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val status by viewModel.connectionStatus.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val engine by viewModel.selectedEngine.collectAsState()
    val mode by viewModel.selectedMode.collectAsState()
    val profiles by viewModel.serverProfiles.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val host by viewModel.hostInput.collectAsState()
    val port by viewModel.portInput.collectAsState()
    val user by viewModel.userInput.collectAsState()
    val pass by viewModel.passInput.collectAsState()

    val payload by viewModel.payloadInput.collectAsState()
    val sni by viewModel.sniInput.collectAsState()
    val proxyHost by viewModel.proxyHostInput.collectAsState()
    val proxyPort by viewModel.proxyPortInput.collectAsState()
    val wsPath by viewModel.wsPathInput.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HeroPowerCard(
            status = status,
            telemetry = telemetry,
            onToggleClick = { viewModel.toggleConnection() }
        )

        EngineSelector(
            selectedEngine = engine,
            onEngineSelect = { viewModel.setEngine(it) }
        )

        ServerConfigCard(
            profiles = profiles,
            selectedProfile = selectedProfile,
            onProfileSelect = { viewModel.selectProfile(it) },
            onSaveServer = { name, h, p, u -> viewModel.saveCustomServer(name, h, p, u) },
            onRemoveServer = { viewModel.removeCustomServer(it) },
            host = host,
            onHostChange = { viewModel.hostInput.value = it },
            port = port,
            onPortChange = { viewModel.portInput.value = it },
            user = user,
            onUserChange = { viewModel.userInput.value = it },
            pass = pass,
            onPassChange = { viewModel.passInput.value = it }
        )

        ModeConfigCard(
            selectedMode = mode,
            onModeSelect = { viewModel.setMode(it) },
            payload = payload,
            onPayloadChange = { viewModel.payloadInput.value = it },
            sni = sni,
            onSniChange = { viewModel.sniInput.value = it },
            proxyHost = proxyHost,
            onProxyHostChange = { viewModel.proxyHostInput.value = it },
            proxyPort = proxyPort,
            onProxyPortChange = { viewModel.proxyPortInput.value = it },
            wsPath = wsPath,
            onWsPathChange = { viewModel.wsPathInput.value = it },
            onSaveSettings = { viewModel.saveModeSettings() }
        )

        TerminalLogCard(
            logs = logs,
            onClearLog = { viewModel.clearLog() }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
