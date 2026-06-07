package de.knutwurst.knutcut.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** BLE (Bluetooth Low Energy) scan and GATT connection for Silhouette cutters. */
@SuppressLint("MissingPermission")
object BluetoothLePlotter {

    /** True if a BLE advertisement name matches a Silhouette plotter. */
    fun isCompatibleLe(name: String?): Boolean = de.knutwurst.knutcut.data.Devices.isCompatibleLe(name)

    private fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /**
     * Start a BLE scan and deliver each Silhouette-compatible device to [onFound]. Returns a
     * [Closeable] that stops the scan — the caller must close it to avoid leaking the scanner.
     */
    fun startScanLe(context: Context, onFound: (BluetoothDevice, String?) -> Unit): Closeable {
        val scanner = adapter(context)?.bluetoothLeScanner ?: return Closeable {}
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Deliver every result with its resolved name; the caller splits compatible Silhouettes
                // from unknown BLE devices (the latter sit behind the "other devices" warning).
                onFound(result.device, result.scanRecord?.deviceName ?: result.device.name)
            }
        }
        scanner.startScan(cb)
        return Closeable { runCatching { scanner.stopScan(cb) } }
    }

    /**
     * Connect [device] via GATT LE, negotiate MTU, discover services, enable notifications on the
     * first notify characteristic found, and return a [BlePlotterLink]. Blocking — call on IO thread.
     * Throws [Exception] on timeout ([timeoutMs]) or failure.
     */
    fun connect(context: Context, device: BluetoothDevice, timeoutMs: Long = 20_000): BlePlotterLink {
        val latch = CountDownLatch(1)
        var result: BlePlotterLink? = null
        var error: Exception? = null
        var mtu = 23

        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (latch.count > 0L) { error = Exception("BLE getrennt (status=$status)"); latch.countDown() }
                    else result?.onDisconnected()
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, negotiatedMtu: Int, status: Int) {
                if (latch.count == 0L) return
                mtu = negotiatedMtu
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (latch.count == 0L) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    error = Exception("Diensterkennung fehlgeschlagen (status=$status)"); latch.countDown(); return
                }
                val (writeChar, notifyChar) = findCharacteristics(g) ?: run {
                    error = Exception("Keine Silhouette-GATT-Charakteristik gefunden."); latch.countDown(); return
                }
                result = BlePlotterLink(g, writeChar, mtu)
                // Only release the connect once notifications are actually enabled (CCCD write acked),
                // so the first status query can't go out before the device can answer it. If there's no
                // CCCD descriptor, there's nothing to wait for.
                if (!enableNotifications(g, notifyChar)) latch.countDown()
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (latch.count > 0L) latch.countDown()
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
                result?.onWriteComplete(status == BluetoothGatt.GATT_SUCCESS)
            }

            @Deprecated("API < 33", replaceWith = ReplaceWith("onCharacteristicChanged(gatt, char, value)"))
            override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION") result?.onNotification(char.value ?: return)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
                result?.onNotification(value)
            }
        }

        val gatt = device.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE)
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) { gatt.close(); throw Exception("BLE-Zeitüberschreitung.") }
        error?.let { gatt.close(); throw it }
        return result ?: throw Exception("Unbekannter BLE-Fehler.")
    }

    /** Returns true if a CCCD descriptor write was issued (so the caller waits for onDescriptorWrite). */
    private fun enableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic): Boolean {
        gatt.setCharacteristicNotification(char, true)
        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val cccd = char.getDescriptor(cccdUuid) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION") cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION") gatt.writeDescriptor(cccd)
        }
        return true
    }

    private fun findCharacteristics(gatt: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        for (service in gatt.services) {
            var write: BluetoothGattCharacteristic? = null
            var notify: BluetoothGattCharacteristic? = null
            for (ch in service.characteristics) {
                val p = ch.properties
                if (write == null && p and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) write = ch
                if (notify == null && p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) notify = ch
            }
            if (write != null && notify != null) return write to notify
        }
        return null
    }
}
