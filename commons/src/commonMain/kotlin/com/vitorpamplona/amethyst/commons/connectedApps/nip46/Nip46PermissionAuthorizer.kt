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
package com.vitorpamplona.amethyst.commons.connectedApps.nip46

import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
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
import com.vitorpamplona.quartz.utils.TimeUtils

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
    /** The user's own signer pubkey — namespaces this account's grants in the app-global store. */
    val signerPubKey: HexKey,
    /** Validates the connect secret for a client (bunker secret, or the offer secret in the nostrconnect flow). */
    val validateSecret: suspend (clientPubKey: HexKey, offeredSecret: String?) -> Boolean,
    /** Trust level assigned to a freshly paired app that has no policy yet. */
    val defaultPolicyOnConnect: AppSignerPolicy = AppSignerPolicy.REASONABLE,
    /** Invoked after a successful connect so the host can persist display metadata (name/url/image). */
    val onConnected: (suspend (clientPubKey: HexKey, request: BunkerRequestConnect) -> Unit)? = null,
    /** Persisted client metadata/relays; cleared on logout so a disconnected app leaves nothing behind. */
    val clientStore: Nip46ClientStore? = null,
) : Nip46RequestAuthorizer {
    // A high-throughput client can authorize many signs per second; last-used is display-only,
    // so coalesce the DataStore write to at most one per client per LAST_USED_THROTTLE_SECS
    // instead of writing the whole per-client preferences file on every request.
    private val lastUsedThrottle = mutableMapOf<String, Long>()
    private val throttleLock = KmpLock()

    /** The ledger coordinate for [clientPubKey] under this account. */
    fun coordinateFor(clientPubKey: HexKey): String = coordinateFor(signerPubKey, clientPubKey)

    private suspend fun touchLastUsed(coordinate: String) {
        val now = TimeUtils.now()
        val shouldWrite =
            throttleLock.withLock {
                val previous = lastUsedThrottle[coordinate] ?: 0L
                if (now - previous >= LAST_USED_THROTTLE_SECS) {
                    lastUsedThrottle[coordinate] = now
                    true
                } else {
                    false
                }
            }
        if (shouldWrite) ledger.updateLastUsed(coordinate, now)
    }

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
        touchLastUsed(coordinate)
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
        if (allowed) touchLastUsed(coordinate)
        return allowed
    }

    override suspend fun onLogout(clientPubKey: HexKey) {
        // The client asked to disconnect — drop its standing grant (so it must pair again) and its
        // persisted metadata/relays (so we stop listening on its relays after the next restart).
        val coordinate = coordinateFor(clientPubKey)
        ledger.revokeAll(coordinate)
        clientStore?.remove(coordinate)
    }

    companion object {
        /** Ledger coordinate namespace for NIP-46 remote-signer clients. */
        const val COORDINATE_PREFIX = "nip46"

        private const val ACK = "ack"

        /** Minimum seconds between persisted last-used updates for one client (write-coalescing). */
        private const val LAST_USED_THROTTLE_SECS = 60L

        /**
         * The Connected-Apps ledger coordinate for a NIP-46 client, namespaced by
         * the user's signer so the same client paired with two accounts on one
         * device gets independent grants: `nip46:<signerPubKey>:<clientPubKey>`.
         */
        fun coordinateFor(
            signerPubKey: HexKey,
            clientPubKey: HexKey,
        ): String = "$COORDINATE_PREFIX:$signerPubKey:$clientPubKey"

        /** True when [coordinate] is a NIP-46 grant belonging to [signerPubKey]. */
        fun belongsTo(
            coordinate: String,
            signerPubKey: HexKey,
        ): Boolean = coordinate.startsWith("$COORDINATE_PREFIX:$signerPubKey:")

        /** The client pubkey of a `nip46:<signer>:<client>` coordinate, or `null` if it is not one. */
        fun clientPubKeyOf(coordinate: String): HexKey? = if (coordinate.startsWith("$COORDINATE_PREFIX:")) coordinate.substringAfterLast(':') else null

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
