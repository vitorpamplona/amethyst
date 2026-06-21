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

import com.vitorpamplona.amethyst.commons.cashu.CashuWalletReader
import com.vitorpamplona.amethyst.commons.cashu.ops.CashuWalletOps
import com.vitorpamplona.amethyst.commons.cashu.ops.MeltCompleted
import com.vitorpamplona.amethyst.commons.cashu.ops.NutzapSent
import com.vitorpamplona.amethyst.commons.cashu.ops.RestoreOutcome
import com.vitorpamplona.amethyst.commons.cashu.ops.SendTokenCompleted
import com.vitorpamplona.amethyst.commons.cashu.ops.TokenEntry
import com.vitorpamplona.amethyst.commons.cashu.ops.describeMintError
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
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
            // Recovery hooks: when a mint replies "outputs already
            // signed" on a quote we already paid (prior attempt
            // crashed after the mint signed our outputs but before
            // we published the kind:7375), completeMintFromLightning
            // re-derives those exact outputs from the seed and asks
            // the mint to redeliver via NUT-09 /v1/restore.
            seedForRestore = { ensureSeed() },
            peekCashuCounter = { keysetId -> settings.peekCashuCounter(keysetId) },
            reserveCashuCounters = { keysetId, count -> settings.reserveCashuCounters(keysetId, count) },
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

    /**
     * Spendable cashu balance per mint URL — the reactive twin of
     * [peekMintBalances], for UI that needs to re-render as proofs arrive
     * (the wallet screen's mint list). Derived from [_tokenEntries] exactly
     * like [balanceSats]. May overstate by NUT-07 stale proofs; the actual
     * spend scrubs before melting.
     */
    val mintBalances: StateFlow<Map<String, Long>> =
        _tokenEntries
            .map { entries ->
                entries
                    .groupBy { it.content.mint }
                    .mapValues { (_, byMint) -> byMint.sumOf { it.content.totalAmount() } }
            }.flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Mints to surface in the wallet screen's per-mint list: the union of
     * our configured mints (kind:17375 — listed even at zero balance so the
     * user can top them up) and every mint we actually hold tokens at
     * (token-derived, via [mintBalances]). The token-derived half is what
     * keeps the per-mint rows summing to [balanceSats]: a balance
     * auto-redeemed from a mint we never configured (e.g. a nutzap on a mint
     * not in our kind:10019) contributes to the total, so without a row for
     * it the displayed mint balances would silently under-count the wallet.
     * Configured mints come first; extra token-only mints follow.
     */
    val displayMints: StateFlow<List<String>> =
        combine(_mints, mintBalances) { configured, balances ->
            (configured + balances.keys).distinct()
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Mints we hold a spendable balance at but never configured in our
     * kind:17375 wallet — keyed by mint URL → balance in sats. Almost
     * always coins auto-redeemed from a NIP-61 nutzap that was sent on a
     * mint outside our kind:10019. Surfaced so the wallet can highlight
     * them and nudge the user to move the funds to a mint they trust (or
     * out to Lightning): holding ecash at an unvetted mint means trusting
     * an issuer the user never chose. Empty in the common case where every
     * mint we hold is also configured.
     */
    val unconfiguredMintBalances: StateFlow<Map<String, Long>> =
        combine(_mints, mintBalances) { configured, balances ->
            balances.filterKeys { it !in configured }.filterValues { it > 0 }
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

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
            // The NUT-13 seed is derived from the wallet's P2PK key. A new
            // kind:17375 may carry a rotated key (our own recreateNutzapKey, or
            // a rotation from another client), so drop the cached seed and let
            // ensureSeed re-derive from whatever key the live event now holds.
            cachedSeed = null
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
            // The cached kind:17375 mirrors the live event; once it's deleted
            // (our own teardown or an external NIP-09) drop the backup too, or
            // the next launch would re-consume it from settings and resurrect
            // the wallet.
            settings.clearCashuWallet()
        }
        if (dirtyNutzapInfo) {
            _nutzapInfoEvent.value = null
            settings.clearNutzapInfo()
        }
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

        // Shared del-rollover + sort with the headless reader.
        _tokenEntries.value = CashuWalletReader.computeUnspent(all, tokenContents)
    }

    private fun recomputePending() {
        // Shared destroyed/expired filter with the headless reader.
        _pendingQuotes.value = CashuWalletReader.computePending(quoteEvents.values, historyEvents.values)
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
    // Stop receiving nutzaps / delete wallet
    // ============================================================

    /**
     * Stop receiving NIP-61 nutzaps: replace kind:10019 with an empty version
     * and NIP-09 delete it (see [CashuWalletOps.stopNutzaps]). Leaves the
     * kind:17375 wallet and held proofs intact — the wallet keeps sending.
     *
     * Clears the local index + on-disk backup immediately so the change is
     * effective without waiting for the kind:5 round-trip; the deletion bundle
     * arriving later via [removeEvents] is then a no-op.
     */
    suspend fun stopNutzaps() {
        check(started) { NOT_STARTED_MESSAGE }
        ops.stopNutzaps()
        nutzapInfoEventInternal = null
        _nutzapInfoEvent.value = null
        settings.clearNutzapInfo()
    }

    /**
     * Delete the whole Cashu wallet: withdraws the nutzap advertisement and
     * NIP-09 deletes the kind:17375 (see [CashuWalletOps.deleteWallet]). Held
     * kind:7375 proofs are NOT deleted — that ecash still exists at the mint —
     * but with the P2PK key gone any unredeemed inbound nutzaps and any
     * remaining balance may become unrecoverable. The UI must warn first.
     *
     * No-op when no wallet is loaded. Clears local indexes + backups inline so
     * the wallet screen drops straight to its empty/create state.
     */
    suspend fun deleteWallet() {
        check(started) { NOT_STARTED_MESSAGE }
        val wallet = walletEventInternal ?: return
        ops.deleteWallet(wallet)

        walletEventInternal = null
        _walletEvent.value = null
        _mints.value = emptyList()
        nutzapInfoEventInternal = null
        _nutzapInfoEvent.value = null
        settings.clearCashuWallet()
        settings.clearNutzapInfo()
    }

    /**
     * Rotate the wallet's NIP-61 P2PK key: re-publish kind:17375 + kind:10019
     * with a fresh (or supplied) key, keeping the current mint list. After
     * this, senders lock nutzaps to the NEW key — any inbound nutzap still
     * locked to the OLD key that hasn't been redeemed yet becomes
     * unrecoverable. Rarely needed (key exposure, or restoring a specific key
     * from a backup), which is why it lives behind the settings Danger Zone.
     *
     * [manualPrivkeyHex] null/blank → generate a fresh random key; otherwise
     * adopt the supplied hex key (e.g. importing a backup).
     */
    suspend fun recreateNutzapKey(manualPrivkeyHex: String? = null) {
        check(started) { NOT_STARTED_MESSAGE }
        val currentMints = _mints.value
        require(currentMints.isNotEmpty()) { "Wallet has no mints to re-publish" }
        ops.publishWalletEvents(
            mints = currentMints,
            p2pkPrivkeyHex = manualPrivkeyHex?.takeIf { it.isNotBlank() },
            nutzapRelays = outboxRelaysFlow.value.toList(),
        )
        // The NUT-13 seed (derived from the P2PK key) is invalidated by
        // applyEvents when the new kind:17375 round-trips in, so it re-derives
        // from the rotated key. No need to reset it here.
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
    fun peekNutzapTarget(recipientPubKey: HexKey): NutzapTarget? = peekNutzapFunding(recipientPubKey)?.target

    /** True if [mintUrl] is a mint we hold AND the recipient accepts nutzaps on. */
    fun sharedNutzapMint(
        recipientPubKey: HexKey,
        mintUrl: String,
    ): Boolean {
        if (mintUrl !in _mints.value) return false
        val info = cache.getOrCreateUser(recipientPubKey).nutzapInfo() ?: return false
        return info.mints().any { it.mintUrl == mintUrl }
    }

    /**
     * Resolve nutzap funding for [recipientPubKey]: the best shared mint to
     * spend from plus the balance figures the zap picker needs to classify
     * each amount as funded, reloadable, or out of reach.
     *
     * The chosen [NutzapFunding.target] is the shared mint where we hold the
     * **most** balance — not merely the first one the recipient lists. The
     * previous `firstOrNull` could pick an empty shared mint and make a zap
     * fail with "No proofs available at <mint>" even though another shared
     * mint was funded.
     *
     * All reads are synchronous from in-memory state (`_mints`,
     * `_tokenEntries`, `LocalCache`), so this is safe to call from a
     * composable `remember {}`. The User pins the recipient's kind:10019
     * addressable note for its own lifetime, so the chip no longer races the
     * notes.LargeSoftCache eviction.
     *
     * Returns null when a nutzap is structurally impossible: we have no mints,
     * the recipient has no kind:10019, no P2PK pubkey, or shares no mint with
     * us. A non-null result still does not guarantee a single mint can cover a
     * given amount — compare against [NutzapFunding.bestSingleMintSats].
     */
    fun peekNutzapFunding(recipientPubKey: HexKey): NutzapFunding? {
        val ourMints = _mints.value.toSet()
        if (ourMints.isEmpty()) return null

        val info = cache.getOrCreateUser(recipientPubKey).nutzapInfo() ?: return null

        val recipientPubkeyHex = info.p2pkPubkey() ?: return null
        val sharedMints = info.mints().map { it.mintUrl }.filter { it in ourMints }
        if (sharedMints.isEmpty()) return null

        val entries = _tokenEntries.value
        var bestMint = sharedMints.first()
        var bestMintSats = 0L
        for (mint in sharedMints) {
            val balance = entries.filter { it.content.mint == mint }.sumOf { it.content.totalAmount() }
            if (balance > bestMintSats) {
                bestMintSats = balance
                bestMint = mint
            }
        }

        return NutzapFunding(
            target = NutzapTarget(mintUrl = bestMint, recipientP2pkPubkeyHex = recipientPubkeyHex),
            bestSingleMintSats = bestMintSats,
            totalWalletSats = entries.sumOf { it.content.totalAmount() },
        )
    }

    /**
     * Spendable cashu balance per mint URL, summed from in-memory proofs.
     * Synchronous and local — feeds the Reload Mint screen's balances list and
     * source-feasibility checks. May overstate by NUT-07 stale proofs; the
     * actual move scrubs before melting.
     */
    fun peekMintBalances(): Map<String, Long> =
        _tokenEntries.value
            .groupBy { it.content.mint }
            .mapValues { (_, entries) -> entries.sumOf { it.content.totalAmount() } }

    /**
     * Mints the recipient accepts (their kind:10019) that we also hold a wallet
     * at — the candidate destinations for a reload. Empty when the recipient
     * has no kind:10019 or shares no mint with us.
     */
    fun recipientSharedMints(recipientPubKey: HexKey): List<String> {
        val ourMints = _mints.value.toSet()
        if (ourMints.isEmpty()) return emptyList()
        val info = cache.getOrCreateUser(recipientPubKey).nutzapInfo() ?: return emptyList()
        return info.mints().map { it.mintUrl }.filter { it in ourMints }
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
        check(started) { NOT_STARTED_MESSAGE }
        val seed = ensureSeed() ?: return null
        // Heal any prior-Resync duplicates BEFORE running the new
        // restore. Without this the existingSecrets set below would
        // be inflated by the duplicates and the dedup logic would
        // still be correct, but the user's balance would stay
        // double-counted until the cleanup eventually fires somewhere
        // else. Running it here means every Resync click is also a
        // best-effort heal of past Resync mistakes.
        cleanupDuplicateProofs()

        // Collect every NUT-00 `secret` already present in our local
        // kind:7375 events. The restore path re-derives the same
        // secrets deterministically from the seed; without this set
        // every Resync click would publish a fresh kind:7375 with
        // the same proofs (the balance computation sums proofs across
        // every kind:7375, so duplicates inflate the displayed
        // balance even though the mint won't honor them twice).
        val existingSecrets =
            _tokenEntries.value
                .flatMap { it.content.proofs }
                .mapTo(HashSet()) { it.secret }

        // Always scan from counter 0 — a fresh-device recovery doesn't
        // know which slots were used. The internal gap-limit heuristic
        // in CashuMintOperations.restore (3 consecutive empty batches)
        // bounds the work for wallets that minted only a handful of
        // proofs.
        val outcome =
            ops.restoreFromMint(
                mintUrl = mintUrl,
                seed = seed,
                startCounter = 0L,
                existingSecrets = existingSecrets,
            )
        // Bump persisted counter past every slot we just confirmed in
        // use. reserveCashuCounters atomically increments + persists,
        // so two restores running concurrently can't collide either.
        val current = settings.peekCashuCounter(outcome.keysetId)
        val delta = (outcome.nextCounterAfterScan - current).coerceAtLeast(0L)
        if (delta > 0) settings.reserveCashuCounters(outcome.keysetId, delta.toInt())
        return outcome
    }

    /**
     * Scan held kind:7375 events for accidental duplicates and NIP-09
     * delete the redundant ones. An event B is redundant when there
     * exists another event A such that A's NUT-00 secret set is a
     * (non-strict) superset of B's AND either A has strictly more
     * secrets than B, or A was created earlier than B with the same
     * secret set. The proofs in B aren't lost — they're still held
     * inside A — but the local balance computation, which sums
     * proofs across every kind:7375, no longer double-counts them.
     *
     * Triggered automatically before every Resync to heal damage
     * from previous Resync clicks that ran without the
     * `existingSecrets` dedup in [restoreFromMint]. Safe to call any
     * other time too — no mint round-trip, just NIP-09 publish + a
     * local [removeEvents] so the very next [_tokenEntries] read
     * excludes the ghosts.
     */
    suspend fun cleanupDuplicateProofs() {
        check(started) { NOT_STARTED_MESSAGE }
        val entries = _tokenEntries.value
        if (entries.size < 2) return

        // Pre-compute each entry's secret set once.
        val withSecrets =
            entries.map { entry ->
                entry to entry.content.proofs.mapTo(HashSet()) { it.secret }
            }

        val redundant = mutableListOf<TokenEntry>()
        for ((entry, secrets) in withSecrets) {
            if (secrets.isEmpty()) continue
            val isRedundant =
                withSecrets.any { (other, otherSecrets) ->
                    other.event.id != entry.event.id &&
                        otherSecrets.containsAll(secrets) &&
                        (
                            otherSecrets.size > secrets.size ||
                                (
                                    otherSecrets.size == secrets.size &&
                                        (
                                            other.event.createdAt < entry.event.createdAt ||
                                                (
                                                    other.event.createdAt == entry.event.createdAt &&
                                                        other.event.id < entry.event.id
                                                )
                                        )
                                )
                        )
                }
            if (isRedundant) redundant += entry
        }

        if (redundant.isEmpty()) return
        Log.i("CashuWallet") {
            "Cleanup: NIP-09 deleting ${redundant.size} duplicate kind:7375 event(s)"
        }
        val redundantIds = redundant.map { it.event.id }.toSet()
        runCatching {
            val template = DeletionEvent.build(redundant.map { it.event })
            val signed = signer.sign(template)
            publishEvent(signed)
        }.onFailure {
            Log.w("CashuWallet", "Failed to NIP-09 delete duplicate kind:7375 entries", it)
        }
        // Drop from internal indexes regardless of publish success — the
        // proofs they reference are still held in the surviving entry,
        // so the balance immediately reflects the de-duplicated state.
        removeEvents(redundantIds)
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
        check(started) { NOT_STARTED_MESSAGE }
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
     * Proactively reconcile *every* mint we currently hold tokens at against
     * its NUT-07 `/checkstate` — not just the one mint a spend happens to
     * target. [scrubLocallyStaleProofs] with a null filter already iterates
     * the token-derived mint set ([mintBalances]), so proofs auto-redeemed
     * from a mint we never configured (e.g. a nutzap on a mint not in our
     * kind:10019) get their spent state checked here too, instead of sitting
     * unverified until the user happens to spend from that mint.
     *
     * Non-destructive (it only prunes proofs the mint reports SPENT) and
     * idempotent — safe to call on every wallet-screen open. Deliberately
     * does NOT run [migrateStaleKeysets]; that swap-then-publish sequence
     * isn't atomic and stays user-driven.
     */
    suspend fun syncAllMints() {
        if (!started) return
        scrubLocallyStaleProofs()
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
        check(started) { NOT_STARTED_MESSAGE }
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
     * [zappedEvent] (null for a profile nutzap that targets the person, not an
     * event). Returns the resulting [NutzapSent] on success or throws
     * — callers should surface errors via [describeMintError].
     */
    suspend fun sendNutzap(
        amountSats: Long,
        recipientPubKey: HexKey,
        zappedEvent: EventHintBundle<out Event>?,
        message: String = "",
        preferredMintUrl: String? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): NutzapSent {
        check(started) { NOT_STARTED_MESSAGE }
        val resolved =
            peekNutzapTarget(recipientPubKey)
                ?: throw IllegalStateException("Recipient does not accept nutzaps from any of our mints")
        // Honor an explicit mint when the caller has one in mind (e.g. the Top-up
        // screen just funded a specific mint and must spend from THAT one, not
        // whichever shared mint happens to hold the most). Only if it's still a
        // valid shared target; otherwise fall back to the best-balance pick.
        val target =
            preferredMintUrl
                ?.takeIf { it == resolved.mintUrl || sharedNutzapMint(recipientPubKey, it) }
                ?.let { NutzapTarget(mintUrl = it, recipientP2pkPubkeyHex = resolved.recipientP2pkPubkeyHex) }
                ?: resolved

        // NUT-07 check + immediate state cleanup before selecting.
        // The startup scrub can't catch entries that arrive from relays
        // after start() finished, so a "ghost proof" from a prior
        // partial-failure persists until the user clicks send. Heal
        // first so the selection below works on a known-fresh view.
        scrubLocallyStaleProofs(target.mintUrl)
        // First on-network step done. The caller already showed an
        // instant 0.05 when the chip was tapped; lift to 0.20 here so
        // the bar visibly moves even before the (slow) swap call.
        onProgress?.invoke(0.20f)

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
            onProgress = onProgress,
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
        check(started) { NOT_STARTED_MESSAGE }
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
        skipScrub: Boolean = false,
    ): MeltCompleted {
        check(started) { NOT_STARTED_MESSAGE }
        if (mintUrl.isBlank()) throw IllegalArgumentException("Pick a mint")

        // [rebalance] already scrubbed this mint to compute its coverage check, so
        // it passes skipScrub=true to avoid a second NUT-07 /checkstate round-trip.
        if (!skipScrub) scrubLocallyStaleProofs(mintUrl)

        val available = _tokenEntries.value.filter { it.content.mint == mintUrl }
        if (available.isEmpty()) {
            throw IllegalStateException("No proofs available at $mintUrl")
        }
        return ops.meltToLightning(mintUrl, quote, available)
    }

    /**
     * Move [sats] of cashu from [sourceMintUrl] into [targetMintUrl] by minting
     * a fresh quote at the target and paying its invoice with a melt at the
     * source — no new sats enter the wallet. Used by the Reload Mint screen to
     * top up a recipient's mint from another mint the user already holds.
     *
     * The melt settles the Lightning payment synchronously, so the target quote
     * should read PAID within moments; we poll briefly to absorb mint-side lag
     * before issuing the proofs.
     *
     * Money is never lost on failure: a failed melt returns change to the
     * source, and a paid-but-not-yet-issued target leaves a resumable kind:7374
     * the pending-quote banner can finish later. Throws with a user-facing
     * message on any failure; callers surface it via [describeMintError].
     */
    suspend fun rebalance(
        sourceMintUrl: String,
        targetMintUrl: String,
        sats: Long,
        onProgress: ((Float) -> Unit)? = null,
        onFundsMoved: () -> Unit = {},
    ): RebalanceCompleted {
        check(started) { NOT_STARTED_MESSAGE }
        require(sats > 0) { "Amount must be positive" }
        require(sourceMintUrl != targetMintUrl) { "Source and target mints must differ" }

        // 1. Mint quote at the destination — publishes a resumable kind:7374.
        onProgress?.invoke(0.1f)
        val mintFlow = ops.startMintFromLightning(targetMintUrl, sats)

        // 2. Quote the melt at the source and make sure it covers amount + fee
        //    BEFORE spending. Abandon the unpaid mint quote otherwise so it
        //    doesn't linger in the pending banner.
        onProgress?.invoke(0.25f)
        val meltQuote = ops.requestMeltQuote(sourceMintUrl, mintFlow.invoice)
        scrubLocallyStaleProofs(sourceMintUrl)
        val sourceBalance =
            _tokenEntries.value
                .filter { it.content.mint == sourceMintUrl }
                .sumOf { it.content.totalAmount() }
        val required = meltQuote.amount + meltQuote.feeReserve
        if (sourceBalance < required) {
            runCatching { ops.cancelMintQuote(mintFlow.quoteEvent) }
            throw IllegalStateException("$sourceMintUrl has $sourceBalance sat — needs $required to move $sats")
        }

        // 3. Pay the destination invoice by melting at the source. We already
        //    scrubbed sourceMintUrl above for the coverage check, so skip the
        //    redundant second scrub inside meltToLightning.
        onProgress?.invoke(0.5f)
        meltToLightning(sourceMintUrl, meltQuote, skipScrub = true)
        // Funds have now LEFT the source. Signal the caller immediately so a
        // failure in the steps below (slow confirmation, completeMint error)
        // never causes a retry to melt a second time — the destination quote is
        // paid and resumable via the pending-quote banner instead.
        onFundsMoved()

        // 4. The melt paid the invoice; confirm + issue proofs at the target.
        //    The melt settled synchronously, but a healthy mint can still lag a
        //    while before flipping the quote to PAID, so give it a generous
        //    ~60s steady budget rather than a tight escalating one — a merely
        //    slow mint shouldn't strand funds that have already left the source.
        onProgress?.invoke(0.75f)
        val pollAttempts = 30
        val pollDelayMs = 2_000L
        var paid = false
        var attempt = 0
        while (!paid && attempt < pollAttempts) {
            paid = ops.checkMintQuote(targetMintUrl, mintFlow.mintQuote.quote).isSettled()
            if (!paid) {
                delay(pollDelayMs)
                attempt++
            }
        }
        if (!paid) {
            // Funds already left the source; the destination invoice is paid or
            // will be. Leave the kind:7374 so the pending banner can finish it.
            throw IllegalStateException("Paid $targetMintUrl but it hasn't confirmed yet — finish from the pending quote banner")
        }
        ops.completeMintFromLightning(targetMintUrl, mintFlow.quoteEvent, sats)
        onProgress?.invoke(1.0f)

        return RebalanceCompleted(
            sourceMintUrl = sourceMintUrl,
            targetMintUrl = targetMintUrl,
            movedSats = sats,
            feeSats = meltQuote.feeReserve,
        )
    }

    // ============================================================
    // Publish bridge
    // ============================================================

    /**
     * Set exactly once in [start]; before that, every coroutine that could
     * call [publishEvent] is gated behind `started` so the no-op default is
     * never observed by produced events.
     */
    private var publish: suspend (Event) -> Unit = { error(NOT_STARTED_MESSAGE) }

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

        private const val NOT_STARTED_MESSAGE = "CashuWalletState.start() not called"
    }
}

/** Mint + recipient pubkey resolved from a kind:10019. */
data class NutzapTarget(
    val mintUrl: String,
    val recipientP2pkPubkeyHex: String,
)

/**
 * Nutzap funding snapshot for a recipient — the [target] mint to spend from
 * plus the balance figures the zap picker uses to classify a given amount:
 *  - `amount <= bestSingleMintSats` → fundable instantly from one shared mint
 *  - `amount <= totalWalletSats`    → fundable only after a mint reload/rebalance
 *  - otherwise                      → out of reach with the current balance
 */
data class NutzapFunding(
    val target: NutzapTarget,
    val bestSingleMintSats: Long,
    val totalWalletSats: Long,
)

/** Result of a [CashuWalletState.rebalance] mint-to-mint move. */
data class RebalanceCompleted(
    val sourceMintUrl: String,
    val targetMintUrl: String,
    val movedSats: Long,
    val feeSats: Long,
)
