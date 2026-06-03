package de.knutwurst.knutcut.transport

import android.bluetooth.BluetoothSocket
import de.knutwurst.knutcut.svgcore.LineFramer
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
        val framer = LineFramer()
        try {
            while (running) {
                val n = input.read(buf)
                if (n < 0) break
                for (line in framer.append(buf, n)) lines.add(line)
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
