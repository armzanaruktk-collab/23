package com.firstham.aethergui.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstham.aethergui.ConnectionStatus
import com.firstham.aethergui.TelemetryState
import com.firstham.aethergui.ui.theme.*

@Composable
fun NetworkTelemetryMonitor(
    status: ConnectionStatus,
    telemetry: TelemetryState,
    modifier: Modifier = Modifier
) {
    val isConnected = status == ConnectionStatus.CONNECTED

    // Animate speed gauge progress (0 to 1 scale mapped to max 10 MB/s)
    val maxSpeedBps = 8_000_000f // ~8 MB/s reference scale
    val currentRatio = if (isConnected) {
        (telemetry.downloadSpeedBps / maxSpeedBps).coerceIn(0.08f, 1f)
    } else {
        0f
    }

    val animatedRatio by animateFloatAsState(
        targetValue = currentRatio,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "speed_ratio"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("network_telemetry_monitor"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(CardBorder, BorderDark)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Speed Monitor",
                        tint = MintGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NETWORK TELEMETRY MONITOR",
                        color = MintGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = if (isConnected) "LIVE TELEMETRY" else "STANDBY",
                    color = if (isConnected) ElectricBlue else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Real-Time Speed Rates Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Download Speed Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .padding(10.dp)
                ) {
                    Text("Download Speed", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) formatSpeed(telemetry.downloadSpeedBps) else "0.0 KB/s",
                        color = MintGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Upload Speed Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .padding(10.dp)
                ) {
                    Text("Upload Speed", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) formatSpeed(telemetry.uploadSpeedBps) else "0.0 KB/s",
                        color = ElectricBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Real-time Bandwidth Utilization Indicator (Linear gauge)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bandwidth load indicator", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = "${(animatedRatio * 100).toInt()}%",
                        color = if (isConnected) MintGreen else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { animatedRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("telemetry_speed_progress"),
                    color = MintGreen,
                    trackColor = CardDark
                )
            }

            // Live Telemetry Waveform Line Chart
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Connection throughput waveform",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardDark.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    val points = telemetry.downloadHistory
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (isConnected && points.size >= 2) {
                            val stepX = size.width / (points.size - 1)
                            val path = Path()
                            val fillPath = Path()

                            points.forEachIndexed { i, normVal ->
                                val x = i * stepX
                                val y = size.height - (normVal * size.height).coerceIn(4f, size.height - 4f)
                                if (i == 0) {
                                    path.moveTo(x, y)
                                    fillPath.moveTo(x, size.height)
                                    fillPath.lineTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                    fillPath.lineTo(x, y)
                                }
                            }
                            fillPath.lineTo(size.width, size.height)
                            fillPath.close()

                            // Gradient under-chart fill
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    listOf(MintGreen.copy(alpha = 0.25f), MintGreen.copy(alpha = 0.02f))
                                )
                            )

                            // Main throughput line
                            drawPath(
                                path = path,
                                color = MintGreen,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        } else {
                            // Flat baseline when idle
                            drawLine(
                                color = CardBorder,
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0.0 KB/s"
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(java.util.Locale.US, "%.2f MB/s", mb)
    } else {
        String.format(java.util.Locale.US, "%.1f KB/s", kb)
    }
}
