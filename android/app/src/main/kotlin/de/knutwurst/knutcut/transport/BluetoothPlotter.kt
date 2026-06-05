package de.knutwurst.knutcut.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.io.Closeable
import java.util.UUID

/** Thin wrapper over the platform Bluetooth APIs for the classic SPP connection. */
@SuppressLint("MissingPermission") // callers gate on BLUETOOTH_CONNECT before calling these
object BluetoothPlotter {

    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isEnabled(context: Context): Boolean = adapter(context)?.isEnabled == true

    /** Paired devices that have a name (the cutter must be paired in Android settings first). */
    fun bondedDevices(context: Context): List<BluetoothDevice> =
        adapter(context)?.bondedDevices?.filter { it.name != null }?.sortedBy { it.name } ?: emptyList()

    /**
     * Start classic Bluetooth discovery, reporting each device found via [onFound]. Returns a
     * [Closeable] that cancels discovery and unregisters the receiver — the caller must close it.
     */
    fun discover(context: Context, onFound: (BluetoothDevice) -> Unit): Closeable {
        val adapter = adapter(context)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_FOUND) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.let(onFound)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        adapter?.cancelDiscovery()
        adapter?.startDiscovery()
        return Closeable {
            runCatching { adapter?.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /** Open and connect an RFCOMM socket to [device]. Blocking; call off the main thread. */
    fun connect(context: Context, device: BluetoothDevice): SppPlotterLink {
        adapter(context)?.cancelDiscovery()
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket.connect()
        return SppPlotterLink(socket)
    }
}
