package com.example.cameye.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cameye.R // Import your R file
import com.example.cameye.ar.ArTrackingState

@Composable
fun ArStatusIndicator(
    trackingState: ArTrackingState,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector
    val color: Color
    val text: String

    when (trackingState) {
        ArTrackingState.TRACKING -> {
            icon = ImageVector.vectorResource(id = R.drawable.ic_ar_tracking)
            color = Color(0xFF4CAF50) // Green
            text = "AR Tracking"
        }
        ArTrackingState.INITIALIZING -> {
            icon = ImageVector.vectorResource(id = R.drawable.ic_ar_not_tracking) // Or a loading icon
            color = MaterialTheme.colorScheme.onSurfaceVariant
            text = "AR Init..."
        }
        ArTrackingState.NOT_TRACKING -> {
            icon = ImageVector.vectorResource(id = R.drawable.ic_ar_not_tracking)
            color = Color(0xFFFFC107) // Amber
            text = "AR Not Tracking"
        }
        ArTrackingState.UNSUPPORTED, ArTrackingState.ERROR -> {
            icon = ImageVector.vectorResource(id = R.drawable.ic_ar_not_tracking) // Or error icon
            color = Color(0xFFF44336) // Red
            text = if (trackingState == ArTrackingState.UNSUPPORTED) "AR Unsupported" else "AR Error"
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "AR Status: $text",
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}