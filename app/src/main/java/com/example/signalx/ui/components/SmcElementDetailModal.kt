package com.example.signalx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signalx.ui.chart.SelectedSmcElement
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun SmcElementDetailModal(
    element: SelectedSmcElement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Karachi")
    }
    val timeFormatted = if (element.timestamp > 0) dateFormat.format(Date(element.timestamp)) else "Current Formation"

    val dirColor = when (element.direction) {
        "BULLISH" -> BullishGreen
        "BEARISH" -> BearishRed
        else -> AccentYellow
    }

    Surface(
        color = Color(0xF0161A1E),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Title, Direction badge, and Close icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = element.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(dirColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = element.direction,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = dirColor
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Price & Formation Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Zone / Price: ${element.priceRangeStr}", fontSize = 10.sp, color = AccentYellow, fontWeight = FontWeight.SemiBold)
                Text(text = "Status: ${element.status}", fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Text(text = "Time (PKT): $timeFormatted", fontSize = 9.sp, color = TextMuted)

            Spacer(modifier = Modifier.height(6.dp))

            // Confirmation & Invalidation Conditions
            if (element.confirmationCondition.isNotEmpty()) {
                Text(
                    text = "• Confirmation: ${element.confirmationCondition}",
                    fontSize = 10.sp,
                    color = BullishGreen
                )
            }
            if (element.invalidationCondition.isNotEmpty()) {
                Text(
                    text = "• Invalidation: ${element.invalidationCondition}",
                    fontSize = 10.sp,
                    color = BearishRed
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Roman Urdu Explanation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkCard)
                    .padding(8.dp)
            ) {
                Column {
                    Text(text = "Roman Urdu Concept:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Text(text = element.romanUrduExplanation, fontSize = 10.sp, color = TextPrimary)
                }
            }

            if (element.supportingEvidence.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Evidence: ${element.supportingEvidence}",
                    fontSize = 9.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
