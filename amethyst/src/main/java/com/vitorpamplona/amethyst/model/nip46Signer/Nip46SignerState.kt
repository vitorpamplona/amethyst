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
package com.vitorpamplona.amethyst.model.nip46Signer

import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Runs Amethyst as a NIP-46 remote signer ("bunker") for the account, so other
 * apps can sign through it. While [AccountSettings.nip46SignerEnabled] is on, a
 * [NostrConnectSignerService] listens on the user's inbox relays (plus any relays
 * pulled in by a pasted `nostrconnect://` offer) for kind:24133 requests.
 *
 * Two keys are kept apart: a dedicated local [transportSigner] wraps/unwraps the
 * kind-24133 envelope (so the bunker address never reveals the user, and an
 * external NIP-55 account pays no IPC cost for envelope crypto), while the actual
 * sign/encrypt/decrypt and `get_public_key` use the account's identity [signer] —
 * a local key or a NIP-55 external app, whichever the user logged in with.
 *
 * Every request is gated by [Nip46PermissionAuthorizer], i.e. the same
 * "Connected Apps" trust ledger that governs napplets and web origins: a remote
 * client is a connected app under the coordinate `nip46:<signerPubKey>:<clientPubKey>`.
 *
 * The listener restarts whenever the enabled flag or the relay set changes
 * ([collectLatest] cancels the previous run), so editing inbox relays or toggling
 * the feature takes effect immediately.
 */
