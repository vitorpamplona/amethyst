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
package com.vitorpamplona.amethyst.model.nip60Cashu

import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuWalletFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuWalletQueryState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Account-scoped state holder for the NIP-60 Cashu wallet + NIP-61 nutzaps.
 *
 * Lives on [com.vitorpamplona.amethyst.model.Account], so it stays alive for
 * the lifetime of the login session — not just while the wallet screen is
 * visible. This matters because:
 *
 *  - Inbound nutzaps (kind 9321) need to be auto-redeemed when they arrive,
 *    regardless of which screen the user is on.
 *  - The wallet event + token events need to land in [LocalCache] on first
 *    launch (or on fresh device sign-in) without requiring the user to open
 *    the wallet screen first.
 *
 * Mirrors the shape of [com.vitorpamplona.amethyst.model.nip47WalletConnect.NwcSignerState]
 * — a single state object on Account, ViewModels are thin presenters.
 *
 * Reactivity: subscribes to [LocalCache.live.newEventBundles] (and the
 * delete bundle) and re-indexes any incoming NIP-60/NIP-61 event authored by
 * or addressed to this account. The full [LocalCache.notes] map is scanned
 * ONCE during init to backfill; after that all updates are incremental.
 *
 * Auto-redeem is serialized through a [Mutex] so concurrent cache updates
 * don't fire duplicate /v1/swap calls against the mint.
 */
