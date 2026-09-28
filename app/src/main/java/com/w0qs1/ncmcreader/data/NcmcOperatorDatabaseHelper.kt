package com.w0qs1.ncmcreader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

data class ResolvedStation(
    val operatorName: String,
    val transitMode: String,
    val stationName: String
)

class NcmcOperatorDatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "NCMC_OP_DB"
        private const val DATABASE_NAME = "ncmc.db"
        private const val DATABASE_VERSION = 2
    }

    init {
        copyDatabaseFromAssetsIfPresent()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createOperators = """
            CREATE TABLE IF NOT EXISTS operators (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                mode TEXT NOT NULL
            )
        """.trimIndent()

        val createStations = """
            CREATE TABLE IF NOT EXISTS stations (
                id INTEGER PRIMARY KEY,
                operator_id TEXT NOT NULL,
                reader_id TEXT,
                stop_name TEXT NOT NULL DEFAULT '',
                comments TEXT NOT NULL DEFAULT '',
                UNIQUE (operator_id, reader_id),
                FOREIGN KEY (operator_id) REFERENCES operators (id)
            )
        """.trimIndent()

        val createIndex = "CREATE INDEX IF NOT EXISTS idx_stations_operator ON stations (operator_id);"

        db.execSQL(createOperators)
        db.execSQL(createStations)
        db.execSQL(createIndex)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Assets copy handles database refresh
    }

    private fun copyDatabaseFromAssetsIfPresent() {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        try {
            val assetManager = context.assets
            val list = assetManager.list("") ?: emptyArray()
            if (DATABASE_NAME in list) {
                dbFile.parentFile?.mkdirs()
                val inputStream: InputStream = assetManager.open(DATABASE_NAME)
                val outputStream = FileOutputStream(dbFile)
                val buffer = ByteArray(1024 * 8)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                Log.d(TAG, "Copied $DATABASE_NAME from assets successfully.")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Asset $DATABASE_NAME not copied: ${e.message}")
        }
    }

    /**
     * Resolves operator and station name using exact match or wildcard match (fewest '?' winning).
     */
    fun resolveStation(acquirerId: Int, operatorId: Int, terminalId: Int): ResolvedStation? {
        val db = try {
            readableDatabase
        } catch (e: Exception) {
            Log.e(TAG, "Error opening operator database: ${e.message}")
            return null
        }

        val opHex = String.format(Locale.US, "%02X%04X", acquirerId, operatorId)
        val termHex = String.format(Locale.US, "%06X", terminalId)

        // Query stations with wildcard matching where fewest '?' wildcards win
        val stationQuery = """
            SELECT s.stop_name, o.name, o.mode,
                   (length(s.reader_id) - length(replace(s.reader_id, '?', ''))) AS wildcard_count
            FROM stations s
            JOIN operators o ON o.id = s.operator_id
            WHERE s.operator_id = ?
              AND ? GLOB s.reader_id
            ORDER BY wildcard_count ASC
            LIMIT 1
        """.trimIndent()

        try {
            val cursor = db.rawQuery(stationQuery, arrayOf(opHex, termHex))
            if (cursor.moveToFirst()) {
                val stopName = cursor.getString(0)
                val opName = cursor.getString(1)
                val mode = cursor.getString(2)
                cursor.close()
                return if (stopName.isNotBlank()) {
                    ResolvedStation(opName, mode, stopName)
                } else {
                    ResolvedStation(opName, mode, "Terminal #0x$termHex")
                }
            }
            cursor.close()

            // If station not matched, try finding operator name
            val opQuery = "SELECT name, mode FROM operators WHERE id = ?"
            val opCursor = db.rawQuery(opQuery, arrayOf(opHex))
            if (opCursor.moveToFirst()) {
                val opName = opCursor.getString(0)
                val mode = opCursor.getString(1)
                opCursor.close()
                return ResolvedStation(opName, mode, "Terminal #0x$termHex")
            }
            opCursor.close()
        } catch (e: Exception) {
            Log.d(TAG, "Query error resolving station for $opHex / $termHex: ${e.message}")
        }

        return null
    }
}
