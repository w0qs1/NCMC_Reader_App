package com.w0qs1.ncmcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.w0qs1.ncmcreader.data.NcmcCard
import com.w0qs1.ncmcreader.data.NcmcOperatorDatabaseHelper
import com.w0qs1.ncmcreader.data.TicketHistoryItem
import java.util.Locale

@Composable
fun CardDetailsView(
    card: NcmcCard,
    modifier: Modifier = Modifier
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
                    text = card.pan,
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
                            text = card.atc.toString(),
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
                    DetailField(label = "Expiry Date", value = card.expiryDate)
                    DetailField(label = "Effective Date", value = card.effectiveDate)
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
                    TicketHistoryRow(item = item, opDbHelper = opDbHelper)
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
    opDbHelper: NcmcOperatorDatabaseHelper?
) {
    val badgeType = item.ticketType.uppercase(Locale.US)
    val badgeColor = when {
        "ENTRY" in badgeType -> Color(0xFF2E7D32)
        "EXIT" in badgeType -> Color(0xFFD84315)
        "ONE_TAP" in badgeType || "ONE TAP" in badgeType -> Color(0xFF0277BD)
        "RECHARGE" in badgeType -> Color(0xFF6A1B9A)
        else -> MaterialTheme.colorScheme.secondary
    }

    // Decode station and operator names only when Card Details page is opened
    val resolved = remember(item, opDbHelper) {
        if (item.acquirerId > 0 || item.operatorId > 0 || item.terminalId > 0) {
            opDbHelper?.resolveStation(item.acquirerId, item.operatorId, item.terminalId)
        } else null
    }

    val displayStation = remember(resolved, item) {
        if (resolved != null) {
            if (resolved.stationName.startsWith("Terminal #")) {
                "${resolved.operatorName} (${resolved.stationName})"
            } else {
                "${resolved.operatorName} - ${resolved.stationName}"
            }
        } else {
            item.stationOrLocation
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayStation,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
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
        }
    }
}