class CashuWalletState(
    private val pubKey: HexKey,
    private val signer: NostrSigner,
    private val cache: LocalCache,
    private val scope: CoroutineScope,
    private val assembler: CashuWalletFilterAssembler,
    private val outboxRelaysFlow: StateFlow<Set<NormalizedRelayUrl>>,
    okHttpClient: (String) -> OkHttpClient,
) {
    val ops: CashuWalletOps =
        CashuWalletOps(
            signer = signer,
            publish = ::publishEvent,
            okHttpClient = okHttpClient,
        )

    // ============================================================
    // Raw indexes — keyed by event id, mutated only on the cache thread.
    // ============================================================
    private var walletEventInternal: CashuWalletEvent? = null
    private val tokenEvents = ConcurrentHashMap<HexKey, CashuTokenEvent>()
    private val historyEvents = ConcurrentHashMap<HexKey, CashuSpendingHistoryEvent>()
    private val quoteEvents = ConcurrentHashMap<HexKey, CashuMintQuoteEvent>()
    private val nutzapEvents = ConcurrentHashMap<HexKey, NutzapEvent>()

    /** NIP-44 decryption cache for token contents, keyed by event id. */
    private val tokenContents = ConcurrentHashMap<HexKey, TokenContent>()
    private val redeemMutex = Mutex()

    // ============================================================
    // Public flows
    // ============================================================
    private val _walletEvent = MutableStateFlow<CashuWalletEvent?>(null)
    val walletEvent: StateFlow<CashuWalletEvent?> = _walletEvent.asStateFlow()

    private val _mints = MutableStateFlow<List<String>>(emptyList())
    val mints: StateFlow<List<String>> = _mints.asStateFlow()

    private val _tokenEntries = MutableStateFlow<List<TokenEntry>>(emptyList())
    val tokenEntries: StateFlow<List<TokenEntry>> = _tokenEntries.asStateFlow()

    val balanceSats: StateFlow<Long> =
        _tokenEntries
            .map { entries -> entries.sumOf { it.content.totalAmount() } }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, 0L)

    private val _history = MutableStateFlow<List<CashuSpendingHistoryEvent>>(emptyList())
    val history: StateFlow<List<CashuSpendingHistoryEvent>> = _history.asStateFlow()

    /** Unfulfilled, unexpired kind:7374 events — surfaced for resume UI. */
    private val _pendingQuotes = MutableStateFlow<List<CashuMintQuoteEvent>>(emptyList())
    val pendingQuotes: StateFlow<List<CashuMintQuoteEvent>> = _pendingQuotes.asStateFlow()

    fun hasWallet(): Boolean = _walletEvent.value != null

    /** Read the wallet's P2PK pubkey (33-byte compressed hex). */
    suspend fun p2pkPubkeyHex(): String? {
        val priv = walletPrivkeyHex() ?: return null
        return Secp256k1
            .pubKeyCompress(Secp256k1.pubkeyCreate(priv.hexToByteArray()))
            .toHexKey()
    }

    private suspend fun walletPrivkeyHex(): String? =
        _walletEvent.value?.let { evt ->
            runCatching { evt.privkey(signer) }.getOrNull()
        }

    // ============================================================
    // Lifecycle
    // ============================================================
    private val jobs = mutableListOf<Job>()
    private var currentSubscription: CashuWalletQueryState? = null

    init {
        // Backfill from cache once.
        scope.launch(Dispatchers.Default) {
            val initial = scanCacheForOwnEvents()
            applyEvents(initial)
            recomputePending()
            triggerAutoRedeem()
        }

        // Keep the relay subscription in sync with the outbox set.
        jobs +=
            scope.launch(Dispatchers.IO) {
                outboxRelaysFlow.collect { relays ->
                    syncSubscription(relays)
                }
            }

        // Reactive incremental update: any new event arrival that matches our
        // pubkey + the NIP-60/61 kinds we care about gets indexed.
        jobs +=
            scope.launch(Dispatchers.Default) {
                cache.live.newEventBundles.collect { notes ->
                    val ours = notes.mapNotNull { it.event }.filter(::isRelevantEvent)
                    if (ours.isNotEmpty()) {
                        applyEvents(ours)
                        recomputePending()
                        triggerAutoRedeem()
                    }
                }
            }

        jobs +=
            scope.launch(Dispatchers.Default) {
                cache.live.deletedEventBundles.collect { notes ->
                    val ids = notes.mapNotNull { it.event?.id }.toSet()
                    if (ids.isNotEmpty()) removeEvents(ids)
                }
            }
    }

    fun destroy() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        currentSubscription?.let { runCatching { assembler.unsubscribe(it) } }
        currentSubscription = null
    }

    // ============================================================
    // Subscription management
    // ============================================================
    private fun syncSubscription(relays: Set<NormalizedRelayUrl>) {
        val previous = currentSubscription
        if (relays.isEmpty()) {
            previous?.let { runCatching { assembler.unsubscribe(it) } }
            currentSubscription = null
            return
        }
        if (previous != null && previous.relays == relays) return // unchanged

        previous?.let { runCatching { assembler.unsubscribe(it) } }
        val next = CashuWalletQueryState(pubKey, relays)
        currentSubscription = next
        assembler.subscribe(next)
    }

    // ============================================================
    // Indexing
    // ============================================================
    private fun isRelevantEvent(event: Event): Boolean =
        when (event) {
            is CashuWalletEvent, is CashuTokenEvent, is CashuSpendingHistoryEvent,
            is CashuMintQuoteEvent,
            -> event.pubKey == pubKey
            // Inbound nutzaps: addressed to us, possibly authored by someone
            // else. Match by the recipient `#p` tag.
            is NutzapEvent -> event.tags.any { it.size >= 2 && it[0] == "p" && it[1] == pubKey }
            else -> false
        }

    private suspend fun applyEvents(events: List<Event>) {
        var dirtyWallet = false
        var dirtyTokens = false
        var dirtyHistory = false
        var dirtyQuotes = false
        var dirtyNutzaps = false

        for (event in events) {
            when (event) {
                is CashuWalletEvent -> {
                    // Replaceable: keep the latest by created_at.
                    val current = walletEventInternal
                    if (current == null || event.createdAt > current.createdAt) {
                        walletEventInternal = event
                        dirtyWallet = true
                    }
                }
                is CashuTokenEvent -> {
                    if (tokenEvents.put(event.id, event) == null) dirtyTokens = true
                }
                is CashuSpendingHistoryEvent -> {
                    if (historyEvents.put(event.id, event) == null) dirtyHistory = true
                }
                is CashuMintQuoteEvent -> {
                    if (quoteEvents.put(event.id, event) == null) dirtyQuotes = true
                }
                is NutzapEvent -> {
                    if (nutzapEvents.put(event.id, event) == null) dirtyNutzaps = true
                }
                else -> Unit
            }
        }

        if (dirtyWallet) {
            _walletEvent.value = walletEventInternal
            walletEventInternal?.let { evt ->
                _mints.value =
                    runCatching { evt.mints(signer) }
                        .onFailure { Log.w("CashuWallet") { "Failed to decrypt wallet mints: ${it.message}" } }
                        .getOrNull() ?: emptyList()
            } ?: run { _mints.value = emptyList() }
        }
        if (dirtyTokens) recomputeUnspent()
        if (dirtyHistory) {
            _history.value = historyEvents.values.sortedByDescending { it.createdAt }
        }
        if (dirtyQuotes || dirtyHistory) {
            // History gains might mark quotes as fulfilled (via the "destroyed"
            // kind:7374 reference); recompute the pending list.
            recomputePending()
        }
        if (dirtyNutzaps) {
            triggerAutoRedeem()
        }
    }

    private suspend fun removeEvents(ids: Set<HexKey>) {
        var dirtyTokens = false
        var dirtyHistory = false
        var dirtyQuotes = false
        var dirtyNutzaps = false
        var dirtyWallet = false

        ids.forEach { id ->
            if (tokenEvents.remove(id) != null) {
                dirtyTokens = true
                tokenContents.remove(id)
            }
            if (historyEvents.remove(id) != null) dirtyHistory = true
            if (quoteEvents.remove(id) != null) dirtyQuotes = true
            if (nutzapEvents.remove(id) != null) dirtyNutzaps = true
            if (walletEventInternal?.id == id) {
                walletEventInternal = null
                dirtyWallet = true
            }
        }

        if (dirtyWallet) {
            _walletEvent.value = null
            _mints.value = emptyList()
        }
        if (dirtyTokens) recomputeUnspent()
        if (dirtyHistory) _history.value = historyEvents.values.sortedByDescending { it.createdAt }
        if (dirtyQuotes || dirtyHistory) recomputePending()
        // dirtyNutzaps would trigger UI surfacing for inbound nutzaps; auto-
        // redeem already fires from the live-event observer, so no extra
        // signal is needed here.
        if (dirtyNutzaps) Unit
    }

    private suspend fun recomputeUnspent() {
        val all = tokenEvents.values.toList()
        // Decrypt anything we haven't seen before; reuse cached TokenContent
        // for events we've already decrypted.
        all.forEach { evt ->
            tokenContents.getOrPut(evt.id) {
                runCatching { evt.tokenContent(signer) }
                    .onFailure { Log.w("CashuWallet") { "Failed to decrypt token ${evt.id.take(8)}: ${it.message}" } }
                    .getOrNull() ?: return@getOrPut return@forEach
            }
        }

        // Apply `del` rollover.
        val deletedIds = mutableSetOf<HexKey>()
        all.forEach { evt -> tokenContents[evt.id]?.del?.let(deletedIds::addAll) }

        val unspent =
            all
                .filter { it.id !in deletedIds && tokenContents.containsKey(it.id) }
                .mapNotNull { evt -> tokenContents[evt.id]?.let { TokenEntry(evt, it) } }
                .sortedByDescending { it.event.createdAt }

        _tokenEntries.value = unspent
    }

    private fun recomputePending() {
        val now = TimeUtils.now()
        // A quote is "pending" if (1) not expired, and (2) no kind:7376 history
        // event references its id with a "destroyed" marker — completion of the
        // mint flow deletes the kind:7374, and history records a `destroyed`
        // reference to the now-fulfilled quote.
        val destroyedQuoteIds =
            historyEvents.values
                .asSequence()
                .flatMap { it.tags.asSequence() }
                .filter { it.size >= 4 && it[0] == "e" && it[3] == "destroyed" }
                .map { it[1] }
                .toSet()

        _pendingQuotes.value =
            quoteEvents.values
                .filter { it.id !in destroyedQuoteIds }
                .filter { evt ->
                    val exp =
                        evt.tags
                            .firstOrNull { it.size >= 2 && it[0] == "expiration" }
                            ?.get(1)
                            ?.toLongOrNull()
                    exp == null || exp > now
                }.sortedByDescending { it.createdAt }
    }

    private fun scanCacheForOwnEvents(): List<Event> {
        val collected = mutableListOf<Event>()
        cache.notes.forEach { _, note ->
            val e = note.event ?: return@forEach
            if (isRelevantEvent(e)) collected += e
        }
        return collected
    }

    // ============================================================
    // Auto-redeem of inbound NIP-61 nutzaps
    // ============================================================
    private fun triggerAutoRedeem() {
        scope.launch(Dispatchers.IO) { redeemPendingNutzapsSerialized() }
    }

    private suspend fun redeemPendingNutzapsSerialized() {
        if (!redeemMutex.tryLock()) return // a sweep is already in flight
        try {
            val privkey = walletPrivkeyHex() ?: return
            val pubkey = p2pkPubkeyHex() ?: return
            val alreadyRedeemed =
                historyEvents.values
                    .flatMap { it.redeemedReferences() }
                    .map { it.eventId }
                    .toSet()

            val candidates = nutzapEvents.values.filter { it.id !in alreadyRedeemed }
            if (candidates.isEmpty()) return

            for (ev in candidates) {
                runCatching { ops.redeemNutzap(ev, privkey, pubkey) }
                    .onFailure { e ->
                        Log.w("CashuWallet") {
                            "Auto-redeem of nutzap ${ev.id.take(8)} failed: ${describeMintError(e)}"
                        }
                    }
            }
        } finally {
            redeemMutex.unlock()
        }
    }

    // ============================================================
    // Publish bridge
    // ============================================================

    /**
     * Bridge for [CashuWalletOps.publish]. Concrete `Account` plugs in its
     * `sendLiterallyEverywhere` via the constructor-time wiring. We keep this
     * delegate field separate to avoid an Account ↔ State direct dependency.
     */
    var publishDelegate: suspend (Event) -> Unit = { /* set by Account */ }

    private suspend fun publishEvent(event: Event) {
        publishDelegate(event)
    }
}
