package com.w0qs1.ncmcreader.nfc

import java.security.SecureRandom
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun ByteArray.toHexString(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        sb.append(String.format(Locale.US, "%02X", b))
    }
    return sb.toString()
}

fun String.hexToByteArray(): ByteArray {
    val clean = this.replace(" ", "").replace(":", "").uppercase(Locale.US)
    val len = clean.length
    if (len % 2 != 0) return byteArrayOf()
    val data = ByteArray(len / 2)
    for (i in 0 until len step 2) {
        data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
    }
    return data
}

fun readUInt16(data: ByteArray, offset: Int): Int {
    if (offset + 1 >= data.size) return 0
    return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}

fun readUInt24(data: ByteArray, offset: Int): Int {
    if (offset + 2 >= data.size) return 0
    return ((data[offset].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() and 0xFF)
}

data class ApduResponse(val data: ByteArray, val sw1: Int, val sw2: Int) {
    val sw: Int = (sw1 shl 8) or sw2
    val isSuccess: Boolean = sw == 0x9000

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApduResponse

        if (!data.contentEquals(other.data)) return false
        if (sw1 != other.sw1) return false
        if (sw2 != other.sw2) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sw1
        result = 31 * result + sw2
        return result
    }

    companion object {
        fun fromBytes(response: ByteArray): ApduResponse {
            if (response.size < 2) {
                return ApduResponse(byteArrayOf(), 0, 0)
            }
            val dataLen = response.size - 2
            val data = ByteArray(dataLen)
            System.arraycopy(response, 0, data, 0, dataLen)
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF
            return ApduResponse(data, sw1, sw2)
        }
    }
}

object ApduUtils {
    fun createSelectApdu(aid: ByteArray): ByteArray {
        val apdu = ByteArray(5 + aid.size + 1)
        apdu[0] = 0x00.toByte() // CLA
        apdu[1] = 0xA4.toByte() // INS (SELECT)
        apdu[2] = 0x04.toByte() // P1
        apdu[3] = 0x00.toByte() // P2
        apdu[4] = aid.size.toByte() // Lc
        System.arraycopy(aid, 0, apdu, 5, aid.size)
        apdu[apdu.size - 1] = 0x00.toByte() // Le
        return apdu
    }

    fun createReadRecordApdu(recordNum: Int, sfi: Int): ByteArray {
        val p2 = ((sfi shl 3) or 0x04) and 0xFF
        return byteArrayOf(
            0x00.toByte(),
            0xB2.toByte(),
            recordNum.toByte(),
            p2.toByte(),
            0x00.toByte()
        )
    }

    fun createGetDataApdu(tag1: Int, tag2: Int): ByteArray {
        return byteArrayOf(
            0x80.toByte(),
            0xCA.toByte(),
            tag1.toByte(),
            tag2.toByte(),
            0x00.toByte()
        )
    }

    /**
     * Builds GPO APDU with crafted PDOL data from get_balance.py reference:
     * Tag 9A (3B system date YYMMDD BCD in local time)
     * Tag 9F21 (3B system time HHMMSS BCD in local time)
     * Tag 9F37 (4B random bytes)
     */
    fun buildPdolGpoCommand(): ByteArray {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        val yy = String.format(Locale.US, "%02d", cal.get(Calendar.YEAR) % 100)
        val mm = String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)
        val dd = String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))
        val hh = String.format(Locale.US, "%02d", cal.get(Calendar.HOUR_OF_DAY))
        val min = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))
        val ss = String.format(Locale.US, "%02d", cal.get(Calendar.SECOND))

        val dateBcd = "$yy$mm$dd".hexToByteArray() // 3B
        val timeBcd = "$hh$min$ss".hexToByteArray() // 3B

        val randomBytes = ByteArray(4)
        SecureRandom().nextBytes(randomBytes) // 4B

        val prefix = "FF80F00001004000000000084800020743".hexToByteArray()
        val suffix = "FF013131323834353833".hexToByteArray()

        val pdolData = prefix + dateBcd + timeBcd + randomBytes + suffix
        val cmdData = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData

        val apdu = ByteArray(5 + cmdData.size + 1)
        apdu[0] = 0x80.toByte()
        apdu[1] = 0xA8.toByte()
        apdu[2] = 0x00.toByte()
        apdu[3] = 0x00.toByte()
        apdu[4] = cmdData.size.toByte()
        System.arraycopy(cmdData, 0, apdu, 5, cmdData.size)
        apdu[apdu.size - 1] = 0x00.toByte()
        return apdu
    }
}

fun maskPan(pan: String): String {
    val digits = pan.filter { it.isDigit() }
    if (digits.length < 10) return pan

    val first6 = digits.take(6)
    val last4 = digits.takeLast(4)
    val middleCount = digits.length - 6 - 4
    val maskedDigits = first6 + "x".repeat(middleCount.coerceAtLeast(0)) + last4

    val formatted = StringBuilder()
    for (i in maskedDigits.indices) {
        if (i > 0 && i % 4 == 0) formatted.append(" ")
        formatted.append(maskedDigits[i])
    }
    return formatted.toString()
}

fun maskTimestamp(timestamp: String, maskTime: Boolean, maskDate: Boolean): String {
    if (!maskTime) return timestamp

    val parts = timestamp.split(" ", "T")
    val datePart = parts.getOrNull(0) ?: ""
    val timePart = parts.getOrNull(1) ?: ""

    val maskedDate = if (maskDate) {
        datePart.replace(Regex("[0-9a-zA-Z]"), "X")
    } else {
        datePart
    }

    val maskedTime = if (timePart.isNotBlank()) {
        timePart.replace(Regex("[0-9a-zA-Z]"), "x")
    } else {
        timePart
    }

    return if (maskedTime.isNotBlank()) {
        "$maskedDate $maskedTime".trim()
    } else {
        maskedDate
    }
}
