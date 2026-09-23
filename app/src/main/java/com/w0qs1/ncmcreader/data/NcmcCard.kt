package com.w0qs1.ncmcreader.data

/**
 * Represents ticket or transaction history extracted from NCMC card logs.
 */
data class TicketHistoryItem(
    val id: Long = 0,
    val cardPan: String,
    val ticketType: String, // e.g., "ENTRY", "EXIT", "ONE_TAP", "RECHARGE", "TRANSACTION"
    val stationOrLocation: String,
    val timestamp: String,
    val amount: Double,
    val balance: Double,
    val rawHex: String = "",
    val acquirerId: Int = 0,
    val operatorId: Int = 0,
    val terminalId: Int = 0
)

/**
 * Represents full NCMC card information read via NFC.
 */
data class NcmcCard(
    val pan: String,
    val expiryDate: String,
    val effectiveDate: String,
    val ncmcVersion: String,
    val cardLanguage: String,
    val atc: Int,
    val currentBalance: Double, // in Rupees (e.g., 150.50)
    val lastScannedAt: Long = System.currentTimeMillis(),
    val ticketHistory: List<TicketHistoryItem> = emptyList(),
    val cardTitle: String = "RuPay NCMC Card"
)
