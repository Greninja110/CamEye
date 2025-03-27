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

enum class StreamQuality {
    GOOD, MEDIUM, POOR, UNKNOWN
}

@Composable
fun StreamQualityIndicator(
    quality: StreamQuality, // Determine quality based on bitrate, latency, packet loss
    bitrateKbps: Int,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector
    val color: Color
    val text: String

    when (quality) {
        StreamQuality.GOOD -> {
            icon = ImageVector.vectorResource(id = R.drawable.ic_stream_good)
            color = Color(0xFF4CAF50) // Green
            text = "Good"
        }
        StreamQuality.MEDIUM -> {
            icon = ImageVector.vectorResource(id = R.drawable.ic_stream_medium)
            color = Color(0xFFFFC107) // Amber
            text = "Medium"
        }
        StreamQuality.POOR -> {
            icon = ImageVector.vectorResource(id = R.drawable.ic_stream_poor)
            color = Color(0xFFF44336) // Red
            text = "Poor"
        }
        StreamQuality.UNKNOWN -> {
            // Use a neutral or loading state
            icon = ImageVector.vectorResource(id = R.drawable.ic_stream_medium) // Or a question mark icon
            color = MaterialTheme.colorScheme.onSurfaceVariant
            text = "N/A"
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
            contentDescription = "Stream Quality: $text",
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${bitrateKbps}kbps", // Display current bitrate
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}