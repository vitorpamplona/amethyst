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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.utils.Log

/**
 * Handles the server (relay) side of a BLE Nostr connection.
 *
 * Per NIP-BE, the server:
 * - Receives commands from clients via writes to the Write Characteristic
 * - Sends messages to clients via notifications on the Read Characteristic
 *
 * Usage:
 * ```kotlin
 * val server = BleNostrServer(
 *     peer = clientPeer,
 *     transport = androidBleTransport,
 *     listener = object : BleNostrServerListener {
 *         override fun onCommandReceived(server: BleNostrServer, cmd: Command) {
 *             // Handle REQ, EVENT, NEG-OPEN, etc.
 *         }
 *     }
 * )
 * // When you have a message to send:
 * server.sendMessage(EventMessage(subId, event))
 * ```
 */
class BleNostrServer(
    val peer: BlePeer,
    val transport: BleTransport,
    val listener: BleNostrServerListener,
    var chunkSize: Int = BleConfig.DEFAULT_CHUNK_SIZE,
) {
    private var connected = false
    private val assembler = BleChunkAssembler()
    private val sendQueue = ArrayDeque<Array<ByteArray>>()
    private var isSending = false

    fun isConnected(): Boolean = connected

    /**
     * Called when the client establishes a GATT connection.
     */
    fun onClientConnected() {
        connected = true
        listener.onClientConnected(this)
    }

    /**
     * Called when the client disconnects.
     */
    fun onClientDisconnected() {
        connected = false
        assembler.reset()
        synchronized(sendQueue) {
            sendQueue.clear()
            isSending = false
        }
        listener.onClientDisconnected(this)
    }

    /**
     * Called when a chunk is received from the client
     * (via Write Characteristic).
     */
    fun onChunkReceived(chunk: ByteArray) {
        val message = assembler.addChunk(chunk) ?: return

        try {
            val cmd = OptimizedJsonMapper.fromJsonToCommand(message)
            listener.onCommandReceived(this, message, cmd)
        } catch (e: Exception) {
            Log.e("BleNostrServer", "Failed to parse command from ${peer.deviceUuid}: $message", e)
        }
    }

    /**
     * Sends a Nostr message to the connected client via Read Characteristic notification.
     */
    fun sendMessage(msg: Message) {
        if (!connected) return

        val json = OptimizedJsonMapper.toJson(msg)
        val chunks = BleMessageChunker.splitIntoChunks(json, chunkSize)

        synchronized(sendQueue) {
            sendQueue.addLast(chunks)
            if (!isSending) {
                isSending = true
                sendNextMessage()
            }
        }
    }

    /**
     * Sends an empty notification to signal the client to read
     * (used in half-duplex event spread when server has a new event).
     */
    fun sendEmptyNotification() {
        if (!connected) return
        transport.notifyChunk(peer, ByteArray(0))
    }

    /**
     * Called when a notification to the client completes, allowing the next chunk.
     */
    fun onNotifySuccess() {
        sendNextChunk()
    }

    private var currentChunks: Array<ByteArray>? = null
    private var currentChunkIndex = 0

    private fun sendNextMessage() {
        val chunks =
            synchronized(sendQueue) {
                sendQueue.removeFirstOrNull()
            }
        if (chunks == null) {
            synchronized(sendQueue) {
                isSending = false
            }
            return
        }
        currentChunks = chunks
        currentChunkIndex = 0
        sendNextChunk()
    }

    private fun sendNextChunk() {
        val chunks = currentChunks ?: return
        if (currentChunkIndex >= chunks.size) {
            currentChunks = null
            sendNextMessage()
            return
        }

        val success = transport.notifyChunk(peer, chunks[currentChunkIndex])
        if (success) {
            currentChunkIndex++
        } else {
            Log.e("BleNostrServer", "Failed to notify chunk $currentChunkIndex to ${peer.deviceUuid}")
        }
    }
}

/**
 * Listener for server-side BLE Nostr events.
 */
interface BleNostrServerListener {
    fun onClientConnected(server: BleNostrServer)

    fun onClientDisconnected(server: BleNostrServer)

    /**
     * A complete NIP-01 command was received from the client.
     */
    fun onCommandReceived(
        server: BleNostrServer,
        cmdStr: String,
        cmd: Command,
    )
}
