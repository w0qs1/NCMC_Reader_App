package com.w0qs1.ncmcreader

import com.w0qs1.ncmcreader.nfc.ApduUtils
import com.w0qs1.ncmcreader.nfc.TlvParser
import com.w0qs1.ncmcreader.nfc.hexToByteArray
import com.w0qs1.ncmcreader.nfc.parseAflFromGpo
import com.w0qs1.ncmcreader.nfc.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NcmcParserTest {

    @Test
    fun testHexConversions() {
        val hex = "A0000005241010"
        val bytes = hex.hexToByteArray()
        assertEquals(7, bytes.size)
        assertEquals(hex, bytes.toHexString())
    }

    @Test
    fun testTlvParserTag5A() {
        val rawTlv = "5A088181942506819397".hexToByteArray()
        val elements = TlvParser.parse(rawTlv)
        assertEquals(1, elements.size)
        val elem = TlvParser.findElement(elements, "5A")
        assertNotNull(elem)
        val pan = TlvParser.decodePan(elem!!.value)
        assertEquals("8181 9425 0681 9397", pan)
    }

    @Test
    fun testTrack2PanDecoding() {
        val rawTlv = "570A8181942506819397D281".hexToByteArray()
        val elements = TlvParser.parse(rawTlv)
        val elem = TlvParser.findElement(elements, "57")
        assertNotNull(elem)
        val pan = TlvParser.decodeTrack2Pan(elem!!.value)
        assertEquals("8181 9425 0681 9397", pan)
    }

    @Test
    fun testTlvParserDates() {
        val rawTlv = "5F2403310228".hexToByteArray()
        val elements = TlvParser.parse(rawTlv)
        val elem = TlvParser.findElement(elements, "5F24")
        assertNotNull(elem)
        val date = TlvParser.parseDate(elem!!.value)
        assertEquals("2031-02-28", date)
    }

    @Test
    fun testSampleDataPanAndDatesExtraction() {
        // Complete SFI 1 Record 1 from sample data (175 bytes)
        val sfi1Rec1Hex = "7081ac5a0881819425068193975f34010157138181942506819397d31026208490000000000f9f080200025f24033102285f25032602019f0702a9385f280203568e0e000000000000000042031f031e03df34060000005000009f0d05a468fc98009f0e0510100000009f0f05a468fc98008c309f02069f03069f1a0295055f2a029a039c019f37049f35019f34039f21039f1c089f4c08df1502df2208df2308df45608d0991108a0295059f3704"
        val tlvs = TlvParser.parse(sfi1Rec1Hex.hexToByteArray())

        val panElem = TlvParser.findElement(tlvs, "5A")
        assertNotNull(panElem)
        val pan = TlvParser.decodePan(panElem!!.value)
        assertEquals("8181 9425 0681 9397", pan)

        val expiryElem = TlvParser.findElement(tlvs, "5F24")
        assertNotNull(expiryElem)
        assertEquals("2031-02-28", TlvParser.parseDate(expiryElem!!.value))

        val effectiveElem = TlvParser.findElement(tlvs, "5F25")
        assertNotNull(effectiveElem)
        assertEquals("2026-02-01", TlvParser.parseDate(effectiveElem!!.value))
    }

    @Test
    fun testMetrodroidGpoCommandBuilder() {
        val gpoApdu = ApduUtils.buildPdolGpoCommand()
        assertTrue(gpoApdu.size > 10)
        assertEquals(0x80.toByte(), gpoApdu[0])
        assertEquals(0xA8.toByte(), gpoApdu[1])
    }

    @Test
    fun testAflParserFromGpoFormat1And2() {
        // Tag 94: AFL with 2 entries:
        // Entry 1: 0x08 (SFI 1), Rec 1 to 2, Auth 0
        // Entry 2: 0x10 (SFI 2), Rec 1 to 3, Auth 1
        val gpoFormat2Hex = "770E8202000094080801020010010301".hexToByteArray()
        val aflEntries = parseAflFromGpo(gpoFormat2Hex)

        assertEquals(2, aflEntries.size)
        assertEquals(1, aflEntries[0].sfi)
        assertEquals(1, aflEntries[0].firstRecord)
        assertEquals(2, aflEntries[0].lastRecord)

        assertEquals(2, aflEntries[1].sfi)
        assertEquals(1, aflEntries[1].firstRecord)
        assertEquals(3, aflEntries[1].lastRecord)
    }
}
