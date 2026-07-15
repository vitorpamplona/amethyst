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
package com.vitorpamplona.amethyst.commons.napplet.signers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer

/**
 * Bridges the NIP-46 signer core to Amethyst's shared "Connected Apps" trust
 * model: a remote client that signs through Amethyst is a connected app just
 * like a napplet or a sandboxed web origin, gated by the same
 * [NostrSignerPermissionLedger] (per-app [AppSignerPolicy] + per-op
 * [NostrOpDecision]) and surfaced on the same management screen.
 *
 * A NIP-46 client is identified by its transport pubkey, mapped to the ledger
 * coordinate `nip46:<clientPubKey>` (see [coordinateFor]) so it lives in its
 * own namespace next to `browser:<origin>` and napplet `<author>:<id>` keys.
 *
 * Authorization mirrors the napplet path:
 *  - each signing/encryption/decryption request maps to a [NostrSignerOp]
 *    ([sign:kind][NostrSignerOp.SignKind] / [encrypt][NostrSignerOp.Encrypt] /
 *    [decrypt][NostrSignerOp.Decrypt]) and is allowed only when the ledger's
 *    standing decision is [NostrOpDecision.ALLOW]. `ASK`/`DENY` are refused —
 *    a background signer cannot raise an interactive prompt, so the user grants
 *    access ahead of time by choosing a trust level (or a per-op override) in
 *    Connected Apps.
 *  - a `connect` request first validates the pairing secret via
 *    [validateSecret]; on success the app is registered at
 *    [defaultPolicyOnConnect] (only if it has no policy yet — never downgrading
 *    a level the user already chose), then [onConnected] runs so the host can
 *    record display metadata.
 */
class Nip46PermissionAuthorizer(
    val ledger: NostrSignerPermissionLedger,
    /** Validates the connect secret for a client (bunker secret, or the offer secret in the nostrconnect flow). */
    val validateSecret: suspend (clientPubKey: HexKey, offeredSecret: String?) -> Boolean,
    /** Trust level assigned to a freshly paired app that has no policy yet. */
    val defaultPolicyOnConnect: AppSignerPolicy = AppSignerPolicy.REASONABLE,
    /** Invoked after a successful connect so the host can persist display metadata (name/url/image). */
    val onConnected: (suspend (clientPubKey: HexKey, request: BunkerRequestConnect) -> Unit)? = null,
) : Nip46RequestAuthorizer {
    override suspend fun onConnect(
        clientPubKey: HexKey,
        request: BunkerRequestConnect,
    ): Nip46ConnectDecision {
        if (!validateSecret(clientPubKey, request.secret)) {
            return Nip46ConnectDecision.Reject("invalid secret")
        }

        val coordinate = coordinateFor(clientPubKey)
        if (!ledger.hasPolicy(coordinate)) {
            ledger.setPolicy(coordinate, defaultPolicyOnConnect)
        }
        ledger.updateLastUsed(coordinate)
        onConnected?.invoke(clientPubKey, request)

        // Echo the offered secret when present (the client validates it); otherwise ack.
        val echo = request.secret?.takeIf { it.isNotEmpty() } ?: ACK
        return Nip46ConnectDecision.Accept(echo)
    }

    override suspend fun authorize(
        clientPubKey: HexKey,
        request: BunkerRequest,
    ): Boolean {
        // Requests the core routes here always map to an op; if a future method
        // is added that does not, default to allow (the core only gates
        // sign/encrypt/decrypt, so this branch is a safety net).
        val op = request.toSignerOp() ?: return true
        val coordinate = coordinateFor(clientPubKey)
        val allowed = ledger.decide(coordinate, op) == NostrOpDecision.ALLOW
        if (allowed) ledger.updateLastUsed(coordinate)
        return allowed
    }

    companion object {
        /** Ledger coordinate namespace for NIP-46 remote-signer clients. */
        const val COORDINATE_PREFIX = "nip46"

        private const val ACK = "ack"

        /** The Connected-Apps ledger coordinate for a NIP-46 client, e.g. `nip46:<pubkey>`. */
        fun coordinateFor(clientPubKey: HexKey): String = "$COORDINATE_PREFIX:$clientPubKey"

        /** The client pubkey of a `nip46:` coordinate, or `null` if it is not one. */
        fun clientPubKeyOf(coordinate: String): HexKey? = if (coordinate.startsWith("$COORDINATE_PREFIX:")) coordinate.substringAfter(':') else null

        /** Maps a signing/encryption/decryption [BunkerRequest] to the [NostrSignerOp] it needs. */
        fun BunkerRequest.toSignerOp(): NostrSignerOp? =
            when (this) {
                is BunkerRequestSign -> NostrSignerOp.SignKind(event.kind)
                is BunkerRequestNip04Encrypt -> NostrSignerOp.Encrypt
                is BunkerRequestNip44Encrypt -> NostrSignerOp.Encrypt
                is BunkerRequestNip04Decrypt -> NostrSignerOp.Decrypt
                is BunkerRequestNip44Decrypt -> NostrSignerOp.Decrypt
                else -> null
            }
    }
}
