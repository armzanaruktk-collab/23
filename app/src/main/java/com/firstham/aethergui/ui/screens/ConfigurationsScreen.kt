package com.firstham.aethergui.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstham.aethergui.MainViewModel
import com.firstham.aethergui.ui.theme.*

@Composable
fun ConfigurationsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val socksAddress by viewModel.socksAddress.collectAsState()
    val mtu by viewModel.mtu.collectAsState()
    val privateDns by viewModel.privateDns.collectAsState()
    val killSwitch by viewModel.killSwitch.collectAsState()
    val fastBridge by viewModel.fastBridge.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "TUNNEL & PROTOCOL SETTINGS",
            color = MintGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SOCKS5 Address
                OutlinedTextField(
                    value = socksAddress,
                    onValueChange = { viewModel.socksAddress.value = it },
                    label = { Text("Local SOCKS5 Address", color = TextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_socks_address"),
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

                // MTU
                OutlinedTextField(
                    value = mtu,
                    onValueChange = { viewModel.mtu.value = it },
                    label = { Text("VPN MTU", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_vpn_mtu"),
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

                Divider(color = CardBorder, thickness = 1.dp)

                // Private DNS Routing switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Private DNS Routing", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Route DNS queries strictly through encrypted tunnel", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = privateDns,
                        onCheckedChange = { viewModel.privateDns.value = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDark,
                            checkedTrackColor = MintGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CardDark
                        ),
                        modifier = Modifier.testTag("switch_private_dns")
                    )
                }

                // Kill Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Fail Closed (Kill Switch)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Keep routes blocked if VPN unexpectedly disconnects", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = killSwitch,
                        onCheckedChange = { viewModel.killSwitch.value = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDark,
                            checkedTrackColor = MintGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CardDark
                        ),
                        modifier = Modifier.testTag("switch_kill_switch")
                    )
                }

                // Fast Tunnel Beta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Fast Tunnel (Beta)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Lightweight native packet engine for lower latency", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = fastBridge,
                        onCheckedChange = { viewModel.fastBridge.value = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDark,
                            checkedTrackColor = MintGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CardDark
                        ),
                        modifier = Modifier.testTag("switch_fast_bridge")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        viewModel.saveConfigSettings()
                        Toast.makeText(context, "Configurations saved", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = BgDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("save_config_button")
                ) {
                    Text("APPLY CONFIGURATIONS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.socksAddress.value = "127.0.0.1:1819"
                        viewModel.mtu.value = "1500"
                        viewModel.privateDns.value = true
                        viewModel.killSwitch.value = false
                        viewModel.fastBridge.value = true
                        viewModel.saveConfigSettings()
                        Toast.makeText(context, "Defaults restored", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorder)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("reset_defaults_button")
                ) {
                    Text("RESET TO DEFAULTS", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
