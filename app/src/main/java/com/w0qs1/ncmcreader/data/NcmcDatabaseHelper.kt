package com.w0qs1.ncmcreader.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction

class NcmcDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ncmc_cards.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_CARDS = "cards"
        private const val COLUMN_PAN = "pan"
        private const val COLUMN_EXPIRY_DATE = "expiry_date"
        private const val COLUMN_EFFECTIVE_DATE = "effective_date"
        private const val COLUMN_NCMC_VERSION = "ncmc_version"
        private const val COLUMN_CARD_LANGUAGE = "card_language"
        private const val COLUMN_ATC = "atc"
        private const val COLUMN_CURRENT_BALANCE = "current_balance"
        private const val COLUMN_LAST_SCANNED_AT = "last_scanned_at"
        private const val COLUMN_CARD_TITLE = "card_title"

        private const val TABLE_HISTORY = "ticket_history"
        private const val COLUMN_HISTORY_ID = "id"
        private const val COLUMN_HISTORY_CARD_PAN = "card_pan"
        private const val COLUMN_HISTORY_TICKET_TYPE = "ticket_type"
        private const val COLUMN_HISTORY_STATION = "station_location"
        private const val COLUMN_HISTORY_TIMESTAMP = "timestamp"
        private const val COLUMN_HISTORY_AMOUNT = "amount"
        private const val COLUMN_HISTORY_BALANCE = "balance"
        private const val COLUMN_HISTORY_RAW_HEX = "raw_hex"
        private const val COLUMN_HISTORY_ACQUIRER_ID = "acquirer_id"
        private const val COLUMN_HISTORY_OPERATOR_ID = "operator_id"
        private const val COLUMN_HISTORY_TERMINAL_ID = "terminal_id"

        private const val TABLE_RAW_RECORDS = "raw_records"
        private const val COLUMN_RAW_RECORD_ID = "id"
        private const val COLUMN_RAW_RECORD_CARD_PAN = "card_pan"
        private const val COLUMN_RAW_RECORD_SFI = "sfi"
        private const val COLUMN_RAW_RECORD_NUM = "record_num"
        private const val COLUMN_RAW_RECORD_HEX = "raw_hex"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createCardsTable = """
            CREATE TABLE $TABLE_CARDS (
                $COLUMN_PAN TEXT PRIMARY KEY,
                $COLUMN_EXPIRY_DATE TEXT,
                $COLUMN_EFFECTIVE_DATE TEXT,
                $COLUMN_NCMC_VERSION TEXT,
                $COLUMN_CARD_LANGUAGE TEXT,
                $COLUMN_ATC INTEGER,
                $COLUMN_CURRENT_BALANCE REAL,
                $COLUMN_LAST_SCANNED_AT INTEGER,
                $COLUMN_CARD_TITLE TEXT,
                raw_logs TEXT
            )
        """.trimIndent()

        val createHistoryTable = """
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_HISTORY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_HISTORY_CARD_PAN TEXT,
                $COLUMN_HISTORY_TICKET_TYPE TEXT,
                $COLUMN_HISTORY_STATION TEXT,
                $COLUMN_HISTORY_TIMESTAMP TEXT,
                $COLUMN_HISTORY_AMOUNT REAL,
                $COLUMN_HISTORY_BALANCE REAL,
                $COLUMN_HISTORY_RAW_HEX TEXT,
                $COLUMN_HISTORY_ACQUIRER_ID INTEGER DEFAULT 0,
                $COLUMN_HISTORY_OPERATOR_ID INTEGER DEFAULT 0,
                $COLUMN_HISTORY_TERMINAL_ID INTEGER DEFAULT 0,
                FOREIGN KEY($COLUMN_HISTORY_CARD_PAN) REFERENCES $TABLE_CARDS($COLUMN_PAN) ON DELETE CASCADE
            )
        """.trimIndent()

        val createRawRecordsTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_RAW_RECORDS (
                $COLUMN_RAW_RECORD_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_RAW_RECORD_CARD_PAN TEXT,
                $COLUMN_RAW_RECORD_SFI INTEGER,
                $COLUMN_RAW_RECORD_NUM INTEGER,
                $COLUMN_RAW_RECORD_HEX TEXT,
                FOREIGN KEY($COLUMN_RAW_RECORD_CARD_PAN) REFERENCES $TABLE_CARDS($COLUMN_PAN) ON DELETE CASCADE
            )
        """.trimIndent()

        db.execSQL(createCardsTable)
        db.execSQL(createHistoryTable)
        db.execSQL(createRawRecordsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RAW_RECORDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CARDS")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL("ALTER TABLE cards ADD COLUMN raw_logs TEXT")
        } catch (_: Exception) {}
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_RAW_RECORDS (
                    $COLUMN_RAW_RECORD_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_RAW_RECORD_CARD_PAN TEXT,
                    $COLUMN_RAW_RECORD_SFI INTEGER,
                    $COLUMN_RAW_RECORD_NUM INTEGER,
                    $COLUMN_RAW_RECORD_HEX TEXT,
                    FOREIGN KEY($COLUMN_RAW_RECORD_CARD_PAN) REFERENCES $TABLE_CARDS($COLUMN_PAN) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // 1. Auto-correct legacy records affected by card EEPROM/status bugs
            db.execSQL(
                "UPDATE $TABLE_HISTORY SET $COLUMN_HISTORY_TICKET_TYPE = 'Entry' WHERE $COLUMN_HISTORY_TICKET_TYPE = 'Exit' AND $COLUMN_HISTORY_AMOUNT = 0.0"
            )
            db.execSQL(
                "UPDATE $TABLE_HISTORY SET $COLUMN_HISTORY_TICKET_TYPE = 'Exit' WHERE $COLUMN_HISTORY_TICKET_TYPE = 'Entry' AND $COLUMN_HISTORY_AMOUNT > 0.0"
            )

            // Update legacy status strings to match updated card reader strings
            db.execSQL(
                "UPDATE $TABLE_HISTORY SET $COLUMN_HISTORY_TICKET_TYPE = 'Penalty' WHERE $COLUMN_HISTORY_TICKET_TYPE = 'Penalty Apply'"
            )
            db.execSQL(
                "UPDATE $TABLE_HISTORY SET $COLUMN_HISTORY_TICKET_TYPE = 'Ticket' WHERE $COLUMN_HISTORY_TICKET_TYPE IN ('One Tap / Ticket', 'ONE_TAP', 'ONE TAP')"
            )

            // 2. Remove duplicate history entries caused by status mismatch between old and new scans
            val deduplicateQuery = """
                DELETE FROM $TABLE_HISTORY 
                WHERE $COLUMN_HISTORY_ID NOT IN (
                    SELECT MIN($COLUMN_HISTORY_ID) 
                    FROM $TABLE_HISTORY 
                    GROUP BY $COLUMN_HISTORY_CARD_PAN, $COLUMN_HISTORY_TIMESTAMP, $COLUMN_HISTORY_AMOUNT, 
                             (CASE WHEN $COLUMN_HISTORY_TERMINAL_ID > 0 THEN $COLUMN_HISTORY_TERMINAL_ID ELSE $COLUMN_HISTORY_STATION END)
                )
            """.trimIndent()
            db.execSQL(deduplicateQuery)
        } catch (_: Exception) {}
    }

    /**
     * Inserts a new card or updates an existing card record in SQLite database.
     * Also updates ATC, card balance, appends ticket history, and saves raw SFI records read during scan.
     */
    fun saveOrUpdateCard(card: NcmcCard) {
        val db = writableDatabase
        db.transaction {
            try {
                // Update or insert card details (updating ATC, balance, and metadata without deleting the card row)
                val cardValues = ContentValues().apply {
                    put(COLUMN_PAN, card.pan)
                    put(COLUMN_EXPIRY_DATE, card.expiryDate)
                    put(COLUMN_EFFECTIVE_DATE, card.effectiveDate)
                    put(COLUMN_NCMC_VERSION, card.ncmcVersion)
                    put(COLUMN_CARD_LANGUAGE, card.cardLanguage)
                    put(COLUMN_ATC, card.atc)
                    put(COLUMN_CURRENT_BALANCE, card.currentBalance)
                    put(
                        COLUMN_LAST_SCANNED_AT,
                        if (card.lastScannedAt > 0) card.lastScannedAt else System.currentTimeMillis()
                    )
                    put(COLUMN_CARD_TITLE, card.cardTitle)
                    put("raw_logs", card.rawScanLogs.joinToString("\n"))
                }

                val rowsUpdated = update(
                    TABLE_CARDS,
                    cardValues,
                    "$COLUMN_PAN = ?",
                    arrayOf(card.pan)
                )

                if (rowsUpdated == 0) {
                    insert(TABLE_CARDS, null, cardValues)
                }

                // Save or update ticket history items avoiding redundant duplicates
                for (item in card.ticketHistory) {
                    val existingId = findMatchingHistoryId(this, card.pan, item)
                    if (existingId == null) {
                        val historyValues = ContentValues().apply {
                            put(COLUMN_HISTORY_CARD_PAN, card.pan)
                            put(COLUMN_HISTORY_TICKET_TYPE, item.ticketType)
                            put(COLUMN_HISTORY_STATION, item.stationOrLocation)
                            put(COLUMN_HISTORY_TIMESTAMP, item.timestamp)
                            put(COLUMN_HISTORY_AMOUNT, item.amount)
                            put(COLUMN_HISTORY_BALANCE, item.balance)
                            put(COLUMN_HISTORY_RAW_HEX, item.rawHex)
                            put(COLUMN_HISTORY_ACQUIRER_ID, item.acquirerId)
                            put(COLUMN_HISTORY_OPERATOR_ID, item.operatorId)
                            put(COLUMN_HISTORY_TERMINAL_ID, item.terminalId)
                        }
                        insert(TABLE_HISTORY, null, historyValues)
                    } else {
                        // Update existing history entry with corrected ticketType and rawHex/station details
                        val updateValues = ContentValues().apply {
                            put(COLUMN_HISTORY_TICKET_TYPE, item.ticketType)
                            if (item.rawHex.isNotEmpty()) put(COLUMN_HISTORY_RAW_HEX, item.rawHex)
                            if (item.acquirerId > 0) put(
                                COLUMN_HISTORY_ACQUIRER_ID,
                                item.acquirerId
                            )
                            if (item.operatorId > 0) put(
                                COLUMN_HISTORY_OPERATOR_ID,
                                item.operatorId
                            )
                            if (item.terminalId > 0) put(
                                COLUMN_HISTORY_TERMINAL_ID,
                                item.terminalId
                            )
                        }
                        update(
                            TABLE_HISTORY,
                            updateValues,
                            "$COLUMN_HISTORY_ID = ?",
                            arrayOf(existingId.toString())
                        )
                    }
                }

                // Save or update raw SFI records read during scan
                for (record in card.rawRecords) {
                    if (record.rawHex.isNotBlank()) {
                        val exists = isRawRecordExists(this, card.pan, record.sfi, record.recordNum)
                        if (!exists) {
                            val recValues = ContentValues().apply {
                                put(COLUMN_RAW_RECORD_CARD_PAN, card.pan)
                                put(COLUMN_RAW_RECORD_SFI, record.sfi)
                                put(COLUMN_RAW_RECORD_NUM, record.recordNum)
                                put(COLUMN_RAW_RECORD_HEX, record.rawHex)
                            }
                            insert(TABLE_RAW_RECORDS, null, recValues)
                        } else {
                            val updateValues = ContentValues().apply {
                                put(COLUMN_RAW_RECORD_HEX, record.rawHex)
                            }
                            update(
                                TABLE_RAW_RECORDS,
                                updateValues,
                                "$COLUMN_RAW_RECORD_CARD_PAN = ? AND $COLUMN_RAW_RECORD_SFI = ? AND $COLUMN_RAW_RECORD_NUM = ?",
                                arrayOf(
                                    card.pan,
                                    record.sfi.toString(),
                                    record.recordNum.toString()
                                )
                            )
                        }
                    }
                }

            } finally {
            }
        }
    }

    private fun isRawRecordExists(db: SQLiteDatabase, pan: String, sfi: Int, recordNum: Int): Boolean {
        val query = """
            SELECT COUNT(*) FROM $TABLE_RAW_RECORDS 
            WHERE $COLUMN_RAW_RECORD_CARD_PAN = ? 
            AND $COLUMN_RAW_RECORD_SFI = ? 
            AND $COLUMN_RAW_RECORD_NUM = ?
        """.trimIndent()
        val cursor = db.rawQuery(query, arrayOf(pan, sfi.toString(), recordNum.toString()))
        var exists = false
        if (cursor.moveToFirst()) {
            exists = cursor.getInt(0) > 0
        }
        cursor.close()
        return exists
    }

    private fun findMatchingHistoryId(db: SQLiteDatabase, pan: String, item: TicketHistoryItem): Long? {
        val query = if (item.terminalId > 0) {
            """
                SELECT $COLUMN_HISTORY_ID FROM $TABLE_HISTORY 
                WHERE $COLUMN_HISTORY_CARD_PAN = ? 
                AND $COLUMN_HISTORY_TIMESTAMP = ? 
                AND ABS($COLUMN_HISTORY_AMOUNT - ?) < 0.001
                AND $COLUMN_HISTORY_TERMINAL_ID = ?
                LIMIT 1
            """.trimIndent()
        } else {
            """
                SELECT $COLUMN_HISTORY_ID FROM $TABLE_HISTORY 
                WHERE $COLUMN_HISTORY_CARD_PAN = ? 
                AND $COLUMN_HISTORY_TIMESTAMP = ? 
                AND ABS($COLUMN_HISTORY_AMOUNT - ?) < 0.001
                AND $COLUMN_HISTORY_STATION = ?
                LIMIT 1
            """.trimIndent()
        }

        val args = if (item.terminalId > 0) {
            arrayOf(pan, item.timestamp, item.amount.toString(), item.terminalId.toString())
        } else {
            arrayOf(pan, item.timestamp, item.amount.toString(), item.stationOrLocation)
        }

        val cursor = db.rawQuery(query, args)
        var matchedId: Long? = null
        if (cursor.moveToFirst()) {
            matchedId = cursor.getLong(0)
        }
        cursor.close()

        if (matchedId == null) {
            val fallbackQuery = """
                SELECT $COLUMN_HISTORY_ID FROM $TABLE_HISTORY 
                WHERE $COLUMN_HISTORY_CARD_PAN = ? 
                AND $COLUMN_HISTORY_TIMESTAMP = ? 
                AND ABS($COLUMN_HISTORY_AMOUNT - ?) < 0.001
                LIMIT 1
            """.trimIndent()
            val fallbackCursor = db.rawQuery(fallbackQuery, arrayOf(pan, item.timestamp, item.amount.toString()))
            if (fallbackCursor.moveToFirst()) {
                matchedId = fallbackCursor.getLong(0)
            }
            fallbackCursor.close()
        }

        return matchedId
    }

    /**
     * Returns all saved NCMC cards with their ticket history.
     */
    fun getAllCards(): List<NcmcCard> {
        val db = readableDatabase
        val cardList = mutableListOf<NcmcCard>()

        val cursor = db.query(
            TABLE_CARDS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_LAST_SCANNED_AT DESC"
        )

        while (cursor.moveToNext()) {
            val pan = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PAN))
            val expiryDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXPIRY_DATE))
            val effectiveDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EFFECTIVE_DATE))
            val ncmcVersion = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NCMC_VERSION))
            val cardLanguage = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CARD_LANGUAGE))
            val atc = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ATC))
            val currentBalance = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_BALANCE))
            val lastScannedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_SCANNED_AT))
            val cardTitleIdx = cursor.getColumnIndex(COLUMN_CARD_TITLE)
            val cardTitle = if (cardTitleIdx != -1) cursor.getString(cardTitleIdx) ?: "RuPay NCMC Card" else "RuPay NCMC Card"
            val rawLogsIdx = cursor.getColumnIndex("raw_logs")
            val rawLogsStr = if (rawLogsIdx != -1) cursor.getString(rawLogsIdx) else ""
            val rawScanLogs = if (!rawLogsStr.isNullOrBlank()) rawLogsStr.split("\n") else emptyList()

            val history = getHistoryForCard(db, pan)
            val rawRecords = getRawRecordsForCard(db, pan)

            cardList.add(
                NcmcCard(
                    pan = pan,
                    expiryDate = expiryDate,
                    effectiveDate = effectiveDate,
                    ncmcVersion = ncmcVersion,
                    cardLanguage = cardLanguage,
                    atc = atc,
                    currentBalance = currentBalance,
                    lastScannedAt = lastScannedAt,
                    ticketHistory = history,
                    rawRecords = rawRecords,
                    cardTitle = cardTitle,
                    rawScanLogs = rawScanLogs
                )
            )
        }
        cursor.close()
        return cardList
    }

    /**
     * Gets a single card by PAN with its ticket history.
     */
    fun getCardByPan(pan: String): NcmcCard? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CARDS,
            null,
            "$COLUMN_PAN = ?",
            arrayOf(pan),
            null,
            null,
            null
        )

        var card: NcmcCard? = null
        if (cursor.moveToFirst()) {
            val expiryDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXPIRY_DATE))
            val effectiveDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EFFECTIVE_DATE))
            val ncmcVersion = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NCMC_VERSION))
            val cardLanguage = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CARD_LANGUAGE))
            val atc = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ATC))
            val currentBalance = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_BALANCE))
            val lastScannedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_SCANNED_AT))
            val cardTitleIdx = cursor.getColumnIndex(COLUMN_CARD_TITLE)
            val cardTitle = if (cardTitleIdx != -1) cursor.getString(cardTitleIdx) ?: "RuPay NCMC Card" else "RuPay NCMC Card"
            val rawLogsIdx = cursor.getColumnIndex("raw_logs")
            val rawLogsStr = if (rawLogsIdx != -1) cursor.getString(rawLogsIdx) else ""
            val rawScanLogs = if (!rawLogsStr.isNullOrBlank()) rawLogsStr.split("\n") else emptyList()

            val history = getHistoryForCard(db, pan)
            val rawRecords = getRawRecordsForCard(db, pan)

            card = NcmcCard(
                pan = pan,
                expiryDate = expiryDate,
                effectiveDate = effectiveDate,
                ncmcVersion = ncmcVersion,
                cardLanguage = cardLanguage,
                atc = atc,
                currentBalance = currentBalance,
                lastScannedAt = lastScannedAt,
                ticketHistory = history,
                rawRecords = rawRecords,
                cardTitle = cardTitle,
                rawScanLogs = rawScanLogs
            )
        }
        cursor.close()
        return card
    }

    private fun getRawRecordsForCard(db: SQLiteDatabase, pan: String): List<RawRecordItem> {
        val recordsList = mutableListOf<RawRecordItem>()
        val cursor = db.query(
            TABLE_RAW_RECORDS,
            null,
            "$COLUMN_RAW_RECORD_CARD_PAN = ?",
            arrayOf(pan),
            null,
            null,
            "$COLUMN_RAW_RECORD_SFI ASC, $COLUMN_RAW_RECORD_NUM ASC"
        )
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RAW_RECORD_ID))
            val sfi = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RAW_RECORD_SFI))
            val recordNum = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RAW_RECORD_NUM))
            val rawHex = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RAW_RECORD_HEX)) ?: ""

            recordsList.add(
                RawRecordItem(
                    id = id,
                    cardPan = pan,
                    sfi = sfi,
                    recordNum = recordNum,
                    rawHex = rawHex
                )
            )
        }
        cursor.close()
        return recordsList
    }

    private fun getHistoryForCard(db: SQLiteDatabase, pan: String): List<TicketHistoryItem> {
        val historyList = mutableListOf<TicketHistoryItem>()
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            "$COLUMN_HISTORY_CARD_PAN = ?",
            arrayOf(pan),
            null,
            null,
            "$COLUMN_HISTORY_TIMESTAMP DESC, $COLUMN_HISTORY_ID DESC"
        )

        val acqIdIdx = cursor.getColumnIndex(COLUMN_HISTORY_ACQUIRER_ID)
        val opIdIdx = cursor.getColumnIndex(COLUMN_HISTORY_OPERATOR_ID)
        val termIdIdx = cursor.getColumnIndex(COLUMN_HISTORY_TERMINAL_ID)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_ID))
            val ticketType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_TICKET_TYPE))
            val station = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_STATION))
            val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_TIMESTAMP))
            val amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_AMOUNT))
            val balance = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_BALANCE))
            val rawHex = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_RAW_HEX)) ?: ""

            val acqId = if (acqIdIdx != -1) cursor.getInt(acqIdIdx) else 0
            val opId = if (opIdIdx != -1) cursor.getInt(opIdIdx) else 0
            val termId = if (termIdIdx != -1) cursor.getInt(termIdIdx) else 0

            historyList.add(
                TicketHistoryItem(
                    id = id,
                    cardPan = pan,
                    ticketType = ticketType,
                    stationOrLocation = station,
                    timestamp = timestamp,
                    amount = amount,
                    balance = balance,
                    rawHex = rawHex,
                    acquirerId = acqId,
                    operatorId = opId,
                    terminalId = termId
                )
            )
        }
        cursor.close()
        return historyList.sortedByDescending { it.timestamp }
    }

    /**
     * Deletes a card and its history from database.
     */
    fun deleteCard(pan: String) {
        val db = writableDatabase
        db.delete(TABLE_CARDS, "$COLUMN_PAN = ?", arrayOf(pan))
    }

    /**
     * Clears all cards and history.
     */
    fun clearAll() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_HISTORY")
        db.execSQL("DELETE FROM $TABLE_CARDS")
    }
}
