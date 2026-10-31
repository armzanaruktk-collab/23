package com.firstham.aethergui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstham.aethergui.ConnectionStatus
import com.firstham.aethergui.TelemetryState
import com.firstham.aethergui.ui.theme.*

@Composable
fun HeroPowerCard(
    status: ConnectionStatus,
    telemetry: TelemetryState,
    onToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = status == ConnectionStatus.CONNECTED
    val isConnecting = status == ConnectionStatus.CONNECTING

    // Pulsing animation for connecting / connected states
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnecting || isConnected) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "power_scale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.CONNECTED -> MintGreen
            ConnectionStatus.CONNECTING -> ElectricBlue
            ConnectionStatus.DISCONNECTED -> CardDark
            ConnectionStatus.FAILED -> ErrorRed
        },
        label = "button_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_power_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(CardBorder, BorderDark)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Status label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (status) {
                                ConnectionStatus.CONNECTED -> MintGreen
                                ConnectionStatus.CONNECTING -> ElectricBlue
                                ConnectionStatus.DISCONNECTED -> TextMuted
                                ConnectionStatus.FAILED -> ErrorRed
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (status) {
                        ConnectionStatus.CONNECTED -> "Panther Active"
                        ConnectionStatus.CONNECTING -> "Connecting…"
                        ConnectionStatus.DISCONNECTED -> "Panther Ready"
                        ConnectionStatus.FAILED -> "Panther Ready"
                    },
                    color = when (status) {
                        ConnectionStatus.CONNECTED -> MintGreen
                        ConnectionStatus.CONNECTING -> ElectricBlue
                        else -> TextSecondary
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Big Power Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                buttonColor.copy(alpha = if (isConnected) 0.35f else 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 3.dp,
                        color = buttonColor.copy(alpha = if (isConnected) 0.9f else 0.4f),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = buttonColor),
                        onClick = onToggleClick
                    )
                    .testTag("power_connect_button")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = if (isConnected) 0.25f else 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Toggle Connection",
                        tint = if (isConnected) MintGreen else TextPrimary,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time Location display per PROMPT.md (Display-only, no server selector or change button)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark.copy(alpha = 0.7f))
                    .padding(vertical = 10.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "YOUR LOCATION",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val locationText = when (status) {
                    ConnectionStatus.DISCONNECTED -> "Location unavailable"
                    ConnectionStatus.CONNECTING -> "Detecting location…"
                    ConnectionStatus.CONNECTED -> {
                        val flag = telemetry.countryFlag ?: "🌐"
                        val loc = telemetry.locationName ?: "Connected"
                        "$flag  $loc"
                    }
                    ConnectionStatus.FAILED -> "Location unavailable"
                }
                Text(
                    text = locationText,
                    color = if (isConnected) MintGreen else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isConnected && telemetry.exitIp != null) {
                    Text(
                        text = "Exit IP: ${telemetry.exitIp}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Row: Ping (bold value) & Traffic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ping metric card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ping",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isConnected && telemetry.pingMs != null) "${telemetry.pingMs} ms" else "--",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold // Requirement: Ping value must be bold
                    )
                }

                // Traffic metric card
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Traffic",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "↑ ${formatBytes(telemetry.uploadBytes)}",
                            color = ElectricBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "↓ ${formatBytes(telemetry.downloadBytes)}",
                            color = MintGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "--"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        else -> String.format(java.util.Locale.US, "%.1f KB", kb)
    }
}
