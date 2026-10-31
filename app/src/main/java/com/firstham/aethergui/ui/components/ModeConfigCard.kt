package com.firstham.aethergui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstham.aethergui.ConnectionMode
import com.firstham.aethergui.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeConfigCard(
    selectedMode: ConnectionMode,
    onModeSelect: (ConnectionMode) -> Unit,
    payload: String,
    onPayloadChange: (String) -> Unit,
    sni: String,
    onSniChange: (String) -> Unit,
    proxyHost: String,
    onProxyHostChange: (String) -> Unit,
    proxyPort: String,
    onProxyPortChange: (String) -> Unit,
    wsPath: String,
    onWsPathChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modeDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "CONNECTION MODE",
            color = MintGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select a mode and configure parameters",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                // Mode Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = modeDropdownExpanded,
                    onExpandedChange = { modeDropdownExpanded = !modeDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMode.title,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("mode_picker_dropdown"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardDark,
                            unfocusedContainerColor = CardDark,
                            focusedBorderColor = MintGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = modeDropdownExpanded,
                        onDismissRequest = { modeDropdownExpanded = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        ConnectionMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.title, color = TextPrimary) },
                                onClick = {
                                    onModeSelect(mode)
                                    modeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Mode-specific configuration fields
                when (selectedMode) {
                    ConnectionMode.HTTP_PAYLOAD, ConnectionMode.PAYLOAD_SNI, ConnectionMode.PAYLOAD_HTTP_PROXY, ConnectionMode.SSH_WEBSOCKET -> {
                        OutlinedTextField(
                            value = payload,
                            onValueChange = onPayloadChange,
                            label = { Text("HTTP Payload", color = TextSecondary) },
                            placeholder = { Text("CONNECT [host_port] [protocol][crlf][crlf]", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_payload"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardDark,
                                unfocusedContainerColor = CardDark,
                                focusedBorderColor = MintGreen,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    else -> {}
                }

                when (selectedMode) {
                    ConnectionMode.SSL_TLS_SNI, ConnectionMode.PAYLOAD_SNI -> {
                        OutlinedTextField(
                            value = sni,
                            onValueChange = onSniChange,
                            label = { Text("SNI / TLS Host", color = TextSecondary) },
                            placeholder = { Text("e.g. tls.example.com", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_sni"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardDark,
                                unfocusedContainerColor = CardDark,
                                focusedBorderColor = MintGreen,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    else -> {}
                }

                when (selectedMode) {
                    ConnectionMode.HTTP_PROXY, ConnectionMode.PAYLOAD_HTTP_PROXY, ConnectionMode.SSH_WEBSOCKET -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = proxyHost,
                                onValueChange = onProxyHostChange,
                                label = { Text("Proxy Host", color = TextSecondary) },
                                placeholder = { Text("proxy.example.com", color = TextMuted) },
                                modifier = Modifier
                                    .weight(2.5f)
                                    .testTag("input_proxy_host"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = CardDark,
                                    unfocusedContainerColor = CardDark,
                                    focusedBorderColor = MintGreen,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = proxyPort,
                                onValueChange = onProxyPortChange,
                                label = { Text("Port", color = TextSecondary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_proxy_port"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = CardDark,
                                    unfocusedContainerColor = CardDark,
                                    focusedBorderColor = MintGreen,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    else -> {}
                }

                if (selectedMode == ConnectionMode.SSH_WEBSOCKET) {
                    OutlinedTextField(
                        value = wsPath,
                        onValueChange = onWsPathChange,
                        label = { Text("WebSocket Path", color = TextSecondary) },
                        placeholder = { Text("/", color = TextMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_ws_path"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardDark,
                            unfocusedContainerColor = CardDark,
                            focusedBorderColor = MintGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Info card
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ⓘ OpenVPN Profile (.ovpn) and Panther VPN start the integrated packet engine. Direct SSH, HTTP Payload and SNI modes test and verify the transport endpoints.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Save button
                Button(
                    onClick = onSaveSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = BgDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_mode_settings_button")
                ) {
                    Text("SAVE CONNECTION SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
