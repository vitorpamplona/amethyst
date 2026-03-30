/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nipBEBle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.vitorpamplona.quartz.utils.Log
import java.util.UUID

/**
 * Android implementation of [BleTransport] using the Android BLE APIs.
 *
 * Handles BLE advertising, scanning, GATT server, and GATT client operations
 * per NIP-BE specification.
 *
 * **Required permissions** (must be granted before calling any methods):
 * - `BLUETOOTH_ADVERTISE` (Android 12+)
 * - `BLUETOOTH_CONNECT` (Android 12+)
 * - `BLUETOOTH_SCAN` (Android 12+)
 * - `ACCESS_FINE_LOCATION` (for scanning on Android < 12)
 *
 * Usage:
 * ```kotlin
 * val mesh = BleNostrMesh(AndroidBleTransport(context))
 * mesh.onEvent { event, peer -> saveEvent(event) }
 * mesh.start()
 * ```
 */
@SuppressLint("MissingPermission")
class AndroidBleTransport(
    private val context: Context,
    override val deviceUuid: String = UUID.randomUUID().toString().uppercase(),
) : BleTransport,
    AndroidBleTransportContract {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var listener: BleTransportListener? = null
    private var gattServer: BluetoothGattServer? = null
    private val gattConnections = mutableMapOf<String, BluetoothGatt>()
    private val connectedDevices = mutableMapOf<String, BluetoothDevice>()
    private val deviceUuidMap = mutableMapOf<String, String>()
    private val peerMap = mutableMapOf<String, BlePeer>()

    private val serviceUuid = UUID.fromString(BleConfig.SERVICE_UUID)
    private val writeCharUuid = UUID.fromString(BleConfig.WRITE_CHARACTERISTIC_UUID)
    private val readCharUuid = UUID.fromString(BleConfig.READ_CHARACTERISTIC_UUID)

    private var readCharacteristic: BluetoothGattCharacteristic? = null

    /**
     * Sets the listener for transport events. Must be called before [startAdvertising] or [startScanning].
     */
    fun setListener(listener: BleTransportListener) {
        this.listener = listener
    }

    override fun setTransportListener(listener: BleTransportListener) {
        this.listener = listener
    }

    override fun startAdvertising() {
        val advertiser =
            bluetoothAdapter?.bluetoothLeAdvertiser ?: run {
                listener?.onError(null, "BLE advertising not supported")
                return
            }

        val settings =
            AdvertiseSettings
                .Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setTimeout(0)
                .build()

        val data =
            AdvertiseData
                .Builder()
                .addServiceUuid(ParcelUuid(serviceUuid))
                .addServiceData(ParcelUuid(serviceUuid), uuidToBytes(deviceUuid))
                .setIncludeDeviceName(false)
                .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    override fun stopAdvertising() {
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
    }

    override fun startScanning() {
        val scanner =
            bluetoothAdapter?.bluetoothLeScanner ?: run {
                listener?.onError(null, "BLE scanning not supported")
                return
            }

        val filter =
            ScanFilter
                .Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()

        val settings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    override fun stopScanning() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    override fun openGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val writeChar =
            BluetoothGattCharacteristic(
                writeCharUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        readCharacteristic =
            BluetoothGattCharacteristic(
                readCharUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )

        // Client Characteristic Configuration Descriptor for notifications
        val cccd =
            BluetoothGattDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            )
        readCharacteristic!!.addDescriptor(cccd)

        service.addCharacteristic(writeChar)
        service.addCharacteristic(readCharacteristic)

        gattServer?.addService(service)
    }

    override fun closeGattServer() {
        gattServer?.close()
        gattServer = null
        readCharacteristic = null
    }

    override fun connectToPeer(peer: BlePeer) {
        val device =
            peer.platformHandle as? BluetoothDevice ?: run {
                listener?.onError(peer, "Invalid platform handle for peer ${peer.deviceUuid}")
                return
            }

        peerMap[peer.deviceUuid] = peer
        val gatt = device.connectGatt(context, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
        gattConnections[peer.deviceUuid] = gatt
    }

    override fun disconnectFromPeer(peer: BlePeer) {
        gattConnections.remove(peer.deviceUuid)?.let {
            it.disconnect()
            it.close()
        }
        connectedDevices.remove(peer.deviceUuid)
        peerMap.remove(peer.deviceUuid)
    }

    override fun writeChunk(
        peer: BlePeer,
        chunk: ByteArray,
    ): Boolean {
        val gatt = gattConnections[peer.deviceUuid] ?: return false
        val service = gatt.getService(serviceUuid) ?: return false
        val char = service.getCharacteristic(writeCharUuid) ?: return false

        char.value = chunk
        return gatt.writeCharacteristic(char)
    }

    override fun notifyChunk(
        peer: BlePeer,
        chunk: ByteArray,
    ): Boolean {
        val server = gattServer ?: return false
        val device = connectedDevices[peer.deviceUuid] ?: return false
        val char = readCharacteristic ?: return false

        char.value = chunk
        return server.notifyCharacteristicChanged(device, char, false)
    }

    override fun requestMtu(
        peer: BlePeer,
        mtu: Int,
    ) {
        gattConnections[peer.deviceUuid]?.requestMtu(mtu)
    }

    // -- Advertise Callback --

    private val advertiseCallback =
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("AndroidBleTransport", "Advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                listener?.onError(null, "Advertising failed with error code: $errorCode")
            }
        }

    // -- Scan Callback --

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(serviceUuid)) ?: return
                val peerUuid = bytesToUuid(serviceData) ?: return
                val device = result.device

                deviceUuidMap[device.address] = peerUuid

                listener?.onPeerDiscovered(peerUuid, device)
            }

            override fun onScanFailed(errorCode: Int) {
                listener?.onError(null, "Scan failed with error code: $errorCode")
            }
        }

    // -- GATT Server Callback --

    private val gattServerCallback =
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int,
            ) {
                val peerUuid = deviceUuidMap[device.address] ?: return
                val peer = peerMap[peerUuid] ?: BlePeer(peerUuid, BleRole.CLIENT, device)

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevices[peerUuid] = device
                    peerMap[peerUuid] = peer
                    listener?.onPeerConnected(peer)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevices.remove(peerUuid)
                    listener?.onPeerDisconnected(peer)
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                if (characteristic.uuid == writeCharUuid && value != null) {
                    val peerUuid = deviceUuidMap[device.address] ?: return
                    val peer = peerMap[peerUuid] ?: return

                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }

                    listener?.onChunkReceived(peer, value)
                    listener?.onWriteSuccess(peer)
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (characteristic.uuid == readCharUuid) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        characteristic.value ?: ByteArray(0),
                    )
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                // Client subscribing/unsubscribing to notifications
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

    // -- GATT Client Callback --

    private val gattClientCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                val peerUuid = deviceUuidMap[gatt.device.address] ?: return
                val peer = peerMap[peerUuid] ?: return

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt.requestMtu(BleConfig.DEFAULT_MTU)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    listener?.onPeerDisconnected(peer)
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) return

                val peerUuid = deviceUuidMap[gatt.device.address] ?: return
                val peer = peerMap[peerUuid] ?: return

                // Subscribe to Read Characteristic notifications
                val service = gatt.getService(serviceUuid) ?: return
                val readChar = service.getCharacteristic(readCharUuid) ?: return
                gatt.setCharacteristicNotification(readChar, true)

                val cccd = readChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                cccd?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }

                listener?.onPeerConnected(peer)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (characteristic.uuid == readCharUuid) {
                    val peerUuid = deviceUuidMap[gatt.device.address] ?: return
                    val peer = peerMap[peerUuid] ?: return
                    val value = characteristic.value ?: return

                    listener?.onChunkReceived(peer, value)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (characteristic.uuid == writeCharUuid && status == BluetoothGatt.GATT_SUCCESS) {
                    val peerUuid = deviceUuidMap[gatt.device.address] ?: return
                    val peer = peerMap[peerUuid] ?: return
                    listener?.onWriteSuccess(peer)
                }
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val peerUuid = deviceUuidMap[gatt.device.address] ?: return
                    val peer = peerMap[peerUuid] ?: return
                    listener?.onMtuChanged(peer, mtu)
                }
                // Discover services after MTU negotiation (matching samiz flow)
                gatt.discoverServices()
            }
        }

    companion object {
        /**
         * Converts a UUID string to a 16-byte array for advertisement data.
         */
        fun uuidToBytes(uuid: String): ByteArray {
            val parsed = UUID.fromString(uuid)
            val bytes = ByteArray(16)
            val msb = parsed.mostSignificantBits
            val lsb = parsed.leastSignificantBits
            for (i in 0..7) {
                bytes[i] = (msb shr (56 - i * 8)).toByte()
                bytes[i + 8] = (lsb shr (56 - i * 8)).toByte()
            }
            return bytes
        }

        /**
         * Converts a 16-byte array back to a UUID string.
         */
        fun bytesToUuid(bytes: ByteArray): String? {
            if (bytes.size != 16) return null
            var msb = 0L
            var lsb = 0L
            for (i in 0..7) {
                msb = (msb shl 8) or (bytes[i].toLong() and 0xFF)
                lsb = (lsb shl 8) or (bytes[i + 8].toLong() and 0xFF)
            }
            return UUID(msb, lsb).toString().uppercase()
        }
    }
}
