package de.knutwurst.knutcut.transport

import android.bluetooth.BluetoothSocket
import de.knutwurst.knutcut.svgcore.PlotterLink
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A [PlotterLink] over a connected RFCOMM socket. A daemon reader thread splits the input stream on
 * CRLF and queues whole response lines; [readLine] pops the next one (blocking up to a timeout).
 */
class SppPlotterLink(private val socket: BluetoothSocket) : PlotterLink, AutoCloseable {

    private val out: OutputStream = socket.outputStream
    private val input: InputStream = socket.inputStream
    private val lines = LinkedBlockingQueue<String>()
    @Volatile private var running = true

    private val reader = Thread {
        val buf = ByteArray(2048)
        val sb = StringBuilder()
        try {
            while (running) {
                val n = input.read(buf)
                if (n < 0) break
                sb.append(String(buf, 0, n, Charsets.UTF_8))
                var idx = sb.indexOf("\r\n")
                while (idx >= 0) {
                    lines.add(sb.substring(0, idx))
                    sb.delete(0, idx + 2)
                    idx = sb.indexOf("\r\n")
                }
            }
        } catch (_: Exception) {
            // socket closed or read error; readLine will time out
        }
    }.apply { isDaemon = true; start() }

    override fun write(text: String) {
        out.write(text.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    override fun readLine(timeoutMs: Long): String? = lines.poll(timeoutMs, TimeUnit.MILLISECONDS)

    override fun close() {
        running = false
        runCatching { input.close() }
        runCatching { out.close() }
        runCatching { socket.close() }
    }
}
