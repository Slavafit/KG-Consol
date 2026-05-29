package com.kgconsol.util

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

        // Label: 150mm × 100mm @ 203 dpi = 1181 × 787 dots
        // Using 8 dots/mm → 150*8=1200 wide, 100*8=800 tall
        private fun buildZpl(
            batchName: String,
            boxDisplay: String,
            orderCount: Int,
            date: String
        ): String = """
^XA
^CI28
^MMT
^PW1200
^LL800

^FO60,100^A0N,100,100^FD$batchName^FS

^FO60,210^A0N,120,120^FD$boxDisplay^FS

^FO60,360^GB1080,4,4^FS

^FO60,385^A0N,45,45^FDOrders: $orderCount^FS
^FO60,445^A0N,40,40^FD$date^FS

^FO60,500^GB1080,4,4^FS

^FO60,530^BQN,2,7^FDMA,$batchName $boxDisplay^FS

^XZ
""".trimIndent()
    }

    /** Print 2 copies of the box label via TCP/IP */
    suspend fun printBoxLabel(
        ip: String,
        port: Int,
        batchName: String,
        boxDisplay: String,
        orderCount: Int,
        copies: Int = 2
    ): PrintResult = withContext(Dispatchers.IO) {
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date())
        val zpl = buildZpl(batchName, boxDisplay, orderCount, date)
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
