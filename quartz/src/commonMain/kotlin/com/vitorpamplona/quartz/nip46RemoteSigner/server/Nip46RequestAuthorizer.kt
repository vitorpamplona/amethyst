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
package com.vitorpamplona.quartz.nip46RemoteSigner.server

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect

/**
 * The authorization boundary a NIP-46 signer (a "bunker") consults before it
 * performs a request on the user's behalf. It is the protocol-agnostic hook the
 * host app uses to plug in *its* permission model — in Amethyst that is the
 * shared "Connected Apps" ledger, so the same trust levels that gate napplets
 * and web apps also gate remote NIP-46 clients.
 *
 * [BunkerRequestProcessor] owns the wire logic; this interface owns "may this
 * client do this?". Everything here is `suspend` so an implementation may block
 * on disk, a live user prompt, or IPC without changing the processor.
 *
 * Public, harmless requests (`get_public_key`, `ping`, `get_relays`) are NOT
 * routed through [authorize]; only signing, encryption and decryption are.
 */
interface Nip46RequestAuthorizer {
    /**
     * Called when a client sends a `connect` request. The implementation
     * validates the offered secret (the `bunker://…?secret=…` pairing token),
     * registers the client as a connected app if it accepts, and returns the
     * decision. On accept the returned [Nip46ConnectDecision.Accept.ackSecret]
     * is echoed to the client (the offered secret, or `"ack"` when none was set).
     */
    suspend fun onConnect(
        clientPubKey: HexKey,
        request: BunkerRequestConnect,
    ): Nip46ConnectDecision

    /**
     * Called before every signing/encryption/decryption request. Return `true`
     * to perform it, `false` to reject the client with an "unauthorized" error.
     * Implementations typically map [request] to a per-app permission and read
     * the standing grant/deny decision for [clientPubKey].
     */
    suspend fun authorize(
        clientPubKey: HexKey,
        request: BunkerRequest,
    ): Boolean
}

/** The verdict for a NIP-46 `connect` request. */
sealed class Nip46ConnectDecision {
    /**
     * Accept the connection. [ackSecret] is echoed back to the client as the
     * connect result — the offered secret when one was set (so the client can
     * validate it), otherwise `"ack"`.
     */
    data class Accept(
        val ackSecret: String,
    ) : Nip46ConnectDecision()

    /** Reject the connection; [reason] is returned to the client as an error. */
    data class Reject(
        val reason: String,
    ) : Nip46ConnectDecision()
}
