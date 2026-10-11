package com.w0qs1.ncmcreader

import com.w0qs1.ncmcreader.data.NcmcCard
import com.w0qs1.ncmcreader.data.RawRecordItem
import com.w0qs1.ncmcreader.data.ResolvedStation
import com.w0qs1.ncmcreader.data.TicketHistoryItem
import com.w0qs1.ncmcreader.data.TransitLine
import org.junit.Assert.assertEquals
import org.junit.Test

class NcmcDatabaseTest {

    @Test
    fun testNcmcCardModelAndHistoryMerging() {
        val initialHistory = listOf(
            TicketHistoryItem(
                cardPan = "6082 1234 5678 9012",
                ticketType = "ENTRY",
                stationOrLocation = "Rajiv Chowk",
                timestamp = "2025-02-15 08:30:00",
                amount = 0.0,
                balance = 150.50
            )
        )

        val card1 = NcmcCard(
            pan = "6082 1234 5678 9012",
            expiryDate = "2028-12-31",
            effectiveDate = "2023-01-01",
            ncmcVersion = "v1.0",
            cardLanguage = "en",
            atc = 42,
            currentBalance = 150.50,
            ticketHistory = initialHistory
        )

        assertEquals("6082 1234 5678 9012", card1.pan)
        assertEquals(150.50, card1.currentBalance, 0.01)
        assertEquals(1, card1.ticketHistory.size)

        // Updated card after exit
        val exitHistoryItem = TicketHistoryItem(
            cardPan = "6082 1234 5678 9012",
            ticketType = "EXIT",
            stationOrLocation = "Hauz Khas",
            timestamp = "2025-02-15 09:15:00",
            amount = 30.0,
            balance = 120.50
        )

        val mergedHistory = (card1.ticketHistory + exitHistoryItem).distinctBy { 
            "${it.ticketType}_${it.stationOrLocation}_${it.timestamp}_${it.amount}" 
        }

        val card2 = card1.copy(
            atc = 43,
            currentBalance = 120.50,
            ticketHistory = mergedHistory
        )

        assertEquals(120.50, card2.currentBalance, 0.01)
        assertEquals(43, card2.atc)
        assertEquals(2, card2.ticketHistory.size)
        assertEquals("EXIT", card2.ticketHistory[1].ticketType)
    }

    @Test
    fun testRawRecordsInNcmcCardModel() {
        val rawRecs = listOf(
            RawRecordItem(sfi = 1, recordNum = 1, rawHex = "7081AC5A088181942506819397"),
            RawRecordItem(sfi = 16, recordNum = 1, rawHex = "010203040506")
        )

        val card = NcmcCard(
            pan = "6082 1234 5678 9012",
            expiryDate = "2028-12-31",
            effectiveDate = "2023-01-01",
            ncmcVersion = "v1.0",
            cardLanguage = "en",
            atc = 42,
            currentBalance = 150.50,
            rawRecords = rawRecs
        )

        assertEquals(2, card.rawRecords.size)
        assertEquals(1, card.rawRecords[0].sfi)
        assertEquals(16, card.rawRecords[1].sfi)
    }

    @Test
    fun testResolvedStationWithLines() {
        val line1 = TransitLine("Blue Line", "0072BC")
        val line2 = TransitLine("Yellow Line", "FFD100")
        val resolved = ResolvedStation(
            operatorName = "Delhi Metro",
            transitMode = "METRO",
            stationName = "Rajiv Chowk",
            lines = listOf(line1, line2)
        )

        assertEquals("Delhi Metro", resolved.operatorName)
        assertEquals("Rajiv Chowk", resolved.stationName)
        assertEquals(2, resolved.lines.size)
        assertEquals("Blue Line", resolved.lines[0].name)
        assertEquals("0072BC", resolved.lines[0].colour)
    }
}
