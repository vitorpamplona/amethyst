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
package com.vitorpamplona.quartz.nipBEBle.relay

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nipBEBle.BlePeer
import com.vitorpamplona.quartz.nipBEBle.BleRole
import com.vitorpamplona.quartz.nipBEBle.assignRole
import com.vitorpamplona.quartz.nipBEBle.protocol.BleMessageChunker
import com.vitorpamplona.quartz.nipBEBle.transport.BleTransport
import com.vitorpamplona.quartz.nipBEBle.transport.BleTransportListener
import com.vitorpamplona.quartz.utils.Log

/**
 * High-level manager for Nostr BLE mesh networking per NIP-BE.
 *
 * Handles the full lifecycle:
 * 1. **Discovery** - Scans for and advertises the Nostr BLE service
 * 2. **Role assignment** - Determines client/server roles based on UUID comparison
 * 3. **Connection** - Establishes GATT connections with appropriate roles
 * 4. **Synchronization** - Drives NIP-77 half-duplex sync after connection
 * 5. **Event spreading** - Forwards new events to connected peers
 *
 * Usage:
 * ```kotlin
 * val mesh = BleMeshManager(
 *     transport = androidBleTransport,
 *     listener = object : BleMeshListener {
 *         override fun onEventReceived(event: Event, fromPeer: BlePeer) {
 *             // Store and process the event
 *         }
 *         override fun onPeerConnected(peer: BlePeer, role: BleRole) {
 *             // UI update: new peer connected
 *         }
 *     }
 * )
 * mesh.start()
 * // ... later
 * mesh.broadcastEvent(newEvent) // Forward to all connected peers
 * mesh.stop()
 * ```
 */
