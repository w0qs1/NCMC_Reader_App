package com.w0qs1.ncmcreader.nfc

import android.nfc.tech.IsoDep
import android.util.Log
import com.w0qs1.ncmcreader.data.NcmcCard
import com.w0qs1.ncmcreader.data.RawRecordItem
import com.w0qs1.ncmcreader.data.TicketHistoryItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.round

fun Double.roundTo2Decimals(): Double {
    return round(this * 100.0) / 100.0
}

class NcmcCardReader(private val isoDep: IsoDep) {

    companion object {
        private const val TAG = "NCMC_READER"

        private val PPSE_AID = "325041592E5359532E4444463031".hexToByteArray() // 2PAY.SYS.DDF01
        private val PRIMARY_AID = "A0000005241010".hexToByteArray()

        private const val CSA_LENGTH = 96
        private const val HISTORY_ENTRY_LENGTH = 17

        private val LANG_MAP = mapOf(
            0 to "English",
            1 to "Hindi",
            2 to "Bengali",
            3 to "Marathi",
            4 to "Telugu",
            5 to "Tamil",
            6 to "Gujarati",
            7 to "Urdu",
            8 to "Kannada",
            9 to "Odia",
            10 to "Malayalam",
            11 to "Punjabi",
            12 to "Sanskrit",
            13 to "Assamese",
            14 to "Maithili",
            15 to "Santali",
            16 to "Kashmiri",
            17 to "Nepali",
            18 to "Sindhi",
            19 to "Dogri",
            20 to "Konkani",
            21 to "Manipuri",
            22 to "Bodo"
        )
    }

    private data class RawValEvent(
        val logRec: Int,
        val minutes: Int,
        val acquirerId: Int,
        val operatorId: Int,
        val terminalId: Int,
        val maxFareRs: Double,
        val statusStr: String
    )

    private data class RawHistEvent(
        val logRec: Int,
        val minutes: Int,
        val acquirerId: Int,
        val operatorId: Int,
        val terminalId: Int,
        val seqNum: Int,
        val amount: Double,
        val balBefore: Double,
        val statusStr: String,
        val rawHex: String
    )

