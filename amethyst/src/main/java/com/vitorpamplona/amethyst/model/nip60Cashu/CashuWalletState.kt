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
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.mintApi.DeterministicSecretFactory
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.ProofState
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.seed.CashuDeterministic
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
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
import kotlinx.coroutines.sync.withLock
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
 * Auto-redeem is serialized through a [Mutex], with successful redemptions
 * pinned in an in-memory set so a second triggerAutoRedeem firing before
 * the kind:7376 history event propagates back doesn't double-spend.
 */
class CashuWalletState(
    private val pubKey: HexKey,
    private val signer: NostrSigner,
    private val cache: LocalCache,
    private val scope: CoroutineScope,
    private val assembler: CashuWalletFilterAssembler,
    private val outboxRelaysFlow: StateFlow<Set<NormalizedRelayUrl>>,
    private val settings: AccountSettings,
    okHttpClient: (String) -> OkHttpClient,
) {
    val ops: CashuWalletOps =
        CashuWalletOps(
            signer = signer,
            publish = ::publishEvent,
            okHttpClient = okHttpClient,
            // NUT-13 wiring: the factory closure reads the cached seed at
            // mint-op time. cachedSeed is populated by ensureSeed() —
            // CashuWalletOps' seedWarmer below calls it before any blind
            // op so the cache is warm. When the cache is empty (no wallet
            // decrypted yet) the factory falls back to random, matching
            // pre-NUT-13 behaviour. Counter allocation is synchronous +
            // persistent via AccountSettings; an atomic read-modify-write
            // makes concurrent mints safe.
            secretFactory =
                DeterministicSecretFactory(
                    seedProvider = ::cachedSeedOrNull,
                    // Batched reservation: the factory asks for N
                    // counters at once (one for every output of the
                    // current mint/swap/melt), so the @Synchronized
                    // critical section + AccountSettings save fires
                    // once per op instead of per-output.
                    reserveCounters = { keysetId, count -> settings.reserveCashuCounters(keysetId, count) },
                ),
            seedWarmer = { ensureSeed() },
        )

    // ============================================================
    // Raw indexes — keyed by event id, mutated only on the cache thread.
    // ============================================================
    private var walletEventInternal: CashuWalletEvent? = null
    private var nutzapInfoEventInternal: NutzapInfoEvent? = null
    private val tokenEvents = ConcurrentHashMap<HexKey, CashuTokenEvent>()
    private val historyEvents = ConcurrentHashMap<HexKey, CashuSpendingHistoryEvent>()
    private val quoteEvents = ConcurrentHashMap<HexKey, CashuMintQuoteEvent>()
    private val nutzapEvents = ConcurrentHashMap<HexKey, NutzapEvent>()

    /**
     * NIP-87 cashu mint recommendations published by this account. Keyed by
     * dTag (the mint identifier — either the announcement d-tag when known
     * or the raw mint URL), so a re-published recommendation for the same
     * mint replaces the older one and we don't show duplicates in the
     * Settings screen list.
     */
    private val recommendationEvents = ConcurrentHashMap<String, MintRecommendationEvent>()

    /** NIP-44 decryption cache for token contents, keyed by event id. */
    private val tokenContents = ConcurrentHashMap<HexKey, TokenContent>()
    private val redeemMutex = Mutex()

    /**
     * Nutzap event ids whose redemption swap has already completed at
     * the mint this session. Recorded inside [redeemMutex] right after
     * the mint accepts the swap, so a second [triggerAutoRedeem] firing
     * before the kind:7376 history event propagates back through
     * [LocalCache.live.newEventBundles] still sees the nutzap as
     * redeemed and skips it — preventing the "proofs already spent"
     * double-redeem race. The set is process-local; on next launch the
     * persisted kind:7376 events rebuild equivalent state.
     */
    private val sessionRedeemedNutzaps = ConcurrentHashMap.newKeySet<HexKey>()

    /**
     * Nutzap event ids that failed redemption with a deterministic
     * (non-retryable) error this session — wrong P2PK lock, malformed
     * proofs, mint we don't trust. Tracked so the auto-redeem doesn't
     * hammer the mint with /v1/keys on every triggerAutoRedeem firing.
     * Without this, an inbound nutzap locked to a stale wallet key
     * (sender saw an old kind:10019) would cost one mint round-trip
     * for every cache update for the rest of the session. The set is
     * process-local — if the user rotates their P2PK key, restarting
     * gives the redeem another shot.
     */
    private val sessionUnredeemableNutzaps = ConcurrentHashMap.newKeySet<HexKey>()

    // ============================================================
    // Public flows
    // ============================================================
    private val _walletEvent = MutableStateFlow<CashuWalletEvent?>(null)
    val walletEvent: StateFlow<CashuWalletEvent?> = _walletEvent.asStateFlow()

    private val _nutzapInfoEvent = MutableStateFlow<NutzapInfoEvent?>(null)
    val nutzapInfoEvent: StateFlow<NutzapInfoEvent?> = _nutzapInfoEvent.asStateFlow()

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

    /**
     * NIP-87 mint recommendations this account has published — surfaced in
     * the Cashu Settings screen so the user can review and retract them.
     */
    private val _ownRecommendations = MutableStateFlow<List<MintRecommendationEvent>>(emptyList())
    val ownRecommendations: StateFlow<List<MintRecommendationEvent>> = _ownRecommendations.asStateFlow()

    /**
     * True while we're still waiting for relays to deliver an existing
     * wallet event for this account.
     *
     * NIP-60 wallets are portable across clients — if the user previously
     * created one in (say) Boardwalk or cashu.me, our subscription should
     * pull the kind:17375 in within a few seconds of sign-in. The UI uses
     * this flag to show a "Looking for your wallet…" state during that
     * window instead of jumping straight to the "Create" CTA, which would
     * overwrite the remote wallet (kind:17375 is replaceable).
     *
     * Cleared when:
     *   - a wallet event arrives via cache backfill or live update, OR
     *   - the discovery timeout elapses (~8 seconds), whichever first.
     */
    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    fun hasWallet(): Boolean = _walletEvent.value != null

    /** Read the wallet's P2PK pubkey (33-byte compressed hex). */
    suspend fun p2pkPubkeyHex(): String? {
        val priv = walletPrivkeyHex() ?: return null
        return Secp256k1
            .pubKeyCompress(Secp256k1.pubkeyCreate(priv.hexToByteArray()))
            .toHexKey()
    }

    /**
     * Read the wallet's P2PK private key. Used by the edit-wallet flow to
     * preserve the same nutzap key when only mints are being changed —
     * regenerating would orphan any inbound nutzaps locked to the old key.
     *
     * The signer round-trip means this is suspending and can fail (remote /
     * external signers may reject the decrypt). Callers should handle null.
     */
    suspend fun exportP2pkPrivkeyHex(): String? = walletPrivkeyHex()

    private suspend fun walletPrivkeyHex(): String? =
        _walletEvent.value?.let { evt ->
            runCatching { evt.privkey(signer) }.getOrNull()
        }

    /**
     * Cached NUT-13 master seed derived from the wallet's P2PK private
     * key. Volatile + double-checked lazy init — the seed never changes
     * for a given wallet (it's a pure function of the P2PK key, which is
     * a constant in kind:17375), so once derived we hold it for the
     * wallet's lifetime. Null until the first time something asks.
     *
     * Derivation: HMAC-SHA512("Cashu-Wallet-Seed-v1", p2pk_priv) — yields
     * a 64-byte seed shaped like BIP-39's PBKDF2 output. We use HMAC
     * rather than the raw private key so any downstream NIP-44-style
     * leakage of secrets derived from `seed` doesn't compromise the
     * P2PK key itself.
     */
    @Volatile private var cachedSeed: ByteArray? = null

    /**
     * Serialises [ensureSeed] so two concurrent first-time callers don't
     * both pay the signer round-trip for [walletPrivkeyHex]. The seed
     * derivation is pure (HMAC-SHA512 of the P2PK key) so even the
     * unprotected version would produce the same bytes — but for NIP-46
     * bunker signers the duplicate decrypt round-trip is observable
     * latency the user doesn't need to pay twice.
     */
    private val seedLoadMutex = Mutex()

    /**
     * Fetch (and cache on first call) the NUT-13 master seed. Returns
     * null when the wallet hasn't decrypted its kind:17375 yet — the
     * secret factory falls back to random in that window. Once the seed
     * is cached, every subsequent mint operation gets deterministic
     * secrets and the counter advances monotonically.
     *
     * Double-checked under [seedLoadMutex]: the unlocked fast-path reads
     * the cache; if empty, contend for the mutex and re-check (someone
     * else may have populated the cache while we waited).
     */
    private suspend fun ensureSeed(): ByteArray? {
        cachedSeed?.let { return it }
        return seedLoadMutex.withLock {
            cachedSeed?.let { return@withLock it }
            val priv = walletPrivkeyHex() ?: return@withLock null
            val seed = CashuDeterministic.deriveWalletSeed(priv.hexToByteArray())
            cachedSeed = seed
            seed
        }
    }

    /**
     * Synchronous seed accessor for the [SecretFactory] thunk. Returns
     * whatever's already in [cachedSeed] — does NOT trigger derivation
     * (which is suspend). Callers must invoke [ensureSeed] before the
     * mint op so the cache is warm by the time the factory queries it.
     */
    private fun cachedSeedOrNull(): ByteArray? = cachedSeed

    // ============================================================
    // Lifecycle
    // ============================================================
    private val jobs = mutableListOf<Job>()
    private var currentSubscription: CashuWalletQueryState? = null

    @Volatile private var started = false

    /**
     * Wire the publish bridge and begin observing the cache + relay flows.
     *
     * Account must call this from its own `init { }` block (after all field
     * initializers complete) — that guarantees `sendLiterallyEverywhere` and
     * its dependencies (`followPlusAllMineWithIndex`, etc.) are fully
     * constructed before the first auto-redeem might fire. Calling start()
     * inside the state's own `init { }` would race: the collectors could
     * publish via a half-built Account.
     */
    fun start(publish: suspend (Event) -> Unit) {
        if (started) return
        started = true
        this.publish = publish

        // Restore previously-seen wallet / nutzap-info events from the
        // on-disk backup (sibling of `backupContactList`, `backupNIP65RelayList`,
        // etc. in AccountSettings). Pushing them into LocalCache means the
        // wallet screen renders the user's existing wallet immediately on
        // launch, before any relay round-trip — important since kind:17375
        // is replaceable and relays may be slow or unreachable. The events
        // we just consumed will flow through `cache.live.newEventBundles`
        // and be re-indexed by `applyEvents()` like any other arrival.
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        scope.launch(Dispatchers.IO) {
            settings.backupCashuWallet?.let {
                Log.d("CashuWallet") { "Restoring cached kind:17375 from settings (id=${it.id.take(8)})" }
                LocalCache.justConsumeMyOwnEvent(it)
            }
            settings.backupNutzapInfo?.let {
                Log.d("CashuWallet") { "Restoring cached kind:10019 from settings (id=${it.id.take(8)})" }
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }

        // Show the "discovering" state until either a wallet event lands
        // (set inside applyEvents()) or the timeout below fires. Without
        // this the UI would render the empty-wallet CTA the moment the
        // screen opens — even for users whose wallet exists on relays but
        // hasn't yet been delivered — and tapping Create there would
        // overwrite the remote kind:17375.
        if (_walletEvent.value == null) {
            _discovering.value = true
            jobs +=
                scope.launch(Dispatchers.Default) {
                    kotlinx.coroutines.delay(DISCOVERY_TIMEOUT_MS)
                    _discovering.value = false
                }
        }

        // Backfill from cache once. Stale-proof healing is deferred
        // until the user actually sends/zaps ([scrubLocallyStaleProofs]
        // is called inline from [sendNutzap] / [sendAsToken]) — running
        // it here races against kind:7375 events that arrive from
        // relays after start() returns, so the sweep would find
        // _tokenEntries empty and no-op.
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
                    val all = notes.mapNotNull { it.event }

                    // Process our own NIP-09 deletions inline rather than
                    // relying on LocalCache's `deletedEventBundles`. That
                    // path only fires when `consume(DeletionEvent)` finds
                    // the target Note still resident in `notes` — a
                    // LargeSoftCache backed by WeakReferences, which can
                    // be cleared on any GC cycle. When the weak ref is
                    // gone (common between publish and the round-trip on
                    // a busy device), the cache's deleteNote/removedNote
                    // chain silently no-ops and our quoteEvents /
                    // tokenEvents / etc. retain the logically-deleted
                    // entries indefinitely — which manifested as the
                    // pending-invoice card not disappearing after the
                    // user tapped Discard. The kind:5 event itself does
                    // reach us via newEventBundles regardless of soft-
                    // cache state, so we extract its target ids and
                    // remove from our maps directly.
                    val ourDeleteIds =
                        all
                            .asSequence()
                            .filterIsInstance<DeletionEvent>()
                            .filter { it.pubKey == pubKey }
                            .flatMap { it.deleteEventIds().asSequence() }
                            .toSet()
                    if (ourDeleteIds.isNotEmpty()) removeEvents(ourDeleteIds)

                    val ours = all.filter(::isRelevantEvent)
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
            is CashuMintQuoteEvent, is NutzapInfoEvent,
            -> event.pubKey == pubKey
            // Inbound nutzaps: addressed to us, possibly authored by someone
            // else. Match by the recipient `#p` tag.
            is NutzapEvent -> event.tags.any { it.size >= 2 && it[0] == "p" && it[1] == pubKey }
            // NIP-87 mint recommendations: only our own, and only cashu-
            // scoped (the kind:38000 namespace covers fedimint too).
            is MintRecommendationEvent -> event.pubKey == pubKey && event.isCashuRecommendation()
            else -> false
        }

    private suspend fun applyEvents(events: List<Event>) {
        var dirtyWallet = false
        var dirtyNutzapInfo = false
        var dirtyTokens = false
        var dirtyHistory = false
        var dirtyQuotes = false
        var dirtyNutzaps = false
        var dirtyRecommendations = false

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
                is NutzapInfoEvent -> {
                    val current = nutzapInfoEventInternal
                    if (current == null || event.createdAt > current.createdAt) {
                        nutzapInfoEventInternal = event
                        dirtyNutzapInfo = true
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
                is MintRecommendationEvent -> {
                    // kind:38000 is parameterized-replaceable — keep only the
                    // newest event per (pubKey, dTag). Our isRelevantEvent
                    // already gates by self+cashu so we only see our own.
                    // dTag() is nullable on this event type; fall back to the
                    // event id so a missing d-tag doesn't collapse every
                    // such event into the same map slot.
                    val key = event.dTag() ?: event.id
                    val current = recommendationEvents[key]
                    if (current == null || event.createdAt > current.createdAt) {
                        recommendationEvents[key] = event
                        dirtyRecommendations = true
                    }
                }
                else -> Unit
            }
        }

        if (dirtyWallet) {
            _walletEvent.value = walletEventInternal
            // Any wallet event resolves the "discovering" state — whether it
            // came from cache backfill or a fresh relay delivery.
            _discovering.value = false
            walletEventInternal?.let { evt ->
                _mints.value =
                    runCatching { evt.mints(signer) }
                        .onFailure { Log.w("CashuWallet") { "Failed to decrypt wallet mints: ${it.message}" } }
                        .getOrNull() ?: emptyList()
                // Persist the wallet event in AccountSettings so we can
                // restore it before any relay round-trip on next launch —
                // same backup pattern as kind:0 / kind:3 / NIP-65.
                settings.updateCashuWallet(evt)
            } ?: run { _mints.value = emptyList() }
        }
        if (dirtyNutzapInfo) {
            _nutzapInfoEvent.value = nutzapInfoEventInternal
            nutzapInfoEventInternal?.let { settings.updateNutzapInfo(it) }
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
        if (dirtyRecommendations) {
            _ownRecommendations.value = recommendationEvents.values.sortedByDescending { it.createdAt }
        }
    }

    private suspend fun removeEvents(ids: Set<HexKey>) {
        var dirtyTokens = false
        var dirtyHistory = false
        var dirtyQuotes = false
        var dirtyNutzaps = false
        var dirtyWallet = false
        var dirtyNutzapInfo = false
        var dirtyRecommendations = false

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
            if (nutzapInfoEventInternal?.id == id) {
                nutzapInfoEventInternal = null
                dirtyNutzapInfo = true
            }
            // Recommendations are indexed by dTag, not event id — find by
            // matching event.id and drop the entry.
            val recoKey =
                recommendationEvents.entries.firstOrNull { it.value.id == id }?.key
            if (recoKey != null) {
                recommendationEvents.remove(recoKey)
                dirtyRecommendations = true
            }
        }

        if (dirtyWallet) {
            _walletEvent.value = null
            _mints.value = emptyList()
        }
        if (dirtyNutzapInfo) _nutzapInfoEvent.value = null
        if (dirtyTokens) recomputeUnspent()
        if (dirtyHistory) _history.value = historyEvents.values.sortedByDescending { it.createdAt }
        if (dirtyQuotes || dirtyHistory) recomputePending()
        // dirtyNutzaps would trigger UI surfacing for inbound nutzaps; auto-
        // redeem already fires from the live-event observer, so no extra
        // signal is needed here.
        if (dirtyNutzaps) Unit
        if (dirtyRecommendations) {
            _ownRecommendations.value = recommendationEvents.values.sortedByDescending { it.createdAt }
        }
    }

    private suspend fun recomputeUnspent() {
        val all = tokenEvents.values.toList()
        // Decrypt anything we haven't seen before; reuse cached TokenContent
        // for events we've already decrypted. Decryption failures are
        // skipped — the proof set rebuilds the next time a re-key happens.
        all.forEach { evt ->
            if (!tokenContents.containsKey(evt.id)) {
                val content =
                    runCatching { evt.tokenContent(signer) }
                        .onFailure {
                            Log.w("CashuWallet") {
                                "Failed to decrypt token ${evt.id.take(8)}: ${it.message}"
                            }
                        }.getOrNull()
                if (content != null) tokenContents[evt.id] = content
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
            val skipIds = HashSet<HexKey>()
            historyEvents.values.forEach { h ->
                h.redeemedReferences().forEach { skipIds.add(it.eventId) }
            }
            skipIds.addAll(sessionRedeemedNutzaps)
            skipIds.addAll(sessionUnredeemableNutzaps)

            val candidates = nutzapEvents.values.filter { it.id !in skipIds }
            if (candidates.isEmpty()) return

            for (ev in candidates) {
                try {
                    ops.redeemNutzap(ev, privkey, pubkey)
                    // Pin redemption in-memory immediately. The kind:7376
                    // history event is bundled via newEventBundles (~1s)
                    // before it lands in historyEvents — until then a
                    // concurrent trigger would re-pick this nutzap and
                    // get HTTP 400 "proofs already spent" from the mint.
                    sessionRedeemedNutzaps.add(ev.id)
                } catch (e: IllegalArgumentException) {
                    // Deterministic local failure (wrong P2PK lock, no
                    // mint tag, no proofs, not P2PK-locked). Won't get
                    // better by retrying — pin so the next cache update
                    // doesn't re-trigger the mint round-trips.
                    sessionUnredeemableNutzaps.add(ev.id)
                    Log.w("CashuWallet") {
                        "Auto-redeem of nutzap ${ev.id.take(8)} skipped: ${e.message}"
                    }
                } catch (e: Exception) {
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
    // Send nutzap
    // ============================================================

    /**
     * Information needed to nutzap [recipientPubKey], resolved from their
     * kind:10019 + our wallet's mint list. Returns null if:
     *  - we don't have a Cashu wallet,
     *  - the recipient hasn't published a kind:10019,
     *  - we share no mint with them, or
     *  - their kind:10019 has no P2PK pubkey.
     */
    fun peekNutzapTarget(recipientPubKey: HexKey): NutzapTarget? {
        val ourMints = _mints.value.toSet()
        if (ourMints.isEmpty()) return null

        // Read the recipient's kind:10019 via their User — User pins the
        // addressable note for its own lifetime, so the previous race
        // (notes.LargeSoftCache evicts the WeakReference even though the
        // event was delivered) no longer drops the chip.
        val info = cache.getOrCreateUser(recipientPubKey).nutzapInfo() ?: return null

        val recipientPubkeyHex = info.p2pkPubkey() ?: return null
        val shared = info.mints().firstOrNull { it.mintUrl in ourMints } ?: return null

        return NutzapTarget(
            mintUrl = shared.mintUrl,
            recipientP2pkPubkeyHex = recipientPubkeyHex,
        )
    }

    /**
     * NUT-09 wallet restore — recover proofs minted at [mintUrl] whose
     * kind:7375 token events have been lost. Scans deterministic
     * secret/r derivations from the wallet's NUT-13 seed and asks the
     * mint which it has signed; surviving UNSPENT proofs get published
     * as a fresh kind:7375 + kind:7376 IN history row, exactly like a
     * mint-from-LN flow.
     *
     * After recovery, advances [AccountSettings.cashuKeysetCounters]
     * past the highest recovered counter so subsequent mints don't
     * reuse a slot the mint already has signatures for.
     *
     * Returns null when the wallet hasn't yet decrypted its kind:17375
     * (no seed available); the UI should retry after the wallet event
     * is loaded.
     */
    suspend fun restoreFromMint(mintUrl: String): RestoreOutcome? {
        check(started) { "CashuWalletState.start() not called" }
        val seed = ensureSeed() ?: return null
        // Always scan from counter 0 — a fresh-device recovery doesn't
        // know which slots were used. The internal gap-limit heuristic
        // in CashuMintOperations.restore (3 consecutive empty batches)
        // bounds the work for wallets that minted only a handful of
        // proofs.
        val outcome = ops.restoreFromMint(mintUrl = mintUrl, seed = seed, startCounter = 0L)
        // Bump persisted counter past every slot we just confirmed in
        // use. reserveCashuCounters atomically increments + persists,
        // so two restores running concurrently can't collide either.
        val current = settings.peekCashuCounter(outcome.keysetId)
        val delta = (outcome.nextCounterAfterScan - current).coerceAtLeast(0L)
        if (delta > 0) settings.reserveCashuCounters(outcome.keysetId, delta.toInt())
        return outcome
    }

    /**
     * NUT-07 sanity sweep: ask each mint which of our held proofs it
     * still considers unspent, and NIP-09 delete any kind:7375 whose
     * proofs are all marked SPENT.
     *
     * Heals the local wallet after a prior swap (send, nutzap,
     * migration) consumed proofs at the mint but failed to publish
     * the kind:5 locally — the most common cause of HTTP 400
     * "proofs already spent" on the next user-initiated send.
     *
     * Read-only at the mint (no swap, no signing of new tokens),
     * so safe to call on demand. Mixed-state entries (some proofs
     * spent, some unspent in the same kind:7375 — rare, happens only
     * when a partial sub-swap lands) are NIP-09 deleted as well so
     * the next send can't pick them and 400; the unspent portion is
     * lost until a NUT-09 restore re-derives them. Single-mint scope
     * lets [sendNutzap] / [sendAsToken] heal only what they're about
     * to spend, keeping latency to one /v1/checkstate per click.
     *
     * Also immediately removes the stale events from internal
     * indexes via [removeEvents] so the very next read of
     * [_tokenEntries] excludes them — without this, the bundled
     * newEventBundles round-trip (~1s) leaves a window where the
     * caller would still pick the ghosts.
     */
    suspend fun scrubLocallyStaleProofs(mintUrlFilter: String? = null) {
        check(started) { "CashuWalletState.start() not called" }
        val byMint =
            _tokenEntries.value
                .groupBy { it.content.mint }
                .filterKeys { mintUrlFilter == null || it == mintUrlFilter }
        for ((mintUrl, entries) in byMint) {
            val allProofs = entries.flatMap { it.content.proofs }
            if (allProofs.isEmpty()) continue
            val states =
                runCatching { ops.checkProofStates(mintUrl, allProofs) }
                    .onFailure {
                        Log.w("CashuWallet", "checkProofStates($mintUrl) failed; skipping sweep", it)
                    }.getOrNull()
                    ?: continue

            // Any entry with at least one SPENT proof gets purged. Keeping
            // mixed-state entries around would let the next send pick them
            // and trip the same HTTP 400 we're trying to prevent.
            val staleEntries =
                entries.filter { entry ->
                    entry.content.proofs.any { states[it.secret] == ProofState.SPENT }
                }
            if (staleEntries.isEmpty()) continue
            Log.i("CashuWallet") {
                "Scrubbing ${staleEntries.size} stale kind:7375 event(s) at $mintUrl"
            }
            val staleIds = staleEntries.map { it.event.id }.toSet()
            runCatching {
                val template = DeletionEvent.build(staleEntries.map { it.event })
                val signed = signer.sign(template)
                publishEvent(signed)
            }.onFailure {
                Log.w("CashuWallet", "Failed to NIP-09 delete stale entries for $mintUrl", it)
            }
            // Drop from internal indexes regardless of publish success — even
            // if the kind:5 didn't go out, we know these proofs are unusable
            // and shouldn't be selected for the next swap.
            removeEvents(staleIds)
        }
    }

    /**
     * Migrate proofs held on inactive keysets onto each mint's current
     * active keyset. Cheap when nothing needs migrating (one /v1/keys
     * per mint to learn the current active id). When stale proofs exist,
     * consolidates them via a swap and republishes as a single kind:7375,
     * NIP-09-deleting the source events the same way [sendNutzap] does.
     *
     * No longer auto-triggered on wallet load — the swap-then-publish
     * sequence isn't atomic, and a failure mid-sequence corrupts local
     * state (mint spent proofs, our kind:7375 still holds them). The
     * non-destructive [scrubLocallyStaleProofs] runs in its place at
     * startup; this method stays available for explicit user-driven
     * migration (e.g. a future "compact wallet" action).
     */
    suspend fun migrateStaleKeysets() {
        check(started) { "CashuWalletState.start() not called" }
        // Group held tokens by mint URL — each mint has its own keysets.
        val byMint = _tokenEntries.value.groupBy { it.content.mint }
        for ((mintUrl, entries) in byMint) {
            val activeId =
                runCatching { ops.fetchActiveKeysetId(mintUrl) }
                    .onFailure {
                        Log.w("CashuWallet", "fetchActiveKeysetId($mintUrl) failed; skipping migration", it)
                    }.getOrNull()
                    ?: continue
            val staleEntries = entries.filter { entry -> entry.content.proofs.any { it.id != activeId } }
            if (staleEntries.isEmpty()) continue
            runCatching { ops.migrateToActiveKeyset(mintUrl, staleEntries, activeId) }
                .onFailure {
                    Log.w("CashuWallet", "migrateToActiveKeyset($mintUrl) failed", it)
                }
        }
    }

    /**
     * Send a NIP-61 nutzap of [amountSats] to [recipientPubKey] referencing
     * [zappedEvent]. Returns the resulting [NutzapSent] on success or throws
     * — callers should surface errors via [describeMintError].
     */
    suspend fun sendNutzap(
        amountSats: Long,
        recipientPubKey: HexKey,
        zappedEvent: EventHintBundle<out Event>,
        message: String = "",
    ): NutzapSent {
        check(started) { "CashuWalletState.start() not called" }
        val target =
            peekNutzapTarget(recipientPubKey)
                ?: throw IllegalStateException("Recipient does not accept nutzaps from any of our mints")

        // NUT-07 check + immediate state cleanup before selecting.
        // The startup scrub can't catch entries that arrive from relays
        // after start() finished, so a "ghost proof" from a prior
        // partial-failure persists until the user clicks send. Heal
        // first so the selection below works on a known-fresh view.
        scrubLocallyStaleProofs(target.mintUrl)

        val available = _tokenEntries.value.filter { it.content.mint == target.mintUrl }
        if (available.isEmpty()) {
            throw IllegalStateException("No proofs available at ${target.mintUrl}")
        }

        return ops.sendNutzap(
            mintUrl = target.mintUrl,
            amountSats = amountSats,
            recipientPubKey = recipientPubKey,
            recipientP2pkPubkeyHex = target.recipientP2pkPubkeyHex,
            zappedEvent = zappedEvent,
            message = message,
            available = available,
        )
    }

    /**
     * Mint a cashuB token for [amountSats] from proofs held at [mintUrl].
     * Heals stale proofs first (NUT-07 + NIP-09) so the swap doesn't trip
     * "proofs already spent" on a ghost entry. Caller surfaces errors via
     * [describeMintError].
     */
    suspend fun sendAsToken(
        mintUrl: String,
        amountSats: Long,
        memo: String? = null,
    ): SendTokenCompleted {
        check(started) { "CashuWalletState.start() not called" }
        if (amountSats <= 0) throw IllegalArgumentException("Amount must be positive")
        if (mintUrl.isBlank()) throw IllegalArgumentException("Pick a mint")

        scrubLocallyStaleProofs(mintUrl)

        val available = _tokenEntries.value.filter { it.content.mint == mintUrl }
        val balance = available.sumOf { it.content.totalAmount() }
        if (balance < amountSats) {
            throw IllegalStateException("Mint $mintUrl has only $balance sat")
        }
        return ops.sendAsToken(mintUrl, amountSats, available, memo)
    }

    /**
     * Pay a lightning invoice via mint melt. Heals stale proofs first
     * (NUT-07 + NIP-09) so the melt doesn't trip "proofs already spent"
     * on a ghost entry. Caller surfaces errors via [describeMintError].
     */
    suspend fun meltToLightning(
        mintUrl: String,
        quote: MeltQuoteBolt11ResponseDto,
    ): MeltCompleted {
        check(started) { "CashuWalletState.start() not called" }
        if (mintUrl.isBlank()) throw IllegalArgumentException("Pick a mint")

        scrubLocallyStaleProofs(mintUrl)

        val available = _tokenEntries.value.filter { it.content.mint == mintUrl }
        if (available.isEmpty()) {
            throw IllegalStateException("No proofs available at $mintUrl")
        }
        return ops.meltToLightning(mintUrl, quote, available)
    }

    // ============================================================
    // Publish bridge
    // ============================================================

    /**
     * Set exactly once in [start]; before that, every coroutine that could
     * call [publishEvent] is gated behind `started` so the no-op default is
     * never observed by produced events.
     */
    private var publish: suspend (Event) -> Unit = { error("CashuWalletState.start() not called") }

    private suspend fun publishEvent(event: Event) {
        publish(event)
    }

    private companion object {
        /**
         * How long to remain in the "discovering" state before falling back
         * to the empty-wallet CTA. Long enough that a typical relay round-
         * trip on a cold start completes; short enough that genuinely
         * wallet-less users don't stare at a spinner.
         */
        const val DISCOVERY_TIMEOUT_MS = 8_000L
    }
}

/** Mint + recipient pubkey resolved from a kind:10019. */
data class NutzapTarget(
    val mintUrl: String,
    val recipientP2pkPubkeyHex: String,
)
