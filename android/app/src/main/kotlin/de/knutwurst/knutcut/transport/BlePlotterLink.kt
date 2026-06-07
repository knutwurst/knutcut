package de.knutwurst.knutcut.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import de.knutwurst.knutcut.svgcore.EtxFramer
import de.knutwurst.knutcut.svgcore.LinkTransport
import de.knutwurst.knutcut.svgcore.ManagedLink
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A [ManagedLink] over a connected BLE GATT session. Incoming notifications are split on ETX via
 * [EtxFramer] and queued; [readLine] pops the next complete token. Outgoing writes are split into
 * [mtu]-3 byte chunks (ATT overhead) and written with WRITE_NO_RESPONSE for maximum throughput.
 *
 * [onNotification] and [onDisconnected] are called by [BluetoothLePlotter]'s GATT callback —
 * they must not be called by anything else.
 */
@SuppressLint("MissingPermission")
class BlePlotterLink(
    private val gatt: BluetoothGatt,
    private val writeChar: BluetoothGattCharacteristic,
    private val mtu: Int = 23,
) : ManagedLink {

    override val transport = LinkTransport.BLE

    override var onClosed: (() -> Unit)? = null

    private val tokens = LinkedBlockingQueue<String>()
    // One slot per completed write: a write must not be issued while another is outstanding, so each
    // chunk waits here for its onCharacteristicWrite before the next is sent.
    private val writeAcks = LinkedBlockingQueue<Boolean>()
    @Volatile private var running = true
    private val framer = EtxFramer()

    // ── called by BluetoothLePlotter's GATT callback ──────────────────────

    internal fun onNotification(data: ByteArray) {
        for (token in framer.append(data, data.size)) tokens.add(token)
    }

    internal fun onWriteComplete(ok: Boolean) { writeAcks.offer(ok) }

    internal fun onDisconnected() {
        if (running) onClosed?.invoke()
    }

    // ── PlotterLink ───────────────────────────────────────────────────────

    override fun write(text: String) {
        if (!running) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        val chunkSize = maxOf(20, mtu - 3)
        var offset = 0
        writeAcks.clear()
        while (offset < bytes.size && running) {
            val end = minOf(offset + chunkSize, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(writeChar, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION") writeChar.value = chunk
                @Suppress("DEPRECATION") gatt.writeCharacteristic(writeChar)
            }
            // Serialise against the GATT queue (only one operation may be outstanding). Bail if the
            // write never completes so a stalled link can't block the cut thread forever.
            // Stop on timeout (null) or a failed write (false) so a dropped chunk isn't treated as sent.
            if (writeAcks.poll(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS) != true) break
            offset = end
        }
    }

    override fun readLine(timeoutMs: Long): String? = tokens.poll(timeoutMs, TimeUnit.MILLISECONDS)

    override fun close() {
        running = false
        writeAcks.offer(false) // release a waiting write as failed so it stops, not "sent"
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 3000L
    }
}