    fun readCard(): NcmcCard {
        Log.d(TAG, "==================== STARTING NCMC CARD SCAN ====================")
        if (!isoDep.isConnected) {
            isoDep.connect()
        }
        isoDep.timeout = 5000

        var rawAppName = "RuPay"

        // 1. SELECT PPSE -> Read Application Label (Tag 50 / Tag 9F12)
        try {
            val ppseApdu = ApduUtils.createSelectApdu(PPSE_AID)
            val ppseResp = ApduResponse.fromBytes(isoDep.transceive(ppseApdu))
            if (ppseResp.isSuccess && ppseResp.data.isNotEmpty()) {
                val ppseTlvs = TlvParser.parse(ppseResp.data)
                val labelElem = TlvParser.findElement(ppseTlvs, "50") ?: TlvParser.findElement(ppseTlvs, "9F12")
                if (labelElem != null && labelElem.value.isNotEmpty()) {
                    rawAppName = TlvParser.parseAscii(labelElem.value)
                    Log.d(TAG, "PPSE Application Name: $rawAppName")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "SELECT PPSE skipped or failed: ${e.message}")
        }

        // 2. SELECT AID (Only valid AID: A0000005241010)
        val selectAidApdu = ApduUtils.createSelectApdu(PRIMARY_AID)
        val selectResp = ApduResponse.fromBytes(isoDep.transceive(selectAidApdu))
        Log.d(TAG, "SELECT AID A0000005241010 SW=${String.format("%04X", selectResp.sw)}")

        if (!selectResp.isSuccess || selectResp.data.isEmpty()) {
            Log.d(TAG, "Card rejected: Invalid or unsupported AID (SW=${String.format("%04X", selectResp.sw)})")
            throw IllegalArgumentException("Only NCMC cards are supported.")
        }

        val aidTlvs = TlvParser.parse(selectResp.data)
        val labelElem = TlvParser.findElement(aidTlvs, "50") ?: TlvParser.findElement(aidTlvs, "9F12")
        if (labelElem != null && labelElem.value.isNotEmpty()) {
            val name = TlvParser.parseAscii(labelElem.value)
            if (name.isNotEmpty()) {
                rawAppName = name
                Log.d(TAG, "SELECT AID Application Name: $rawAppName")
            }
        }

        // Clean App Name Prefix (e.g. "RuPay Prepaid", "RuPay Debit", "RuPay Credit")
        val cleanAppName = when {
            rawAppName.contains("Prepaid", ignoreCase = true) -> "RuPay Prepaid"
            rawAppName.contains("Debit", ignoreCase = true) -> "RuPay Debit"
            rawAppName.contains("Credit", ignoreCase = true) -> "RuPay Credit"
            else -> rawAppName.ifEmpty { "RuPay" }
        }

        // 3. READ ATC Value (Tag 9F36) BEFORE GPO
        var atcValue = 0
        try {
            val atcApdu = ApduUtils.createGetDataApdu(0x9F, 0x36)
            val atcResp = ApduResponse.fromBytes(isoDep.transceive(atcApdu))
            Log.d(TAG, "GET DATA 9F36 (ATC) Response SW=${String.format("%04X", atcResp.sw)} Data=${atcResp.data.toHexString()}")

            if (atcResp.isSuccess && atcResp.data.isNotEmpty()) {
                val atcTlvs = TlvParser.parse(atcResp.data)
                val atcElem = TlvParser.findElement(atcTlvs, "9F36")
                if (atcElem != null && atcElem.value.isNotEmpty()) {
                    atcValue = readUInt16(atcElem.value, 0)
                    Log.d(TAG, "Extracted ATC value before GPO: $atcValue")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "GET DATA 9F36 ATC read skipped or failed: ${e.message}")
        }

        // 4. PERFORM GPO with PDOL -> Extract Tag DF33 Service Balance & AFL (Tag 94)
        var df33BalanceRs: Double? = null
        var hasDf33Service = false
        var aflEntries = emptyList<AflEntry>()

        try {
            val gpoApdu = ApduUtils.buildPdolGpoCommand()
            val gpoResp = ApduResponse.fromBytes(isoDep.transceive(gpoApdu))
            Log.d(TAG, "GPO APDU Response SW=${String.format("%04X", gpoResp.sw)} Data=${gpoResp.data.toHexString()}")

            if (gpoResp.data.isNotEmpty()) {
                val gpoTlvs = TlvParser.parse(gpoResp.data)
                val df33Elem = TlvParser.findElement(gpoTlvs, "DF33")
                if (df33Elem != null && df33Elem.value.isNotEmpty()) {
                    hasDf33Service = true
                    df33BalanceRs = parseDf33Balance(df33Elem.value)
                    Log.d(TAG, "Extracted Tag DF33 Balance: ₹$df33BalanceRs")
                }

                aflEntries = parseAflFromGpo(gpoResp.data)
                Log.d(TAG, "Parsed ${aflEntries.size} AFL entries from GPO: $aflEntries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing GPO command: ${e.message}", e)
        }

        // 5. READ Card Information -> PAN, Expiry Date & Effective Date from AFL ranges and fallback SFIs
        var panStr: String? = null
        var effectiveDateStr = "N/A"
        var expiryDateStr = "N/A"
        val rawScannedRecords = mutableListOf<RawRecordItem>()

        // Dynamically build list of SFI and Record pairs to read from AFL
        val recordsToRead = mutableListOf<Pair<Int, Int>>()
        for (entry in aflEntries) {
            for (rec in entry.firstRecord..entry.lastRecord) {
                val pair = entry.sfi to rec
                if (pair !in recordsToRead) {
                    recordsToRead.add(pair)
                }
            }
        }

        // Add default fallback SFIs 1..4 records 1..3 if not already included
        val defaultRecords = listOf(
            1 to 1, 1 to 2, 1 to 3,
            2 to 1, 2 to 2, 2 to 3,
            3 to 1, 3 to 2, 3 to 3,
            4 to 1, 4 to 2, 4 to 3
        )
        for (pair in defaultRecords) {
            if (pair !in recordsToRead) {
                recordsToRead.add(pair)
            }
        }

        for ((sfi, rec) in recordsToRead) {
            try {
                val recApdu = ApduUtils.createReadRecordApdu(rec, sfi)
                val recResp = ApduResponse.fromBytes(isoDep.transceive(recApdu))
                if (recResp.isSuccess && recResp.data.isNotEmpty()) {
                    Log.d(TAG, "Read Card Info SFI $sfi Record $rec: ${recResp.data.toHexString()}")
                    rawScannedRecords.add(
                        RawRecordItem(
                            sfi = sfi,
                            recordNum = rec,
                            rawHex = recResp.data.toHexString()
                        )
                    )
                    val tlvs = TlvParser.parse(recResp.data)

                    if (!hasDf33Service) {
                        val df33Elem = TlvParser.findElement(tlvs, "DF33")
                        if (df33Elem != null) hasDf33Service = true
                    }

                    val panElem = TlvParser.findElement(tlvs, "5A")
                    if (panElem != null && panStr == null) {
                        panStr = TlvParser.decodePan(panElem.value)
                    }

                    val effElem = TlvParser.findElement(tlvs, "5F25")
                    if (effElem != null && effectiveDateStr == "N/A") {
                        effectiveDateStr = TlvParser.parseDate(effElem.value)
                    }

                    val expElem = TlvParser.findElement(tlvs, "5F24")
                    if (expElem != null && expiryDateStr == "N/A") {
                        expiryDateStr = TlvParser.parseDate(expElem.value)
                    }

                    val tr2Elem = TlvParser.findElement(tlvs, "57")
                    if (tr2Elem != null && (panStr == null || expiryDateStr == "N/A")) {
                        if (panStr == null) {
                            panStr = TlvParser.decodeTrack2Pan(tr2Elem.value)
                        }
                        if (expiryDateStr == "N/A") {
                            val tr2Hex = tr2Elem.value.toHexString().uppercase(Locale.US)
                            if ("D" in tr2Hex) {
                                val rest = tr2Hex.split("D", "=")[1]
                                if (rest.length >= 4) {
                                    val expYy = rest.substring(0, 2)
                                    val expMm = rest.substring(2, 4)
                                    expiryDateStr = "20$expYy-$expMm"
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 6. READ SFI 16 (Log Records 1..10) -> Decode Common Service Area & Language IF DF33 is present
        val rawValEvents = mutableListOf<RawValEvent>()
        val rawHistEvents = mutableListOf<RawHistEvent>()
        var detectedLanguage: String? = null
        var csaVersion: String? = null
        val effectiveDateMillis = parseEffectiveDateMillis(effectiveDateStr)

        if (hasDf33Service) {
            for (record in 1..10) {
                try {
                    val recApdu = ApduUtils.createReadRecordApdu(record, 16)
                    val recResp = ApduResponse.fromBytes(isoDep.transceive(recApdu))
                    if (recResp.isSuccess && recResp.data.isNotEmpty()) {
                        val data = recResp.data
                        Log.d(TAG, "Read SFI 16 Record $record: ${data.toHexString()}")
                        rawScannedRecords.add(
                            RawRecordItem(
                                sfi = 16,
                                recordNum = record,
                                rawHex = data.toHexString()
                            )
                        )

                        val csaData = extractCsaBytes(data, effectiveDateMillis)
                        if (csaData != null) {
                            val (valEvent, histRecords, lang, ver) = decodeCommonServiceArea(csaData, record)
                            if (detectedLanguage == null) detectedLanguage = lang
                            if (csaVersion == null && ver.isNotEmpty()) csaVersion = ver

                            valEvent?.let { rawValEvents.add(it) }
                            rawHistEvents.addAll(histRecords)
                        }
                    } else if (recResp.sw1 == 0x6A || recResp.sw1 == 0x6C) {
                        break
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error reading SFI 16 Record $record: ${e.message}")
                    break
                }
            }
        }

        // 7. Check if card is non-NCMC or NCMC service not enabled
        val cardTitle = if (hasDf33Service) "$cleanAppName NCMC Card" else cleanAppName
        val finalNcmcVersion = if (hasDf33Service) (csaVersion ?: "3.1") else "-"
        val finalLanguage = if (hasDf33Service) (detectedLanguage ?: "English") else "-"

        val ticketHistory = if (hasDf33Service) {
            processCleanHistory(
                rawValEvents = rawValEvents,
                rawHistEvents = rawHistEvents,
                effectiveDateMillis = effectiveDateMillis,
                pan = panStr ?: "NCMC-CARD"
            )
        } else {
            emptyList()
        }

        val rawBalance = if (hasDf33Service) (df33BalanceRs ?: (ticketHistory.firstOrNull()?.balance ?: 0.0)) else 0.0
        val finalBalance = rawBalance.roundTo2Decimals()

        val finalPan = panStr ?: "NCMC-CARD-${(System.currentTimeMillis() % 1000000)}"
        val finalRawRecords = rawScannedRecords.map { it.copy(cardPan = finalPan) }

        Log.d(TAG, "-------------------- EXTRACTED CARD DATA --------------------")
        Log.d(TAG, "Card Title: $cardTitle")
        Log.d(TAG, "PAN: $finalPan")
        Log.d(TAG, "Effective Date: $effectiveDateStr")
        Log.d(TAG, "Expiry Date: $expiryDateStr")
        Log.d(TAG, "NCMC Version: $finalNcmcVersion")
        Log.d(TAG, "Card Language: $finalLanguage")
        Log.d(TAG, "ATC: $atcValue")
        Log.d(TAG, "Current Balance: ₹$finalBalance")
        Log.d(TAG, "Has DF33 Service: $hasDf33Service")
        Log.d(TAG, "Parsed ${ticketHistory.size} transaction records")
        Log.d(TAG, "Read ${finalRawRecords.size} raw SFI records")
        Log.d(TAG, "=============================================================")

        return NcmcCard(
            pan = finalPan,
            expiryDate = expiryDateStr,
            effectiveDate = effectiveDateStr,
            ncmcVersion = finalNcmcVersion,
            cardLanguage = finalLanguage,
            atc = atcValue,
            currentBalance = finalBalance,
            lastScannedAt = System.currentTimeMillis(),
            ticketHistory = ticketHistory,
            rawRecords = finalRawRecords,
            cardTitle = cardTitle
        )
    }

    private fun parseDf33Balance(df33Data: ByteArray): Double? {
        if (df33Data.size < 29) return null
        val balBcdBytes = df33Data.copyOfRange(23, 29)
        val balBcdStr = balBcdBytes.toHexString()
        return try {
            val balPaise = balBcdStr.toLong()
            (balPaise / 100.0).roundTo2Decimals()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCsaBytes(data: ByteArray, effectiveDateMillis: Long): ByteArray? {
        if (data.size < CSA_LENGTH) return null

        val nowMillis = System.currentTimeMillis()
        val maxAllowedMillis = nowMillis + 86400_000L // 24 hours tolerance for time zones / clock skew

        // Sliding window: start from last 96 bytes, slide left 1 byte at a time
        val startOffset = data.size - CSA_LENGTH
        for (offset in startOffset downTo 0) {
            val candidate = data.copyOfRange(offset, offset + CSA_LENGTH)
            if (isValidCsaWindow(candidate, effectiveDateMillis, maxAllowedMillis)) {
                return candidate
            }
        }

        // Fallback to taking the last 96 bytes if sliding window check didn't match
        return data.copyOfRange(data.size - CSA_LENGTH, data.size)
    }

    private fun isValidCsaWindow(
        csaBytes: ByteArray,
        effectiveDateMillis: Long,
        maxAllowedMillis: Long
    ): Boolean {
        if (csaBytes.size != CSA_LENGTH) return false

        // 1. Check Validation Event Txn Status (upper 4 bits at byte 20)
        val valStatusByte = csaBytes[20].toInt() and 0xFF
        val valTxnStatus = (valStatusByte ushr 4) and 0x0F
        if (valTxnStatus > 3) { // Status must be 0b0000, 0b0001, 0b0010, or 0b0011
            return false
        }

        // Check Validation Event Minutes (byte 10..12)
        val valMinutes = readUInt24(csaBytes, 10)
        if (valMinutes > 0) {
            val valTimeMillis = effectiveDateMillis + (valMinutes.toLong() * 60 * 1000L)
            if (valTimeMillis > maxAllowedMillis) {
                return false
            }
        }

        // 2. Check 4 History Records (17 bytes each starting at offset 21)
        var hasValidSignal = valMinutes > 0
        for (idx in 0 until 4) {
            val offset = 21 + idx * 17
            val hMinutes = readUInt24(csaBytes, offset + 6)
            val hSeq = readUInt16(csaBytes, offset + 9)
            val hTerm = readUInt24(csaBytes, offset + 3)

            val balStatusBytes = readUInt24(csaBytes, offset + 13)
            val hStatusCode = balStatusBytes and 0x0F
            if (hStatusCode > 3) { // Status must be 0b0000, 0b0001, 0b0010, or 0b0011
                return false
            }

            if (hMinutes > 0) {
                val hTimeMillis = effectiveDateMillis + (hMinutes.toLong() * 60 * 1000L)
                if (hTimeMillis > maxAllowedMillis) {
                    return false
                }
                hasValidSignal = true
            }

            if (hSeq > 0 || hTerm > 0) {
                hasValidSignal = true
            }
        }

        return hasValidSignal
    }

    private fun decodeCommonServiceArea(
        csaBytes: ByteArray,
        recordNum: Int
    ): CsaDecodeResult {
        if (csaBytes.size != CSA_LENGTH) {
            return CsaDecodeResult(null, emptyList(), "English", "")
        }

        val verByte = csaBytes[0].toInt() and 0xFF
        val verMajor = verByte ushr 4
        val verMinor = verByte and 0x0F
        val versionStr = if (verMajor > 0) "$verMajor.$verMinor" else ""

        val langByte = csaBytes[1].toInt() and 0xFF
        val langCode = (langByte ushr 3) and 0x1F
        val cardLanguage = LANG_MAP[langCode] ?: "English"

        val valAcq = csaBytes[4].toInt() and 0xFF
        val valOp = readUInt16(csaBytes, 5)
        val valTerm = readUInt24(csaBytes, 7)
        val valMinutes = readUInt24(csaBytes, 10)
        val maxFareUnits = readUInt16(csaBytes, 13)
        val maxFareRs = (maxFareUnits / 10.0).roundTo2Decimals()
        val valStatusByte = csaBytes[20].toInt() and 0xFF
        val valTxnStatus = (valStatusByte ushr 4) and 0x0F

        val valStatusStr = mapStatusString(valTxnStatus)

        var valEvent: RawValEvent? = null
        if (valMinutes > 0 || valTerm > 0 || valAcq > 0) {
            valEvent = RawValEvent(
                logRec = recordNum,
                minutes = valMinutes,
                acquirerId = valAcq,
                operatorId = valOp,
                terminalId = valTerm,
                maxFareRs = maxFareRs,
                statusStr = valStatusStr
            )
        }

        val historyRecords = mutableListOf<RawHistEvent>()
        for (idx in 0 until 4) {
            val offset = 21 + idx * 17
            val hAcq = csaBytes[offset].toInt() and 0xFF
            val hOp = readUInt16(csaBytes, offset + 1)
            val hTerm = readUInt24(csaBytes, offset + 3)
            val hMinutes = readUInt24(csaBytes, offset + 6)
            val hSeq = readUInt16(csaBytes, offset + 9)
            val hAmtRaw = readUInt16(csaBytes, offset + 11)
            val hAmt = (hAmtRaw / 10.0).roundTo2Decimals()

            val balStatusBytes = readUInt24(csaBytes, offset + 13)
            val hBalBeforeRaw = (balStatusBytes ushr 4) and 0xFFFFF
            val hBalBefore = (hBalBeforeRaw / 10.0).roundTo2Decimals()

            val hStatusCode = balStatusBytes and 0x0F
            val hStatusStr = mapStatusString(hStatusCode, hAmt)

            if (hMinutes > 0 || hSeq > 0 || hTerm > 0) {
                val rawHex = csaBytes.copyOfRange(offset, offset + HISTORY_ENTRY_LENGTH).toHexString()
                historyRecords.add(
                    RawHistEvent(
                        logRec = recordNum,
                        minutes = hMinutes,
                        acquirerId = hAcq,
                        operatorId = hOp,
                        terminalId = hTerm,
                        seqNum = hSeq,
                        amount = hAmt,
                        balBefore = hBalBefore,
                        statusStr = hStatusStr,
                        rawHex = rawHex
                    )
                )
            }
        }

        return CsaDecodeResult(valEvent, historyRecords, cardLanguage, versionStr)
    }

    private data class CsaDecodeResult(
        val valEvent: RawValEvent?,
        val historyRecords: List<RawHistEvent>,
        val language: String,
        val version: String
    )

    private fun mapStatusString(code: Int, amount: Double = 0.0): String {
        return when (code) {
            // Code 0: If amount == 0.0, it's an Entry (fixing EEPROM bug for old entries). If amount > 0.0, it's Exit.
            0 -> if (amount == 0.0) "Entry" else "Exit"
            // Code 1: If amount > 0.0, it's an Exit (fixing cases where entry status code was set with non-zero fare).
            1 -> if (amount > 0.0) "Exit" else "Entry"
            2 -> "Penalty Apply"
            3 -> "One Tap / Ticket"
            else -> "Transaction ($code)"
        }
    }

    private fun processCleanHistory(
        rawValEvents: List<RawValEvent>,
        rawHistEvents: List<RawHistEvent>,
        effectiveDateMillis: Long,
        pan: String
    ): List<TicketHistoryItem> {
        val valMap = rawValEvents.associateBy { Triple(it.minutes, it.acquirerId, it.terminalId) }
        val histMap = rawHistEvents.associateBy { Triple(it.minutes, it.acquirerId, it.terminalId) }

        val allKeys = valMap.keys.union(histMap.keys)
        val mergedList = mutableListOf<RawMergedItem>()

        for (key in allKeys) {
            val h = histMap[key]
            val v = valMap[key]

            val mins = h?.minutes ?: v?.minutes ?: 0
            val acq = h?.acquirerId ?: v?.acquirerId ?: 0
            val op = h?.operatorId ?: v?.operatorId ?: 0
            val term = h?.terminalId ?: v?.terminalId ?: 0
            val amt = (h?.amount ?: 0.0).roundTo2Decimals()
            val balBefore = (h?.balBefore ?: 0.0).roundTo2Decimals()

            // Prioritize history record status over active validation event status
            var status = h?.statusStr ?: v?.statusStr ?: "Transaction"
            if (amt > 0.0 && status == "Entry") {
                status = "Exit"
            } else if (amt == 0.0 && status == "Exit") {
                status = "Entry"
            }

            val seq = h?.seqNum ?: 0
            val hex = h?.rawHex ?: ""

            val balAfter = (if (balBefore > 0) balBefore - amt else 0.0).roundTo2Decimals()

            mergedList.add(
                RawMergedItem(
                    minutes = mins,
                    seq = seq,
                    acquirerId = acq,
                    operatorId = op,
                    terminalId = term,
                    amount = amt,
                    balBefore = balBefore,
                    balAfter = balAfter,
                    status = status,
                    rawHex = hex
                )
            )
        }

        val sortedList = mergedList.sortedWith(
            compareByDescending<RawMergedItem> { it.minutes }.thenByDescending { it.seq }
        )

        val result = mutableListOf<TicketHistoryItem>()
        for (item in sortedList) {
            val timestampStr = formatMinutesElapsedToLocalTime(effectiveDateMillis, item.minutes)
            val defaultStationName = String.format(Locale.US, "Terminal #0x%06X", item.terminalId)

            result.add(
                TicketHistoryItem(
                    cardPan = pan,
                    ticketType = item.status,
                    stationOrLocation = defaultStationName,
                    timestamp = timestampStr,
                    amount = item.amount.roundTo2Decimals(),
                    balance = item.balAfter.roundTo2Decimals(),
                    rawHex = item.rawHex,
                    acquirerId = item.acquirerId,
                    operatorId = item.operatorId,
                    terminalId = item.terminalId
                )
            )
        }

        return result
    }

    private data class RawMergedItem(
        val minutes: Int,
        val seq: Int,
        val acquirerId: Int,
        val operatorId: Int,
        val terminalId: Int,
        val amount: Double,
        val balBefore: Double,
        val balAfter: Double,
        val status: String,
        val rawHex: String
    )

    fun parseEffectiveDateMillis(dateStr: String): Long {
        val clean = dateStr.trim()
        if (clean.isEmpty() || clean == "N/A") {
            val cal = Calendar.getInstance(TimeZone.getDefault())
            cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        return try {
            val sdf = if (clean.contains("-")) {
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            } else {
                SimpleDateFormat("yyMMdd", Locale.US)
            }
            sdf.timeZone = TimeZone.getDefault()
            val date = sdf.parse(clean)
            date?.time ?: 0L
        } catch (_: Exception) {
            val cal = Calendar.getInstance(TimeZone.getDefault())
            cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
    }

    fun formatMinutesElapsedToLocalTime(baseDateMillis: Long, minutesElapsed: Int): String {
        val txTimeMillis = baseDateMillis + (minutesElapsed.toLong() * 60 * 1000L)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(txTimeMillis))
    }
}

data class AflEntry(
    val sfi: Int,
    val firstRecord: Int,
    val lastRecord: Int,
    val offlineAuthRecords: Int
)

fun parseAflFromGpo(gpoData: ByteArray): List<AflEntry> {
    val entries = mutableListOf<AflEntry>()
    if (gpoData.isEmpty()) return entries

    var aflBytes: ByteArray? = null

    // Check Format 1 (Response Template Tag 0x80)
    if (gpoData[0] == 0x80.toByte()) {
        val parsedTlvs = TlvParser.parse(gpoData)
        val elem80 = TlvParser.findElement(parsedTlvs, "80")
        if (elem80 != null && elem80.value.size >= 6) {
            aflBytes = elem80.value.copyOfRange(2, elem80.value.size)
        }
    }

    // Check Format 2 (Response Template Tag 0x77) or Tag 94
    if (aflBytes == null) {
        val parsedTlvs = TlvParser.parse(gpoData)
        val elem94 = TlvParser.findElement(parsedTlvs, "94")
        if (elem94 != null) {
            aflBytes = elem94.value
        }
    }

    if (aflBytes != null) {
        var i = 0
        while (i + 3 < aflBytes.size) {
            val b0 = aflBytes[i].toInt() and 0xFF
            val sfi = b0 ushr 3
            val firstRec = aflBytes[i + 1].toInt() and 0xFF
            val lastRec = aflBytes[i + 2].toInt() and 0xFF
            val authRecs = aflBytes[i + 3].toInt() and 0xFF

            if (sfi in 1..31 && firstRec >= 1 && lastRec >= firstRec) {
                entries.add(AflEntry(sfi, firstRec, lastRec, authRecs))
            }
            i += 4
        }
    }

    return entries
}
