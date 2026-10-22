package com.kgconsol.util

import com.kgconsol.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ZebraPrinterTest {

    @Test
    fun `tracking code contains prefix date time and four digit box id`() {
        val utc = TimeZone.getTimeZone("UTC")
        val date = Calendar.getInstance(utc).apply {
            set(2026, Calendar.OCTOBER, 21, 14, 26, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val code = ZebraPrinter.buildPackageCode("6376", date, utc)

        assertEquals("NBX102114266376", code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tracking code rejects a box id that is not four digits`() {
        ZebraPrinter.buildPackageCode("11111", java.util.Date(), TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun `zpl contains the complete reference label data`() {
        val zpl = ZebraPrinter.buildZpl(
            batchName = "KG153",
            boxNumber = "6376",
            orderCount = 42,
            date = "21.10.2026 15:04",
            packageCode = "NBX102114266376"
        )

        val requiredCommands = listOf(
            "^PW800",
            "^LL1200",
            "^FDOnza España^FS",
            "^FO25,110^BY3,3,145^BCN,145,Y,N,N^FDNBX102114266376^FS",
            "^FD6376^FS",
            "^FDPacked in Malaga: 21.10.2026 15:04^FS",
            "^FDKG153^FS",
            "^FDOrders total: 42^FS",
            "^FO25,820^BQN,2,10^FDMA,NBX102114266376^FS",
            "^FO390,1030^A0N,32,32^FDKG Consol^FS",
            "^FDKG Consol^FS",
            "^FDVersion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})^FS",
            "^FDDev: Viacheslav Fitlin^FS"
        )

        requiredCommands.forEach { command ->
            assertTrue("Missing ZPL command: $command", command in zpl)
        }
        assertTrue(zpl.startsWith("^XA"))
        assertTrue(zpl.endsWith("^XZ"))
    }
}
