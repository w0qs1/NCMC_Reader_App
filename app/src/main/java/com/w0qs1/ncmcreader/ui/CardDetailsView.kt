package com.w0qs1.ncmcreader.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.w0qs1.ncmcreader.data.NcmcCard
import com.w0qs1.ncmcreader.data.NcmcOperatorDatabaseHelper
import com.w0qs1.ncmcreader.data.TicketHistoryItem
import com.w0qs1.ncmcreader.data.TransitLine
import com.w0qs1.ncmcreader.nfc.maskPan
import com.w0qs1.ncmcreader.nfc.maskTimestamp
import java.util.Locale

@Composable
fun CardDetailsView(
    card: NcmcCard,
    modifier: Modifier = Modifier,
    isMaskPanEnabled: Boolean = false,
    isMaskTimeEnabled: Boolean = false,
    isMaskDateEnabled: Boolean = false,
    isMaskMetadataEnabled: Boolean = false
) {
    val context = LocalContext.current
    val opDbHelper = remember(context) { NcmcOperatorDatabaseHelper(context) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Main Card Info Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.cardTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if ((card.ncmcVersion == "-") || (card.ncmcVersion == "NCMC Not Enabled")) "NCMC Not Enabled" else "Version " + card.ncmcVersion,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isMaskPanEnabled) maskPan(card.pan) else card.pan,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Current Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = String.format(Locale.US, "₹%.2f", card.currentBalance),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ATC",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (isMaskMetadataEnabled) "XXXX" else card.atc.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Metadata Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailField(label = "Expiry Date", value = if (isMaskMetadataEnabled) "XXXX-XX-XX" else card.expiryDate)
                    DetailField(label = "Effective Date", value = if (isMaskMetadataEnabled) "XXXX-XX-XX" else card.effectiveDate)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailField(label = "Language", value = card.cardLanguage)
                    DetailField(label = "NCMC Version", value = card.ncmcVersion)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Ticket History Section
        Text(
            text = "Ticket & Transaction History (${card.ticketHistory.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (card.ticketHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (card.ncmcVersion == "-" || card.ncmcVersion == "NCMC Not Enabled") "NCMC service is not enabled on this card." else "No ticket or transaction records found on this card.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                card.ticketHistory.forEach { item ->
                    TicketHistoryRow(
                        item = item,
                        opDbHelper = opDbHelper,
                        isMaskTimeEnabled = isMaskTimeEnabled,
                        isMaskDateEnabled = isMaskDateEnabled
                    )
                }
            }
        }
    }
}

@Composable
fun DetailField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TicketHistoryRow(
    item: TicketHistoryItem,
    opDbHelper: NcmcOperatorDatabaseHelper?,
    isMaskTimeEnabled: Boolean = false,
    isMaskDateEnabled: Boolean = false
) {
    val badgeType = item.ticketType.uppercase(Locale.US)
    val badgeColor = when {
        "ENTRY" in badgeType -> Color(0xFF2E7D32)
        "EXIT" in badgeType -> Color(0xFFD84315)
        "TICKET" in badgeType -> Color(0xFF0277BD)
        "RECHARGE" in badgeType -> Color(0xFF6A1B9A)
        else -> MaterialTheme.colorScheme.secondary
    }

    // Decode station and operator names only when Card Details page is opened
    val resolved = remember(item, opDbHelper) {
        val res = if (item.acquirerId > 0 || item.operatorId > 0 || item.terminalId > 0) {
            opDbHelper?.resolveStation(item.acquirerId, item.operatorId, item.terminalId)
        } else null

        if (res != null && res.lines.isNotEmpty()) {
            res
        } else {
            opDbHelper?.resolveStationByName(item.stationOrLocation) ?: res
        }
    }

    LaunchedEffect(resolved, item) {
        val linesStr = resolved?.lines?.joinToString(", ") { "${it.name} (${it.colour ?: "no-color"})" } ?: "None"
        Log.d(
            "NCMC_DECODE",
            "Decoded Station Info -> Date/Time: ${item.timestamp}, Type/Action: ${item.ticketType}, Operator: ${resolved?.operatorName ?: "Unknown"}, Station: ${resolved?.stationName ?: item.stationOrLocation}, Line(s): [$linesStr]"
        )
    }

    val displayStation = remember(resolved, item) {
        if (resolved != null) {
            if (resolved.stationName.startsWith("Terminal #") || resolved.stationName.startsWith("Terminal 0x")) {
                "${resolved.operatorName} (${resolved.stationName})"
            } else {
                "${resolved.operatorName} - ${resolved.stationName}"
            }
        } else {
            if (item.stationOrLocation.startsWith("Terminal 0x") || item.stationOrLocation.startsWith("Terminal #")) {
                "Unknown Operator (${item.stationOrLocation})"
            } else {
                "Unknown Operator - ${item.stationOrLocation}"
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 1st row: Operator Name - Station Name | Fare
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = displayStation,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                if (item.amount > 0) {
                    Text(
                        text = String.format(Locale.US, "-₹%.2f", item.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "₹0.00",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2nd row: Entry/Exit/Ticket | date time | line colours
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge (Entry/Exit/Ticket/Recharge)
                    Box(
                        modifier = Modifier
                            .background(badgeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.ticketType,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }

                    // Date & Time
                    Text(
                        text = maskTimestamp(item.timestamp, isMaskTimeEnabled, isMaskDateEnabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Line colours
                if (resolved?.lines?.isNotEmpty() == true) {
                    Box(modifier = Modifier.padding(end = 10.dp)) {
                        TransitLinesRow(lines = resolved.lines)
                    }
                }
            }
        }
    }
}

@Composable
fun TransitLinesRow(lines: List<TransitLine>) {
    val unitWidth = 10.dp
    val height = 16.dp
    val totalWidth = unitWidth * lines.size

    Canvas(
        modifier = Modifier
            .width(totalWidth)
            .height(height)
    ) {
        val w = size.width / lines.size
        val h = size.height
        val slant = h * 0.35f

        // Draw each segment fill
        lines.forEachIndexed { index, line ->
            val color = parseColor(line.colour)
            val path = Path().apply {
                moveTo(index * w + slant, 0f)
                lineTo((index + 1) * w + slant, 0f)
                lineTo((index + 1) * w, h)
                lineTo(index * w, h)
                close()
            }
            drawPath(path, color = color)
        }

        // Draw internal dividing lines between segments (tilted to the right)
        for (i in 1 until lines.size) {
            val divX = i * w
            drawLine(
                color = Color.White,
                start = Offset(divX + slant, 0f),
                end = Offset(divX, h),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw outer shared border/outline around the combined parallelogram
        val outerPath = Path().apply {
            moveTo(slant, 0f)
            lineTo(size.width + slant, 0f)
            lineTo(size.width, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            outerPath,
            color = Color.White,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

fun parseColor(colourStr: String?): Color {
    val parsedColorInt = try {
        if (!colourStr.isNullOrBlank()) {
            "#$colourStr".toColorInt()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
    return if (parsedColorInt != null) Color(parsedColorInt) else Color.Gray
}
