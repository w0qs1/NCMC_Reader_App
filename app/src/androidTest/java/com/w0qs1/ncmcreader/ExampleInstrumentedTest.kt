package com.w0qs1.ncmcreader

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.w0qs1.ncmcreader.data.NcmcCard
import com.w0qs1.ncmcreader.data.NcmcDatabaseHelper
import com.w0qs1.ncmcreader.data.TicketHistoryItem

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.w0qs1.ncmcreader", appContext.packageName)
    }

    @Test
    fun testCardUpdateAppendsHistoryAndOverwritesAtcAndBalance() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dbHelper = NcmcDatabaseHelper(appContext)
        val pan = "6082 9999 8888 7777"

        // Clean up pre-existing test entry if any
        dbHelper.deleteCard(pan)

        val history1 = listOf(
            TicketHistoryItem(
                cardPan = pan,
                ticketType = "ENTRY",
                stationOrLocation = "Station A",
                timestamp = "2025-02-15 08:00",
                amount = 0.0,
                balance = 200.0,
                rawHex = "112233"
            ),
            TicketHistoryItem(
                cardPan = pan,
                ticketType = "EXIT",
                stationOrLocation = "Station B",
                timestamp = "2025-02-15 08:30",
                amount = 30.0,
                balance = 170.0,
                rawHex = "445566"
            )
        )

        val cardInitial = NcmcCard(
            pan = pan,
            expiryDate = "2030-01-01",
            effectiveDate = "2025-01-01",
            ncmcVersion = "3.1",
            cardLanguage = "English",
            atc = 10,
            currentBalance = 170.0,
            ticketHistory = history1
        )

        dbHelper.saveOrUpdateCard(cardInitial)

        val retrieved1 = dbHelper.getCardByPan(pan)
        assertNotNull(retrieved1)
        assertEquals(10, retrieved1!!.atc)
        assertEquals(170.0, retrieved1.currentBalance, 0.01)
        assertEquals(2, retrieved1.ticketHistory.size)

        // Second scan of the same card with updated ATC, new balance, and a new transaction
        val history2 = listOf(
            TicketHistoryItem(
                cardPan = pan,
                ticketType = "EXIT",
                stationOrLocation = "Station B",
                timestamp = "2025-02-15 08:30",
                amount = 30.0,
                balance = 170.0,
                rawHex = "445566"
            ),
            TicketHistoryItem(
                cardPan = pan,
                ticketType = "ENTRY",
                stationOrLocation = "Station C",
                timestamp = "2025-02-15 17:00",
                amount = 0.0,
                balance = 170.0,
                rawHex = "778899"
            )
        )

        val cardUpdated = cardInitial.copy(
            atc = 11,
            currentBalance = 140.0,
            ticketHistory = history2
        )

        dbHelper.saveOrUpdateCard(cardUpdated)

        val retrieved2 = dbHelper.getCardByPan(pan)
        assertNotNull(retrieved2)
        assertEquals(11, retrieved2!!.atc)
        assertEquals(140.0, retrieved2.currentBalance, 0.01)
        // Earlier history ("ENTRY" at Station A) must be preserved, and new history ("ENTRY" at Station C) appended
        assertEquals(3, retrieved2.ticketHistory.size)

        // Clean up
        dbHelper.deleteCard(pan)
    }

    @Test
    fun testDeduplicationAndCorrectionOfBuggyExitStatus() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dbHelper = NcmcDatabaseHelper(appContext)
        val pan = "6082 8888 7777 6666"

        dbHelper.deleteCard(pan)

        // Old scan where entry record was stored as Exit with amount = 0.0
        val buggyOldHistory = listOf(
            TicketHistoryItem(
                cardPan = pan,
                ticketType = "Exit",
                stationOrLocation = "Station X",
                timestamp = "2025-02-10 10:00",
                amount = 0.0,
                balance = 100.0,
                rawHex = "AABBCC"
            )
        )

        val card1 = NcmcCard(
            pan = pan,
            expiryDate = "2030-01-01",
            effectiveDate = "2025-01-01",
            ncmcVersion = "3.1",
            cardLanguage = "English",
            atc = 5,
            currentBalance = 100.0,
            ticketHistory = buggyOldHistory
        )

        dbHelper.saveOrUpdateCard(card1)

        // New scan where the reader corrected the status to Entry
        val correctedScanHistory = listOf(
            TicketHistoryItem(
                cardPan = pan,
                ticketType = "Entry",
                stationOrLocation = "Station X",
                timestamp = "2025-02-10 10:00",
                amount = 0.0,
                balance = 100.0,
                rawHex = "AABBCC00"
            )
        )

        val card2 = card1.copy(atc = 6, ticketHistory = correctedScanHistory)
        dbHelper.saveOrUpdateCard(card2)

        val retrieved = dbHelper.getCardByPan(pan)
        assertNotNull(retrieved)
        // Should have exactly 1 record, updated to "Entry", and NO duplicates!
        assertEquals(1, retrieved!!.ticketHistory.size)
        assertEquals("Entry", retrieved.ticketHistory[0].ticketType)

        dbHelper.deleteCard(pan)
    }
}