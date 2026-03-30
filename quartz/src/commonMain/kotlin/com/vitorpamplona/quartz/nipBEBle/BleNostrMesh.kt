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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command

/**
 * Easy-to-use facade for NIP-BE Nostr BLE mesh networking.
 *
 * Wraps all the BLE protocol complexity behind a simple lambda-based API.
 * Handles transport wiring, role assignment, message chunking, and
 * event routing automatically.
 *
 * ## Quick start (Android)
 * ```kotlin
 * val mesh = BleNostrMesh(AndroidBleTransport(context))
 *
 * mesh.onEvent { event, peer ->
 *     Log.d("BLE", "Got ${event.kind} from ${peer.deviceUuid}")
 *     saveToDatabase(event)
 * }
 *
 * mesh.onPeerConnected { peer ->
 *     Log.d("BLE", "Peer connected: ${peer.deviceUuid}")
 * }
 *
 * mesh.start()
 *
 * // Broadcast a signed event to all connected peers
 * mesh.broadcast(myEvent)
 *
 * // Clean up
 * mesh.stop()
 * ```
 *
 * ## Advanced usage
 * For relay-level protocol access (REQ, filters, subscriptions),
 * use [getRelayClient] to get an [IRelayClient] for a specific peer,
 * or set [onMessage] / [onCommand] for raw protocol callbacks.
 */
class BleNostrMesh(
    transport: BleTransport,
) {
    /** Called when a Nostr event is received from any peer. */
    var onEvent: ((event: Event, fromPeer: BlePeer) -> Unit)? = null

    /** Called when a new peer connects. */
    var onPeerConnected: ((peer: BlePeer) -> Unit)? = null

    /** Called when a peer disconnects. */
    var onPeerDisconnected: ((peer: BlePeer) -> Unit)? = null

    /** Called when a new peer is discovered (before connection). */
    var onPeerDiscovered: ((peer: BlePeer) -> Unit)? = null

    /** Called on BLE errors. */
    var onError: ((error: String) -> Unit)? = null

    /** Called for raw Nostr messages (when acting as client). */
    var onMessage: ((relay: IRelayClient, msg: Message) -> Unit)? = null

    /** Called for raw Nostr commands (when acting as server). */
    var onCommand: ((server: BleNostrServer, cmd: Command) -> Unit)? = null

    private val manager =
        BleMeshManager(
            transport = transport,
            listener =
                object : BleMeshListener {
                    override fun onEventReceived(
                        event: Event,
                        fromPeer: BlePeer,
                    ) {
                        onEvent?.invoke(event, fromPeer)
                    }

                    override fun onPeerConnected(
                        peer: BlePeer,
                        role: BleRole,
                    ) {
                        this@BleNostrMesh.onPeerConnected?.invoke(peer)
                    }

                    override fun onPeerDisconnected(peer: BlePeer) {
                        this@BleNostrMesh.onPeerDisconnected?.invoke(peer)
                    }

                    override fun onPeerDiscovered(peer: BlePeer) {
                        this@BleNostrMesh.onPeerDiscovered?.invoke(peer)
                    }

                    override fun onMessageReceived(
                        relay: IRelayClient,
                        msgStr: String,
                        msg: Message,
                    ) {
                        onMessage?.invoke(relay, msg)
                    }

                    override fun onCommandReceived(
                        server: BleNostrServer,
                        cmdStr: String,
                        cmd: Command,
                    ) {
                        onCommand?.invoke(server, cmd)
                    }

                    override fun onError(
                        peer: BlePeer?,
                        error: String,
                    ) {
                        this@BleNostrMesh.onError?.invoke(error)
                    }
                },
        )

    init {
        if (transport is AndroidBleTransportContract) {
            transport.setTransportListener(manager)
        }
    }

    /** Starts BLE advertising and scanning for peers. */
    fun start() = manager.start()

    /** Stops all BLE operations and disconnects from all peers. */
    fun stop() = manager.stop()

    /** Broadcasts an event to all connected peers. */
    fun broadcast(event: Event) = manager.broadcastEvent(event)

    /** Returns all currently connected peers. */
    fun connectedPeers(): List<BlePeer> = manager.connectedPeers()

    /** Returns the [IRelayClient] for a specific peer (for advanced protocol use). */
    fun getRelayClient(peerUuid: String): IRelayClient? = manager.getRelayClient(peerUuid)
}

/**
 * Contract for Android BLE transports that support automatic listener wiring.
 * Implemented by [AndroidBleTransport] so that [BleNostrMesh] can self-configure.
 */
interface AndroidBleTransportContract {
    fun setTransportListener(listener: BleTransportListener)
}
