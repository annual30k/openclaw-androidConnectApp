package com.rethinkingstudio.clawlink.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType

@Composable
fun GatewayTypeIconBadge(
    type: GatewayType,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = gatewayTypeBadgeBackground(type),
        border = BorderStroke(1.dp, gatewayTypeBadgeForeground(type).copy(alpha = 0.18f)),
        modifier = modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(gatewayTypeIconResource(type)),
                contentDescription = type.displayTitle,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun gatewayTypeIconResource(type: GatewayType): Int {
    return when (type) {
        GatewayType.hermes -> R.drawable.ic_gateway_hermes
        GatewayType.openclaw -> R.drawable.ic_gateway_openclaw
    }
}

private fun gatewayTypeBadgeBackground(type: GatewayType): Color {
    return when (type) {
        GatewayType.hermes -> Color(0xFFFFF3D6)
        GatewayType.openclaw -> Color(0x1F35A8FF)
    }
}

private fun gatewayTypeBadgeForeground(type: GatewayType): Color {
    return when (type) {
        GatewayType.hermes -> Color(0xFF8A5A00)
        GatewayType.openclaw -> Color(0xFF2498FF)
    }
}