class Nip46SignerState(
    val signer: NostrSigner,
    val client: INostrClient,
    val ledger: NostrSignerPermissionLedger,
    val clientStore: Nip46ClientStore,
    val inboxRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    /** Relays contributed by pasted `nostrconnect://` offers this session, unioned with the inbox set. */
    private val extraRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /**
     * The dedicated per-account transport signer that wraps the kind-24133 envelope — a local key
     * unrelated to the account identity, so the bunker address/traffic doesn't reveal who it is for,
     * and (unlike the identity signer) an external NIP-55 account pays no IPC cost for envelope crypto.
     * Generated + persisted lazily on first use so accounts that never enable the signer mint nothing.
     */
    private val transportSigner: NostrSignerInternal by lazy { NostrSignerInternal(KeyPair(ensureTransportKeyBytes())) }

    /** All relays the signer listens on: the account inbox plus any nostrconnect offer relays. */
    val listeningRelays: StateFlow<Set<NormalizedRelayUrl>> =
        combine(inboxRelays, extraRelays) { inbox, extra -> inbox + extra }
            .stateIn(scope, SharingStarted.Eagerly, inboxRelays.value)

    private val authorizer =
        Nip46PermissionAuthorizer(
            ledger = ledger,
            signerPubKey = signer.pubKey,
            validateSecret = { clientPubKey, offered ->
                // A new app pairs with the current bunker secret; an already-connected app
                // re-authenticates by identity (it already holds a trust level in the ledger).
                val secret = settings.nip46BunkerSecret.value
                (secret.isNotEmpty() && offered == secret) ||
                    ledger.hasPolicy(Nip46PermissionAuthorizer.coordinateFor(signer.pubKey, clientPubKey))
            },
            onConnected = { clientPubKey, request ->
                // A bunker-flow client talks to us on the inbox relays we always listen on, so we
                // only persist its self-declared display metadata (never as authorization — just a label).
                val meta = request.clientMetadata
                if (meta != null && !meta.isEmpty()) {
                    clientStore.store(
                        Nip46PermissionAuthorizer.coordinateFor(signer.pubKey, clientPubKey),
                        Nip46ClientInfo(name = meta.name, url = meta.url, image = meta.image),
                    )
                }
            },
            clientStore = clientStore,
            // A forgotten client's relays are gone from the store now; recompute the listen set so we
            // stop listening on them this session instead of waiting for a restart.
            onDisconnected = { refreshExtraRelaysFromStore() },
        )

    init {
        // extraRelays is a live projection of the persisted client store (the nostrconnect apps' own
        // relays). Load it on start so paired apps stay reachable across restarts; it is refreshed
        // whenever a client connects or is forgotten (bunker-flow apps use the inbox relays instead).
        scope.launch(Dispatchers.IO) { refreshExtraRelaysFromStore() }

        scope.launch(Dispatchers.IO) {
            combine(settings.nip46SignerEnabled, listeningRelays) { enabled, relays -> enabled to relays }
                // Inbox/relay StateFlows can re-emit an identical set; without this every duplicate
                // would tear the subscription down and re-open it on every relay for no reason.
                .distinctUntilChanged()
                .collectLatest { (enabled, relays) ->
                    if (!enabled) return@collectLatest
                    if (!signer.isWriteable()) {
                        Log.w("NIP46Signer") { "signer not writeable; cannot host a bunker" }
                        return@collectLatest
                    }
                    if (relays.isEmpty()) return@collectLatest

                    // Envelope wrapped with the local transport key; the actual work (and get_public_key)
                    // uses the account's identity signer inside the processor.
                    val processor = BunkerRequestProcessor(signer, { listeningRelays.value }, authorizer)
                    val service =
                        NostrConnectSignerService(
                            client = client,
                            transportSigner = transportSigner,
                            processor = processor,
                            relays = relays,
                            onServiced = { method, clientPubKey, error ->
                                Log.d("NIP46Signer") { "$method from ${clientPubKey.take(8)}… → ${error ?: "ok"}" }
                            },
                        )
                    service.run()
                }
        }
    }

    /** Whether the account is currently advertising itself as a signer. */
    val enabled: StateFlow<Boolean> get() = settings.nip46SignerEnabled

    fun setEnabled(enabled: Boolean) {
        if (enabled) ensureSecret()
        settings.changeNip46SignerEnabled(enabled)
    }

    /** The `bunker://<transport-pubkey>?relay=…&secret=…` string to paste into another app. Generates keys/secret if needed. */
    fun bunkerUri(): String {
        val secret = ensureSecret()
        // Advertise the transport key, not the identity key, so the address doesn't reveal who we are.
        return NostrConnectURI.buildBunker(transportSigner.pubKey, inboxRelays.value, secret)
    }

    /** Replaces the pairing secret with a fresh one, revoking the ability of not-yet-connected apps to use the old one. */
    fun regenerateSecret(): String {
        val fresh = RandomInstance.randomChars(32)
        settings.changeNip46BunkerSecret(fresh)
        return fresh
    }

    /** Recomputes [extraRelays] from the persisted client store — the source of truth for nostrconnect relays. */
    private suspend fun refreshExtraRelaysFromStore() {
        extraRelays.value =
            clientStore
                .all()
                .filterKeys { Nip46PermissionAuthorizer.belongsTo(it, signer.pubKey) }
                .values
                .flatMap { it.relays }
                .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                .toSet()
    }

    /**
     * Forgets a connected client (the user's "Forget" action): revokes its grant, drops its stored
     * metadata/relays, and stops listening on relays that only it used. Same path as a client-sent
     * `logout`, so both are consistent.
     */
    suspend fun forgetClient(clientPubKey: HexKey) = authorizer.forget(clientPubKey)

    /** Returns the current pairing secret, generating and persisting one the first time. */
    private fun ensureSecret(): String {
        val current = settings.nip46BunkerSecret.value
        if (current.isNotEmpty()) return current
        val fresh = RandomInstance.randomChars(32)
        settings.changeNip46BunkerSecret(fresh)
        return fresh
    }

    /** The transport private key bytes, generating and persisting a fresh keypair the first time (or if corrupt). */
    private fun ensureTransportKeyBytes(): ByteArray {
        val stored = settings.nip46TransportKey.value
        val existing = stored.takeIf { it.length == 64 }?.let { runCatching { it.hexToByteArray() }.getOrNull() }
        if (existing != null) return existing
        val fresh = KeyPair()
        settings.changeNip46TransportKey(fresh.privKey!!.toHexKey())
        return fresh.privKey!!
    }

    /**
     * The client-initiated (`nostrconnect://`) pairing flow: parse a client's
     * offer, send the connect ack that echoes its secret (so the client learns
     * our signer pubkey), register it as a connected app, and start listening on
     * its relays. Enables the signer if it was off.
     */
    suspend fun connectViaNostrConnect(uri: String): ConnectResult {
        val offer = NostrConnectURI.parseNostrConnect(uri) ?: return ConnectResult.InvalidUri
        if (offer.relays.isEmpty()) return ConnectResult.NoRelays
        if (!signer.isWriteable()) return ConnectResult.NotWriteable

        return try {
            // Echo the offer secret back to the client, authored by the transport key so the client
            // learns THAT as our remote-signer pubkey (not our identity).
            val ack = BunkerResponse(newSubId(), offer.secret, null)
            val reply = NostrConnectEvent.create(ack, offer.clientPubKey, transportSigner)
            client.publish(reply, offer.relays)

            // Register the app (the paste is the user's consent) and listen on its relays.
            val coordinate = authorizer.coordinateFor(offer.clientPubKey)
            if (!ledger.hasPolicy(coordinate)) {
                ledger.setPolicy(coordinate, authorizer.defaultPolicyOnConnect)
            }
            ledger.updateLastUsed(coordinate)
            // Persist the app's label + its relays so it survives a restart, then start listening now.
            clientStore.store(
                coordinate,
                Nip46ClientInfo(name = offer.name, url = offer.url, image = offer.image, relays = offer.relays.map { it.url }.toSet()),
            )
            refreshExtraRelaysFromStore()

            setEnabled(true)
            ConnectResult.Connected(offer.clientPubKey, offer.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NIP46Signer") { "nostrconnect pairing failed: ${e.message}" }
            ConnectResult.Failed(e.message ?: "unknown error")
        }
    }

    sealed interface ConnectResult {
        data class Connected(
            val clientPubKey: String,
            val name: String?,
        ) : ConnectResult

        data object InvalidUri : ConnectResult

        data object NoRelays : ConnectResult

        data object NotWriteable : ConnectResult

        data class Failed(
            val reason: String,
        ) : ConnectResult
    }
}