class BleMeshManager(
    val transport: BleTransport,
    val listener: BleMeshListener,
) : BleTransportListener {
    private val clients = mutableMapOf<String, BleNostrClient>()
    private val servers = mutableMapOf<String, BleNostrServer>()
    private val knownPeers = mutableSetOf<String>()
    private val sentEventIds = mutableMapOf<String, MutableSet<String>>()
    private var running = false

    /**
     * Starts BLE mesh networking: begins advertising and scanning.
     */
    fun start() {
        if (running) return
        running = true
        transport.openGattServer()
        transport.startAdvertising()
        transport.startScanning()
    }

    /**
     * Stops all BLE operations and disconnects from all peers.
     */
    fun stop() {
        running = false
        transport.stopScanning()
        transport.stopAdvertising()

        clients.values.forEach { it.disconnect() }
        clients.clear()

        servers.values.forEach { it.onClientDisconnected() }
        servers.clear()

        knownPeers.clear()
        sentEventIds.clear()

        transport.closeGattServer()
    }

    /**
     * Broadcasts an event to all connected peers that haven't received it yet.
     * Implements the half-duplex event spread workflow from NIP-BE.
     */
    fun broadcastEvent(event: Event) {
        for ((peerUuid, client) in clients) {
            if (trackEventSent(peerUuid, event.id)) {
                client.sendIfConnected(EventCmd(event))
            }
        }

        for ((peerUuid, server) in servers) {
            if (trackEventSent(peerUuid, event.id)) {
                server.sendEmptyNotification()
                server.sendMessage(EventMessage("ble", event))
            }
        }
    }

    /**
     * Returns all currently connected peers.
     */
    fun connectedPeers(): List<BlePeer> {
        val result = mutableListOf<BlePeer>()
        clients.values.filter { it.isConnected() }.mapTo(result) { it.peer }
        servers.values.filter { it.isConnected() }.mapTo(result) { it.peer }
        return result
    }

    /**
     * Returns the [IRelayClient] for a given peer (if connected as client).
     * This allows integrating BLE peers into the existing relay pool.
     */
    fun getRelayClient(peerUuid: String): IRelayClient? = clients[peerUuid]

    // -- BleTransportListener implementation --

    override fun onPeerDiscovered(
        peerUuid: String,
        platformHandle: Any?,
    ) {
        if (!running) return
        if (peerUuid == transport.deviceUuid) return
        if (peerUuid in knownPeers) return

        knownPeers.add(peerUuid)

        val role = assignRole(transport.deviceUuid, peerUuid)
        val peer = BlePeer(peerUuid, role, platformHandle)

        Log.d("BleMeshManager") { "Discovered peer $peerUuid, my role: $role" }

        when (role) {
            BleRole.CLIENT -> {
                val client =
                    BleNostrClient(
                        peer = peer,
                        transport = transport,
                        listener = ClientConnectionListener(peerUuid),
                    )
                clients[peerUuid] = client
                client.connect()
            }

            BleRole.SERVER -> {
                val server =
                    BleNostrServer(
                        peer = peer,
                        transport = transport,
                        listener = ServerEventListener(peerUuid),
                    )
                servers[peerUuid] = server
            }
        }

        listener.onPeerDiscovered(peer)
    }

    override fun onPeerConnected(peer: BlePeer) {
        when (peer.role) {
            BleRole.CLIENT -> clients[peer.deviceUuid]?.onConnected()
            BleRole.SERVER -> servers[peer.deviceUuid]?.onClientConnected()
        }
        listener.onPeerConnected(peer, peer.role)
    }

    override fun onPeerDisconnected(peer: BlePeer) {
        when (peer.role) {
            BleRole.CLIENT -> {
                clients[peer.deviceUuid]?.onDisconnected()
                clients.remove(peer.deviceUuid)
            }

            BleRole.SERVER -> {
                servers[peer.deviceUuid]?.onClientDisconnected()
                servers.remove(peer.deviceUuid)
            }
        }
        knownPeers.remove(peer.deviceUuid)
        sentEventIds.remove(peer.deviceUuid)
        listener.onPeerDisconnected(peer)
    }

    override fun onChunkReceived(
        peer: BlePeer,
        chunk: ByteArray,
    ) {
        when (peer.role) {
            BleRole.CLIENT -> clients[peer.deviceUuid]?.onChunkReceived(chunk)
            BleRole.SERVER -> servers[peer.deviceUuid]?.onChunkReceived(chunk)
        }
    }

    override fun onWriteSuccess(peer: BlePeer) {
        when (peer.role) {
            BleRole.CLIENT -> clients[peer.deviceUuid]?.onWriteSuccess()
            BleRole.SERVER -> servers[peer.deviceUuid]?.onNotifySuccess()
        }
    }

    override fun onMtuChanged(
        peer: BlePeer,
        mtu: Int,
    ) {
        // ATT overhead is 3 bytes, chunk framing is 2 bytes (index + total count)
        val chunkSize = mtu - 3 - BleMessageChunker.CHUNK_OVERHEAD
        clients[peer.deviceUuid]?.chunkSize = chunkSize
        servers[peer.deviceUuid]?.chunkSize = chunkSize
    }

    override fun onError(
        peer: BlePeer?,
        error: String,
    ) {
        Log.e("BleMeshManager") { "BLE error for peer ${peer?.deviceUuid}: $error" }
        listener.onError(peer, error)
    }

    // -- Private helpers --

    /**
     * Tracks that an event has been sent to a peer.
     * @return true if the event was NOT previously sent (should be sent now).
     */
    private fun trackEventSent(
        peerUuid: String,
        eventId: String,
    ): Boolean {
        val sent = sentEventIds.getOrPut(peerUuid) { mutableSetOf() }
        return sent.add(eventId)
    }

    private inner class ClientConnectionListener(
        val peerUuid: String,
    ) : RelayConnectionListener {
        override fun onIncomingMessage(
            relay: IRelayClient,
            msgStr: String,
            msg: Message,
        ) {
            if (msg is EventMessage) {
                trackEventSent(peerUuid, msg.event.id)
                listener.onEventReceived(msg.event, clients[peerUuid]?.peer ?: return)
            }
            listener.onMessageReceived(relay, msgStr, msg)
        }
    }

    private inner class ServerEventListener(
        val peerUuid: String,
    ) : BleNostrServerListener {
        override fun onClientConnected(server: BleNostrServer) {}

        override fun onClientDisconnected(server: BleNostrServer) {}

        override fun onCommandReceived(
            server: BleNostrServer,
            cmdStr: String,
            cmd: Command,
        ) {
            if (cmd is EventCmd) {
                trackEventSent(peerUuid, cmd.event.id)
                listener.onEventReceived(cmd.event, server.peer)
            }
            listener.onCommandReceived(server, cmdStr, cmd)
        }
    }
}

/**
 * Listener for high-level BLE mesh events.
 */
interface BleMeshListener {
    /** A new peer advertising the Nostr BLE service was discovered. */
    fun onPeerDiscovered(peer: BlePeer) {}

    /** A connection with a peer has been established. */
    fun onPeerConnected(
        peer: BlePeer,
        role: BleRole,
    ) {}

    /** A peer has disconnected. */
    fun onPeerDisconnected(peer: BlePeer) {}

    /** An event was received from a peer (via client or server path). */
    fun onEventReceived(
        event: Event,
        fromPeer: BlePeer,
    )

    /** A Nostr message was received (client role - from a BLE relay). */
    fun onMessageReceived(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {}

    /** A Nostr command was received (server role - from a BLE client). */
    fun onCommandReceived(
        server: BleNostrServer,
        cmdStr: String,
        cmd: Command,
    ) {}

    /** An error occurred. */
    fun onError(
        peer: BlePeer?,
        error: String,
    ) {}
}
