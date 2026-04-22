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
package com.vitorpamplona.nestsclient.moq

import com.vitorpamplona.nestsclient.transport.WebTransportBidiStream
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * MoQ-transport draft version constants. Value shape: `0xff00000000 | draft`
 * is how some draft tests label versions; draft-ietf-moq-transport-17 is
 * commonly represented as 0xff000011 but implementations vary. The actual
 * version used on the wire is negotiated in [MoqSession.setup].
 *
 * Listed as `Long` to survive the MSB-set encoding on JVM.
 */
object MoqVersion {
    /** draft-ietf-moq-transport-11 (a popular stable draft). */
    const val DRAFT_11: Long = 0xff00000bL

    /** draft-ietf-moq-transport-17 (newer draft). */
    const val DRAFT_17: Long = 0xff000011L
}

/**
 * Session wrapper over a [WebTransportSession] that speaks MoQ-transport.
 *
 * Phase 3c-1 ships only [setup] — the CLIENT_SETUP / SERVER_SETUP handshake.
 * Phase 3c-2 adds SUBSCRIBE + ANNOUNCE + object-stream multiplexing.
 *
 * Usage:
 * ```
 * val session = MoqSession.client(webTransport).apply {
 *     setup(listOf(MoqVersion.DRAFT_17))
 * }
 * // session.selectedVersion is now the version the server picked.
 * ```
 */
class MoqSession private constructor(
    private val transport: WebTransportSession,
    /** The single bidi stream every MoQ exchange uses for control. */
    private val controlStream: WebTransportBidiStream,
    private val role: Role,
) {
    enum class Role { Client, Server }

    /** Server's selected version; null until [setup] returns. */
    var selectedVersion: Long? = null
        private set

    /** Parameters the server sent back in SERVER_SETUP. Empty until [setup] returns. */
    var serverParameters: List<SetupParameter> = emptyList()
        private set

    /**
     * Run the SETUP handshake.
     *
     * Client side: writes CLIENT_SETUP with [supportedVersions] + [clientParameters],
     *   then reads exactly one SERVER_SETUP, stores the result, and returns.
     * Server side: reads exactly one CLIENT_SETUP, selects the first mutually
     *   supported version from [supportedVersions] (where the local list is the
     *   set of versions the server is willing to accept), writes SERVER_SETUP,
     *   and returns.
     *
     * @throws MoqProtocolException if the peer sent an unexpected message, or
     *   if no version overlap exists (server side).
     */
    suspend fun setup(
        supportedVersions: List<Long>,
        clientParameters: List<SetupParameter> = emptyList(),
        handshakeTimeoutMs: Long = 10_000,
    ) {
        withTimeout(handshakeTimeoutMs) {
            when (role) {
                Role.Client -> runClientSetup(supportedVersions, clientParameters)
                Role.Server -> runServerSetup(supportedVersions, clientParameters)
            }
        }
    }

    private suspend fun runClientSetup(
        supportedVersions: List<Long>,
        clientParameters: List<SetupParameter>,
    ) {
        controlStream.write(MoqCodec.encode(ClientSetup(supportedVersions, clientParameters)))
        val reply = readOneMessage()
        val server =
            reply as? ServerSetup
                ?: throw MoqProtocolException("expected SERVER_SETUP, got ${reply.type.name}")
        if (server.selectedVersion !in supportedVersions) {
            throw MoqProtocolException(
                "server picked version 0x${server.selectedVersion.toString(16)} which we did not offer",
            )
        }
        selectedVersion = server.selectedVersion
        serverParameters = server.parameters
    }

    private suspend fun runServerSetup(
        acceptedVersions: List<Long>,
        serverParameters: List<SetupParameter>,
    ) {
        val incoming = readOneMessage()
        val client =
            incoming as? ClientSetup
                ?: throw MoqProtocolException("expected CLIENT_SETUP, got ${incoming.type.name}")
        val overlap =
            client.supportedVersions.firstOrNull { it in acceptedVersions }
                ?: throw MoqProtocolException(
                    "no mutually-supported MoQ version (client offered ${client.supportedVersions})",
                )
        controlStream.write(MoqCodec.encode(ServerSetup(overlap, serverParameters)))
        this.selectedVersion = overlap
        this.serverParameters = serverParameters
    }

    /**
     * Read exactly one full MoQ message from the control stream.
     *
     * Phase 3c-1 assumes a whole message fits in a single transport write
     * (SETUP frames are only a handful of bytes and the peer always writes
     * them atomically). Phase 3c-2 will replace this with a buffer-and-retry
     * loop for messages that may fragment across chunks.
     */
    private suspend fun readOneMessage(): MoqMessage {
        val chunk = controlStream.incoming().first()
        val decoded =
            MoqCodec.decode(chunk)
                ?: throw MoqProtocolException(
                    "control-stream chunk did not contain a complete MoQ frame (size=${chunk.size})",
                )
        return decoded.message
    }

    /** Close the underlying transport. */
    suspend fun close(
        code: Int = 0,
        reason: String = "",
    ) {
        controlStream.finish()
        transport.close(code, reason)
    }

    companion object {
        /**
         * Attach to a [WebTransportSession] in the client role. Opens the
         * control stream eagerly so [setup] doesn't need to manage stream
         * acquisition.
         */
        suspend fun client(transport: WebTransportSession): MoqSession {
            val control = transport.openBidiStream()
            return MoqSession(transport, control, Role.Client)
        }

        /**
         * Attach to a [WebTransportSession] in the server role over an
         * already-accepted control stream (usually the first bidi stream the
         * peer opened). Used in tests — a real server accepts the first bidi.
         */
        fun server(
            transport: WebTransportSession,
            control: WebTransportBidiStream,
        ): MoqSession = MoqSession(transport, control, Role.Server)
    }
}

/** Thrown when the peer violates the MoQ-transport state machine. */
class MoqProtocolException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
