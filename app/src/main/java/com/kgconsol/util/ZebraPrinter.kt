package com.kgconsol.util

import com.kgconsol.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

sealed class PrintResult {
    object Success : PrintResult()
    data class Error(val message: String) : PrintResult()
}

@Singleton
class ZebraPrinter @Inject constructor() {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000

        // Label: 100mm × 150mm @ 203 dpi, portrait orientation (800 × 1200 dots).
        internal fun buildZpl(
            batchName: String,
            boxNumber: String,
            orderCount: Int,
            date: String,
            packageCode: String
        ): String = """
^XA
^CI28
^MMT
^PW800
^LL1200

^FO25,35^A0N,58,58^FDOnza España^FS

^FO25,110^BY3,3,145^BCN,145,Y,N,N^FD$packageCode^FS

^FO25,330^A0N,120,120^FD$boxNumber^FS

^FO25,465^GB750,3,3^FS
^FO25,482^A0N,32,32^FDPacked in Malaga: $date^FS
^FO25,530^GB750,3,3^FS

^FO25,555^A0N,115,115^FD$batchName^FS

^FO25,710^A0N,50,50^FDOrders total: $orderCount^FS

^FO25,820^BQN,2,10^FDMA,$packageCode^FS

^FO390,1030^A0N,32,32^FDKG Consol^FS
^FO390,1072^A0N,26,26^FDVersion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})^FS
^FO390,1110^A0N,26,26^FDDev: Viacheslav Fitlin^FS

^XZ
""".trimIndent()

        internal fun buildPackageCode(
            boxNumber: String,
            now: Date,
            timeZone: TimeZone = TimeZone.getDefault()
        ): String {
            require(boxNumber.matches(Regex("""\d{4}"""))) {
                "Box number must contain exactly four digits"
            }
            val formatter = SimpleDateFormat("MMddHHmm", Locale.US).apply {
                this.timeZone = timeZone
            }
            return "NBX${formatter.format(now)}$boxNumber"
        }
    }

    /** Print 2 copies of the box label via TCP/IP */
    suspend fun printBoxLabel(
        ip: String,
        port: Int,
        batchName: String,
        boxNumber: String,
        orderCount: Int,
        copies: Int = 2
    ): PrintResult = withContext(Dispatchers.IO) {
        val now = Date()
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(now)
        val packageCode = buildPackageCode(boxNumber, now)
        val zpl = buildZpl(batchName, boxNumber, orderCount, date, packageCode)
        sendZpl(ip, port, zpl, copies)
    }

    /** Test print: sends a simple label */
    suspend fun printTest(ip: String, port: Int): PrintResult = withContext(Dispatchers.IO) {
        val zpl = """
^XA
^CI28
^FO50,50^A0N,60,60^FDKG Consol^FS
^FO50,140^A0N,40,40^FDTest Print OK^FS
^FO50,200^A0N,30,30^FD${ip}:${port}^FS
^XZ
""".trimIndent()
        sendZpl(ip, port, zpl, 1)
    }

    private fun sendZpl(
        ip: String,
        port: Int,
        zpl: String,
        copies: Int
    ): PrintResult {
        if (ip.isBlank()) return PrintResult.Error("Printer IP is not configured")
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                val writer = PrintWriter(socket.getOutputStream(), true)
                repeat(copies) { writer.print(zpl) }
                writer.flush()
            }
            PrintResult.Success
        } catch (e: java.net.ConnectException) {
            PrintResult.Error("Cannot connect to printer at $ip:$port.\nCheck IP and network.")
        } catch (e: java.net.SocketTimeoutException) {
            PrintResult.Error("Connection timed out. Printer not responding.")
        } catch (e: Exception) {
            PrintResult.Error("Print error: ${e.message}")
        }
    }
}
