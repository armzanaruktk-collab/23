package com.firstham.aethergui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstham.aethergui.VpnEngine
import com.firstham.aethergui.ui.theme.*

@Composable
fun EngineSelector(
    selectedEngine: VpnEngine,
    onEngineSelect: (VpnEngine) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "ENGINE",
            color = MintGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VpnEngine.entries.forEach { engine ->
                val isSelected = engine == selectedEngine
                val activeColor = when (engine) {
                    VpnEngine.TURBO -> MintGreen
                    VpnEngine.GLOBAL -> ElectricBlue
                    VpnEngine.PROWL -> ProwlViolet
                    VpnEngine.BEACON -> BeaconAmber
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) activeColor.copy(alpha = 0.15f) else SurfaceDark)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) activeColor else CardBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onEngineSelect(engine) }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .testTag("engine_${engine.name.lowercase()}"),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = engine.title,
                        color = if (isSelected) activeColor else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = engine.detail,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
