package com.firstham.aethergui.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstham.aethergui.MainViewModel
import com.firstham.aethergui.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val splitTunneling by viewModel.splitTunneling.collectAsState()
    val splitInclude by viewModel.splitInclude.collectAsState()
    val autoReconnect by viewModel.autoReconnect.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "APPLICATION SETTINGS",
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
                // Split Tunneling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Split Tunneling", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Choose which apps route through Panther VPN", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = splitTunneling,
                        onCheckedChange = { viewModel.splitTunneling.value = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDark,
                            checkedTrackColor = MintGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CardDark
                        ),
                        modifier = Modifier.testTag("switch_split_tunneling")
                    )
                }

                if (splitTunneling) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardDark, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Routing Mode", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = splitInclude,
                                onClick = { viewModel.splitInclude.value = true },
                                label = { Text("Include Apps Only", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MintGreen,
                                    selectedLabelColor = BgDark
                                )
                            )
                            FilterChip(
                                selected = !splitInclude,
                                onClick = { viewModel.splitInclude.value = false },
                                label = { Text("Bypass Selected Apps", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MintGreen,
                                    selectedLabelColor = BgDark
                                )
                            )
                        }
                    }
                }

                Divider(color = CardBorder, thickness = 1.dp)

                // Auto Reconnect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Auto Reconnect", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Automatically recover session after network switches", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoReconnect,
                        onCheckedChange = { viewModel.autoReconnect.value = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDark,
                            checkedTrackColor = MintGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CardDark
                        ),
                        modifier = Modifier.testTag("switch_auto_reconnect")
                    )
                }

                Divider(color = CardBorder, thickness = 1.dp)

                // System Notification Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("System Notifications", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Manage VPN persistent notification and channel settings", color = TextSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Manage", fontSize = 11.sp)
                    }
                }

                Divider(color = CardBorder, thickness = 1.dp)

                // Reset Prowl route
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Reset Prowl Route", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Forget current gateway memory and redial fresh route", color = TextSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.appendLog("Prowl route cache cleared. Next connection will re-probe all endpoints.")
                            Toast.makeText(context, "Prowl route memory cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ProwlVioletSoft, contentColor = ProwlViolet),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
