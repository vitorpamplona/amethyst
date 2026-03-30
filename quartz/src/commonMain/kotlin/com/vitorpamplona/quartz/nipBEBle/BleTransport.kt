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

/**
 * Platform-agnostic interface for BLE transport operations.
 *
 * Each platform (Android, iOS) provides a concrete implementation that handles
 * the low-level BLE GATT operations. The protocol layer ([BleNostrClient],
 * [BleNostrServer], [BleMeshManager]) consumes this interface.
 *
 * ## Implementation notes
 * - All callbacks in [BleTransportListener] may be invoked from any thread.
 * - Implementations must handle BLE permission checks internally.
 * - The [deviceUuid] is used for role assignment per NIP-BE.
 */
interface BleTransport {
    /** This device's UUID used for role assignment and advertisement. */
    val deviceUuid: String

    /**
     * Starts BLE advertising with the Nostr BLE service UUID and this device's UUID.
     */
    fun startAdvertising()

    /**
     * Stops BLE advertising.
     */
    fun stopAdvertising()

    /**
     * Starts scanning for nearby devices advertising the Nostr BLE service.
     */
    fun startScanning()

    /**
     * Stops BLE scanning.
     */
    fun stopScanning()

    /**
     * Opens the GATT server to accept incoming connections (server role).
     */
    fun openGattServer()

    /**
     * Closes the GATT server.
     */
    fun closeGattServer()

    /**
     * Initiates a GATT client connection to a peer (client role).
     */
    fun connectToPeer(peer: BlePeer)

    /**
     * Disconnects from a peer.
     */
    fun disconnectFromPeer(peer: BlePeer)

    /**
     * Writes a chunk to the peer's Write Characteristic (client -> server).
     * Only valid when this device has the CLIENT role for this peer.
     *
     * @return true if the write was successfully enqueued.
     */
    fun writeChunk(
        peer: BlePeer,
        chunk: ByteArray,
    ): Boolean

    /**
     * Sends a notification on the Read Characteristic (server -> client).
     * Only valid when this device has the SERVER role for this peer.
     *
     * @return true if the notification was successfully enqueued.
     */
    fun notifyChunk(
        peer: BlePeer,
        chunk: ByteArray,
    ): Boolean

    /**
     * Requests an MTU size negotiation with the peer.
     */
    fun requestMtu(
        peer: BlePeer,
        mtu: Int,
    )
}

/**
 * Callbacks from the BLE transport layer to the protocol layer.
 */
interface BleTransportListener {
    /**
     * A peer advertising the Nostr BLE service was discovered.
     *
     * @param peerUuid The discovered peer's device UUID.
     * @param platformHandle Opaque platform-specific device reference.
     */
    fun onPeerDiscovered(
        peerUuid: String,
        platformHandle: Any?,
    )

    /**
     * A GATT connection has been established with a peer.
     */
    fun onPeerConnected(peer: BlePeer)

    /**
     * A GATT connection with a peer has been lost.
     */
    fun onPeerDisconnected(peer: BlePeer)

    /**
     * A chunk was received from a peer (via write characteristic or notification).
     */
    fun onChunkReceived(
        peer: BlePeer,
        chunk: ByteArray,
    )

    /**
     * A write operation to a peer completed successfully.
     */
    fun onWriteSuccess(peer: BlePeer)

    /**
     * MTU negotiation completed.
     */
    fun onMtuChanged(
        peer: BlePeer,
        mtu: Int,
    )

    /**
     * An error occurred during BLE operations.
     */
    fun onError(
        peer: BlePeer?,
        error: String,
    )
}
