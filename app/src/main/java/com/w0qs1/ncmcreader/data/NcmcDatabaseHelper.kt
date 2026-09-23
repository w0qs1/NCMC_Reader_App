package com.w0qs1.ncmcreader.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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
                $COLUMN_CARD_TITLE TEXT
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

        db.execSQL(createCardsTable)
        db.execSQL(createHistoryTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CARDS")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    /**
     * Inserts a new card or updates an existing card record in SQLite database.
     * Also updates and appends ticket history items.
     */
    fun saveOrUpdateCard(card: NcmcCard) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Save or replace card details
            val cardValues = ContentValues().apply {
                put(COLUMN_PAN, card.pan)
                put(COLUMN_EXPIRY_DATE, card.expiryDate)
                put(COLUMN_EFFECTIVE_DATE, card.effectiveDate)
                put(COLUMN_NCMC_VERSION, card.ncmcVersion)
                put(COLUMN_CARD_LANGUAGE, card.cardLanguage)
                put(COLUMN_ATC, card.atc)
                put(COLUMN_CURRENT_BALANCE, card.currentBalance)
                put(COLUMN_LAST_SCANNED_AT, System.currentTimeMillis())
                put(COLUMN_CARD_TITLE, card.cardTitle)
            }
            db.insertWithOnConflict(
                TABLE_CARDS,
                null,
                cardValues,
                SQLiteDatabase.CONFLICT_REPLACE
            )

            // Save ticket history items avoiding duplicates
            for (item in card.ticketHistory) {
                val exists = isHistoryItemExists(db, card.pan, item)
                if (!exists) {
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
                    db.insert(TABLE_HISTORY, null, historyValues)
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun isHistoryItemExists(db: SQLiteDatabase, pan: String, item: TicketHistoryItem): Boolean {
        val query = """
            SELECT COUNT(*) FROM $TABLE_HISTORY 
            WHERE $COLUMN_HISTORY_CARD_PAN = ? 
            AND $COLUMN_HISTORY_TICKET_TYPE = ? 
            AND $COLUMN_HISTORY_STATION = ? 
            AND $COLUMN_HISTORY_TIMESTAMP = ? 
            AND $COLUMN_HISTORY_AMOUNT = ?
        """.trimIndent()
        
        val cursor = db.rawQuery(
            query,
            arrayOf(pan, item.ticketType, item.stationOrLocation, item.timestamp, item.amount.toString())
        )
        var exists = false
        if (cursor.moveToFirst()) {
            exists = cursor.getInt(0) > 0
        }
        cursor.close()
        return exists
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

            val history = getHistoryForCard(db, pan)

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
                    cardTitle = cardTitle
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

            val history = getHistoryForCard(db, pan)

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
                cardTitle = cardTitle
            )
        }
        cursor.close()
        return card
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
