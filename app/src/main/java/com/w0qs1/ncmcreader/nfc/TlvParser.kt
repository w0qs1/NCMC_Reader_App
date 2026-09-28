package com.w0qs1.ncmcreader.nfc

import java.util.Locale

/**
 * Represents a parsed BER-TLV (Tag-Length-Value) node.
 */
data class TlvElement(
    val tag: String,
    val length: Int,
    val value: ByteArray,
    val children: List<TlvElement> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TlvElement

        if (tag != other.tag) return false
        if (length != other.length) return false
        if (!value.contentEquals(other.value)) return false
        if (children != other.children) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tag.hashCode()
        result = 31 * result + length
        result = 31 * result + value.contentHashCode()
        result = 31 * result + children.hashCode()
        return result
    }
}

object TlvParser {

    /**
     * Parses a byte array into a list of root TLV elements.
     */
    fun parse(data: ByteArray): List<TlvElement> {
        val result = mutableListOf<TlvElement>()
        var offset = 0

        while (offset < data.size) {
            // Ignore trailing null or padding bytes
            if (data[offset] == 0x00.toByte() || data[offset] == 0xFF.toByte()) {
                offset++
                continue
            }

            val tagStart = offset
            val firstByte = data[offset].toInt() and 0xFF
            offset++

            // Multi-byte tag check: if low 5 bits of first byte are 11111 (0x1F)
            val isMultiByte = (firstByte and 0x1F) == 0x1F
            if (isMultiByte) {
                while (offset < data.size) {
                    val b = data[offset].toInt() and 0xFF
                    offset++
                    // Last byte of tag has bit 8 (0x80) set to 0
                    if ((b and 0x80) == 0) {
                        break
                    }
                }
            }

            val tagBytes = data.copyOfRange(tagStart, offset)
            val tagHex = tagBytes.toHexString().uppercase(Locale.US)

            if (offset >= data.size) break

            // Parse Length
            val lenByte = data[offset].toInt() and 0xFF
            offset++

            val length: Int = when {
                lenByte < 0x80 -> lenByte
                lenByte == 0x81 -> {
                    if (offset >= data.size) break
                    val len = data[offset].toInt() and 0xFF
                    offset++
                    len
                }
                lenByte == 0x82 -> {
                    if (offset + 1 >= data.size) break
                    val len = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                    offset += 2
                    len
                }
                lenByte == 0x83 -> {
                    if (offset + 2 >= data.size) break
                    val len = ((data[offset].toInt() and 0xFF) shl 16) or
                            ((data[offset + 1].toInt() and 0xFF) shl 8) or
                            (data[offset + 2].toInt() and 0xFF)
                    offset += 3
                    len
                }
                else -> 0
            }

            if (offset + length > data.size) {
                break
            }

            val valueBytes = data.copyOfRange(offset, offset + length)
            offset += length

            val isConstructed = ((firstByte and 0x20) != 0) || isKnownConstructedTag(tagHex)
            val children = if (isConstructed && valueBytes.isNotEmpty()) {
                try {
                    parse(valueBytes)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            result.add(TlvElement(tagHex, length, valueBytes, children))
        }

        return result
    }

    private fun isKnownConstructedTag(tag: String): Boolean {
        return tag in setOf("6F", "70", "77", "A5", "BF0C", "E0", "E1", "E2", "61", "DF33")
    }

    fun findElement(elements: List<TlvElement>, tagHex: String): TlvElement? {
        val upperTag = tagHex.uppercase(Locale.US)
        for (element in elements) {
            if (element.tag.uppercase(Locale.US) == upperTag) {
                return element
            }
            if (element.children.isNotEmpty()) {
                val found = findElement(element.children, upperTag)
                if (found != null) return found
            }
        }
        return null
    }

    fun findAllElements(elements: List<TlvElement>, tagHex: String): List<TlvElement> {
        val result = mutableListOf<TlvElement>()
        val upperTag = tagHex.uppercase(Locale.US)
        for (element in elements) {
            if (element.tag.uppercase(Locale.US) == upperTag) {
                result.add(element)
            }
            if (element.children.isNotEmpty()) {
                result.addAll(findAllElements(element.children, upperTag))
            }
        }
        return result
    }

    /**
     * Decodes PAN from Tag 5A or raw bytes.
     * Metrodroid logic: data.toHexString().uppercase(), strip trailing 'F', group into 4s.
     */
    fun decodePan(value: ByteArray): String {
        val raw = value.toHexString().uppercase(Locale.US)
        val clean = if (raw.endsWith("F")) raw.substring(0, raw.length - 1) else raw
        if (clean.length < 10) return ""

        val formatted = StringBuilder()
        for (i in clean.indices) {
            if (i > 0 && i % 4 == 0) formatted.append(" ")
            formatted.append(clean[i])
        }
        return formatted.toString()
    }

    /**
     * Decodes PAN from Track 2 Equivalent Data (Tag 57).
     * Metrodroid logic: find character 'D' or '=', take digits prior to 'D', strip 'F', group in 4s.
     */
    fun decodeTrack2Pan(value: ByteArray): String {
        val raw = value.toHexString().uppercase(Locale.US)
        val panPart = raw.split("D", "=")[0]
        val clean = panPart.replace("F", "")
        if (clean.length < 10) return ""

        val formatted = StringBuilder()
        for (i in clean.indices) {
            if (i > 0 && i % 4 == 0) formatted.append(" ")
            formatted.append(clean[i])
        }
        return formatted.toString()
    }

    fun parseBcdPan(value: ByteArray): String {
        return decodePan(value)
    }

    fun parseDate(value: ByteArray): String {
        if (value.isEmpty()) return ""
        val hex = value.toHexString().uppercase(Locale.US)
        if (hex.length >= 6) {
            val yy = hex.substring(0, 2)
            val mm = hex.substring(2, 4)
            val dd = hex.substring(4, 6)
            return "20$yy-$mm-$dd"
        } else if (hex.length >= 4) {
            val yy = hex.substring(0, 2)
            val mm = hex.substring(2, 4)
            return "20$yy-$mm"
        }
        return hex
    }

    fun parseAscii(value: ByteArray): String {
        return try {
            String(value, Charsets.UTF_8).replace("\u0000", "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun parseAmount(value: ByteArray): Double {
        if (value.isEmpty()) return 0.0
        var rawLong = 0L
        for (b in value) {
            rawLong = (rawLong shl 8) or (b.toLong() and 0xFF)
        }

        if (rawLong in 0..10_000_000) {
            return rawLong / 100.0
        }

        val bcdStr = parseBcdPan(value).replace(" ", "")
        val bcdValue = bcdStr.toLongOrNull() ?: 0L
        return bcdValue / 100.0
    }

    fun parseInt(value: ByteArray): Int {
        if (value.isEmpty()) return 0
        var result = 0
        for (b in value) {
            result = (result shl 8) or (b.toInt() and 0xFF)
        }
        return result
    }
}
