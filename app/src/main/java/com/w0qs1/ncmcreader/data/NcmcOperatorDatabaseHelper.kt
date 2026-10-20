package com.w0qs1.ncmcreader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.content.edit
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

data class TransitLine(
    val name: String,
    val colour: String? // e.g. "0072BC"
)

data class ResolvedStation(
    val operatorName: String,
    val transitMode: String,
    val stationName: String,
    val lines: List<TransitLine> = emptyList()
)

class NcmcOperatorDatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "NCMC_OP_DB"
        private const val DATABASE_NAME = "ncmc.db"
        private const val DATABASE_VERSION = 3
        private const val PREFS_NAME = "ncmc_op_db_prefs"
        private const val KEY_ETAG = "db_etag"
        private const val DB_GITHUB_URL = "https://raw.githubusercontent.com/w0qs1/NCMC_IDs/main/data/ncmc.db"
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

        val createLines = """
            CREATE TABLE IF NOT EXISTS lines (
                id INTEGER PRIMARY KEY,
                operator_id TEXT NOT NULL,
                name TEXT NOT NULL COLLATE NOCASE,
                colour TEXT,
                UNIQUE (operator_id, name),
                FOREIGN KEY (operator_id) REFERENCES operators (id)
            )
        """.trimIndent()

        val createStationLines = """
            CREATE TABLE IF NOT EXISTS station_lines (
                station_id INTEGER NOT NULL,
                line_id INTEGER NOT NULL,
                PRIMARY KEY (station_id, line_id),
                FOREIGN KEY (station_id) REFERENCES stations (id) ON DELETE CASCADE,
                FOREIGN KEY (line_id) REFERENCES lines (id) ON DELETE CASCADE
            )
        """.trimIndent()

        val createStationLinesIndex = "CREATE INDEX IF NOT EXISTS idx_station_lines_line ON station_lines (line_id);"

        db.execSQL(createOperators)
        db.execSQL(createStations)
        db.execSQL(createIndex)
        db.execSQL(createLines)
        db.execSQL(createStationLines)
        db.execSQL(createStationLinesIndex)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < newVersion) {
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
                db.close()
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                if (dbFile.exists()) {
                    dbFile.delete()
                }
                File(dbFile.path + "-wal").takeIf { it.exists() }?.delete()
                File(dbFile.path + "-journal").takeIf { it.exists() }?.delete()

                val assetManager = context.assets
                assetManager.open(DATABASE_NAME).use { inputStream ->
                    FileOutputStream(dbFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "Refreshed $DATABASE_NAME from assets during upgrade to version $newVersion.")
            } catch (_: Exception) {
                Log.e(TAG, "Error refreshing database on upgrade")
            }
        }
    }

    private fun copyDatabaseFromAssetsIfPresent() {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        var needsCopy = (!dbFile.exists()) || (dbFile.length() == 0L)
        if (!needsCopy) {
            try {
                SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    if (db.version < DATABASE_VERSION) {
                        needsCopy = true
                    }
                }
            } catch (_: Exception) {
                needsCopy = true
            }
        }

        if (needsCopy) {
            try {
                if (dbFile.exists()) {
                    dbFile.delete()
                }
                File(dbFile.path + "-wal").takeIf { it.exists() }?.delete()
                File(dbFile.path + "-journal").takeIf { it.exists() }?.delete()

                val assetManager = context.assets
                val list = assetManager.list("") ?: emptyArray()
                if (DATABASE_NAME in list) {
                    dbFile.parentFile?.mkdirs()
                    assetManager.open(DATABASE_NAME).use { inputStream ->
                        FileOutputStream(dbFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Log.d(TAG, "Copied/Refreshed $DATABASE_NAME from assets successfully (version $DATABASE_VERSION).")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Asset $DATABASE_NAME copy failed: ${e.message}")
            }
        }
    }

    /**
     * Asynchronously checks GitHub for an updated version of ncmc.db.
     * If updated (HTTP 200 with new ETag), downloads and atomically updates local database.
     */
    fun checkForRemoteUpdate(onUpdated: (() -> Unit)? = null) {
        Thread {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedEtag = prefs.getString(KEY_ETAG, "") ?: ""

                val url = URI(DB_GITHUB_URL).toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.requestMethod = "GET"

                if (savedEtag.isNotEmpty()) {
                    connection.setRequestProperty("If-None-Match", savedEtag)
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val newEtag = connection.getHeaderField("ETag") ?: ""
                    val dbFile = context.getDatabasePath(DATABASE_NAME)
                    val tmpFile = File(dbFile.parentFile, "$DATABASE_NAME.tmp")

                    dbFile.parentFile?.mkdirs()
                    connection.inputStream.use { input ->
                        FileOutputStream(tmpFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (tmpFile.exists() && tmpFile.length() > 0) {
                        synchronized(this) {
                            close()
                            if (dbFile.exists()) {
                                dbFile.delete()
                            }
                            tmpFile.renameTo(dbFile)
                        }
                        if (newEtag.isNotEmpty()) {
                            prefs.edit { putString(KEY_ETAG, newEtag) }
                        }
                        Log.d(TAG, "Successfully updated $DATABASE_NAME from GitHub (${dbFile.length()} bytes)")
                        onUpdated?.invoke()
                    }
                } else if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    Log.d(TAG, "$DATABASE_NAME is already up to date with GitHub.")
                } else {
                    Log.d(TAG, "GitHub check returned HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Runtime update check for $DATABASE_NAME skipped/failed: ${e.message}")
            }
        }.start()
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
            SELECT s.id, s.stop_name, o.name, o.mode,
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
                val stationId = cursor.getLong(0)
                val stopName = cursor.getString(1)
                val opName = cursor.getString(2)
                val mode = cursor.getString(3)
                cursor.close()

                val linesList = mutableListOf<TransitLine>()
                if (stationId > 0) {
                    val lineQuery = """
                        SELECT l.name, l.colour
                        FROM station_lines sl
                        JOIN lines l ON l.id = sl.line_id
                        WHERE sl.station_id = ?
                        ORDER BY sl.rowid
                    """.trimIndent()
                    try {
                        val lineCursor = db.rawQuery(lineQuery, arrayOf(stationId.toString()))
                        while (lineCursor.moveToNext()) {
                            val lineName = lineCursor.getString(0)
                            val lineColour = lineCursor.getString(1)
                            linesList.add(TransitLine(lineName, lineColour))
                        }
                        lineCursor.close()
                    } catch (_: Exception) {
                        Log.d(TAG, "Query error resolving lines for station $stationId")
                    }
                }

                val finalStationName = stopName.ifBlank { "Terminal 0x$termHex" }
                val resolved = ResolvedStation(opName, mode, finalStationName, linesList)
                Log.d(TAG, "resolveStation ($opHex / $termHex) -> Op: $opName, Station: $finalStationName, Lines: ${linesList.map { "${it.name} (#${it.colour})" }}")
                return resolved
            }
            cursor.close()

            // If station not matched, try finding operator name
            val opQuery = "SELECT name, mode FROM operators WHERE id = ?"
            val opCursor = db.rawQuery(opQuery, arrayOf(opHex))
            if (opCursor.moveToFirst()) {
                val opName = opCursor.getString(0)
                val mode = opCursor.getString(1)
                opCursor.close()
                val resolved = ResolvedStation(opName, mode, "Terminal 0x$termHex", emptyList())
                Log.d(TAG, "resolveStation ($opHex / $termHex) -> Op only: $opName, Station: Terminal 0x$termHex")
                return resolved
            }
            opCursor.close()
        } catch (_: Exception) {
            Log.d(TAG, "Query error resolving station for $opHex / $termHex")
        }

        return null
    }

    /**
     * Resolves station and line information by station name (fallback for legacy records without terminal IDs).
     */
    fun resolveStationByName(stationName: String): ResolvedStation? {
        val db = try {
            readableDatabase
        } catch (_: Exception) {
            return null
        }

        val cleanName = if (stationName.contains(" - ")) {
            stationName.substringAfter(" - ").trim()
        } else {
            stationName.trim()
        }

        if (cleanName.isBlank() || cleanName.startsWith("Terminal #")) return null

        val query = """
            SELECT s.id, s.stop_name, o.name, o.mode
            FROM stations s
            JOIN operators o ON o.id = s.operator_id
            WHERE s.stop_name COLLATE NOCASE = ?
            LIMIT 1
        """.trimIndent()

        try {
            val cursor = db.rawQuery(query, arrayOf(cleanName))
            if (cursor.moveToFirst()) {
                val stationId = cursor.getLong(0)
                val stopName = cursor.getString(1)
                val opName = cursor.getString(2)
                val mode = cursor.getString(3)
                cursor.close()

                val linesList = mutableListOf<TransitLine>()
                if (stationId > 0) {
                    val lineQuery = """
                        SELECT l.name, l.colour
                        FROM station_lines sl
                        JOIN lines l ON l.id = sl.line_id
                        WHERE sl.station_id = ?
                        ORDER BY sl.rowid
                    """.trimIndent()
                    try {
                        val lineCursor = db.rawQuery(lineQuery, arrayOf(stationId.toString()))
                        while (lineCursor.moveToNext()) {
                            val lineName = lineCursor.getString(0)
                            val lineColour = lineCursor.getString(1)
                            linesList.add(TransitLine(lineName, lineColour))
                        }
                        lineCursor.close()
                    } catch (_: Exception) {}
                }
                val resolved = ResolvedStation(opName, mode, stopName, linesList)
                Log.d(TAG, "resolveStationByName ($cleanName) -> Op: $opName, Station: $stopName, Lines: ${linesList.map { "${it.name} (#${it.colour})" }}")
                return resolved
            }
            cursor.close()
        } catch (_: Exception) {}

        return null
    }
}
