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
package com.vitorpamplona.quartz.nip01Core.relay.client.paging

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Reusable **per-relay backward pagination** engine: pages a set of relays back through history,
 * **one page at a time, per relay, on demand**, by `until`+`limit` ([UntilLimitPager]) — with each
 * relay advancing independently the moment *it* settles, never paced by the slowest one. This is the
 * generic core extracted from the DM history loaders (gift-wrap, conversation NIP-04, rooms-list
 * NIP-04), which were ~80% identical; any feed that wants demand-driven, gap-proof, per-relay history
 * paging can build one of these instead of re-deriving the cursor/stall/exhausted bookkeeping.
 *
 * What it owns: the per-relay cursors ([UntilLimitPager]), the in-flight + silence tracking
 * ([PerRelayLoadTracker]), the stalled-relay set, the per-key "exhausted" memo, the session-pinned
 * history floor, and the display [StateFlow]s ([relayProgress], [exhausted], [reachedBack],
 * [relayCount], [stalledCount]).
 *
 * What it does NOT own (the caller supplies these — they are protocol- and framework-specific):
 *  - **Building the actual REQ filters.** The caller reads [armedRelays] + [requestedUntilFor] and
 *    assembles its own `RelayBasedFilter`s (the kinds / authors / `#p` tags differ per feed).
 *  - **The subscription lifecycle.** The caller wires its `INostrClient` subscription and forwards
 *    relay callbacks here via [onEvent] / [onEose] / [onClosed] / [onCannotConnect], then re-issues
 *    its filter (e.g. `invalidateFilters()`) after [advance] / [advanceAll] return true.
 *  - **Which relays a key fans out to.** Supplied once as [relaysFor]; the engine reads it whenever it
 *    needs the active key's relay set (status recompute, exhaustion, membership checks).
 *
 * ### Keying ([K])
 * State is partitioned by an opaque key [K] — e.g. an account pubkey, or `(account, conversation)` —
 * so several independently-paged scopes can share one engine without leaking cursors across them, and
 * switching the on-screen scope just repoints the display flows ([activate]) instead of resetting
 * progress. Exactly one key is "active" (its state is mirrored into the display flows) at a time.
 *
 * ### Done vs stalled (read [exhausted] with care)
 * A relay is **done** once it answers an empty page (gap-proof: nothing older). A relay that won't
 * answer right now — auth CLOSE, unreachable, or silent past the tracker's window — is flagged
 * **stalled** but kept (its subscription stays open; re-[advance] retries it). [exhausted] flips true
 * once every relay is *done or stalled* — "nothing more reachable right now", which is NOT the same as
 * "fully caught up". Callers that render a terminal state should split on [stalledCount]: `exhausted &&
 * stalledCount == 0` is genuinely caught up; `exhausted && stalledCount > 0` stopped early and may be
 * missing messages.
 *
 * Not internally synchronized beyond the primitives it composes; intended to be driven from one owning
 * scope with relay callbacks serialized per relay (as the relay IO layer delivers them).
 */
class BackwardRelayPager<K>(
    // Short label for the DMPagination logs (e.g. "giftwrap.history", "convo.nip04.history").
    private val name: String,
    // Asked of every relay per page; large on purpose (a whole band in one page), and caps per-request
    // volume. A relay returning fewer is its own cap, NOT exhaustion — only an empty page ends a relay.
    val pageLimit: Int = DEFAULT_PAGE_LIMIT,
    // How far below "now" the history floor sits — paging starts here and walks backward. Defaults to
    // the one-week live-tail boundary: everything newer is the always-on tail's job.
    private val liveTailSeconds: Long = DEFAULT_LIVE_TAIL_SECONDS,
    // The relay set a key currently fans out to. Read on every status/exhaustion recompute, so it must
    // reflect the key's live relay list. Null/empty means "no relays known yet" (no-op).
    private val relaysFor: (K) -> Collection<NormalizedRelayUrl>?,
) {
    private val pager = UntilLimitPager<K>()
    private val loadTracker = PerRelayLoadTracker(name, onSilenced = ::onSilenced)

    // Relays not currently advancing for a key (auth CLOSE / unreachable / silent). Kept (not given up)
    // and surfaced as stalled; they resume if the key re-advances them.
    private val stalledRelays = ConcurrentHashMap<K, MutableSet<NormalizedRelayUrl>>()

    // Per-key exhausted memo, so a backgrounded key keeps its terminal state and switching back to it
    // restores the right flag instead of flashing "loading".
    private val exhaustedByKey = ConcurrentHashMap<K, Boolean>()

    // History starts just below the live-tail floor and pages backward. Pinned per key for the session:
    // it must NOT drift forward on every recompute, or an un-delivered relay's marker (which sits at this
    // floor) would keep changing and re-trigger its on-screen sentinel.
    private val pinnedFloor = ConcurrentHashMap<K, Long>()

    // The key whose state is currently mirrored into the display flows (the one on screen). A background
    // key's late EOSE still advances its cursors in [pager] but must not overwrite the display flows.
    @Volatile
    private var activeKey: K? = null

    /** True while any relay is mid-page. Starts false (an idle engine isn't "loading"). */
    val loadingMore: StateFlow<Boolean> = loadTracker.loading

    private val _exhausted = MutableStateFlow(false)

    /** Nothing more reachable right now: every relay is done or stalled. See class doc — not "caught up". */
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    private val _relayCount = MutableStateFlow(0)

    /** Relays currently fetching a page (for an "asking N relays" status line). */
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    private val _stalledCount = MutableStateFlow(0)

    /** Not-done relays that can't be reached right now (auth CLOSE / unreachable / silent). */
    val stalledCount: StateFlow<Int> = _stalledCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)

    /** Oldest `createdAt` reached across all relays (the deepest cursor), or null before any delivery. */
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    private val _relayProgress = MutableStateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>>(emptyMap())

    /** Per-relay window position (reached / done / stalled) — the data on-screen reach markers render. */
    val relayProgress: StateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>> = _relayProgress.asStateFlow()

    /** The session-pinned floor for [key] — where its paging starts (just below the live tail). */
    internal fun floorFor(key: K): Long = pinnedFloor.getOrPut(key) { TimeUtils.now() - liveTailSeconds }

    // --- Filter building support: the caller assembles the actual REQ from these. ---

    /** Relays of [key] that have been advanced (armed) and aren't done — i.e. that should carry a REQ. */
    fun armedRelays(
        key: K,
        relays: Collection<NormalizedRelayUrl>,
    ): List<NormalizedRelayUrl> = pager.armedRelays(key, relays)

    /** The `until` [relay]'s next page should carry for [key] (null if it isn't armed). */
    fun requestedUntilFor(
        key: K,
        relay: NormalizedRelayUrl,
    ): Long? = pager.requestedUntilFor(key, relay)

    // --- Demand-driven advance (the caller re-issues its filter when these return true). ---

    /** Steps a single [relay] to its next, older page for [key]. @return true if it actually advanced. */
    fun advance(
        key: K,
        relay: NormalizedRelayUrl,
        scope: CoroutineScope,
    ): Boolean {
        if (!arm(key, relay, scope)) return false
        if (activeKey == key) _exhausted.value = false
        updateStatus(key)
        return true
    }

    /** Steps every not-done, not-in-flight relay of [key] one page. For a scope too small to scroll. */
    fun advanceAll(
        key: K,
        scope: CoroutineScope,
    ): Boolean {
        val relays = relaysFor(key) ?: return false
        var any = false
        relays.forEach { if (arm(key, it, scope)) any = true }
        if (any) {
            if (activeKey == key) _exhausted.value = false
            updateStatus(key)
        }
        return any
    }

    // Moves one relay's cursor to its next page and marks it in-flight. Returns false if it can't advance
    // (unknown relay, already fetching, or already done). Does NOT recompute status — the caller batches.
    private fun arm(
        key: K,
        relay: NormalizedRelayUrl,
        scope: CoroutineScope,
    ): Boolean {
        val relays = relaysFor(key) ?: return false
        if (relay !in relays) return false
        if (loadTracker.isInFlight(relay)) return false
        if (!pager.advance(key, relay, floorFor(key))) return false
        stalledRelays[key]?.remove(relay)
        loadTracker.bind(scope)
        loadTracker.onAdvance(relay)
        return true
    }

    // --- Subscription callbacks: the owner forwards these from its SubscriptionListener. ---

    /** Records one delivered event for [relay] (a sign of life + a page tally entry). */
    fun onEvent(
        key: K,
        relay: NormalizedRelayUrl,
        createdAt: Long,
    ) {
        loadTracker.onActivity()
        pager.onEvent(key, relay, createdAt)
        stalledRelays[key]?.remove(relay)
    }

    /** Finalizes [relay]'s page on EOSE. @return true if this EOSE is the one that marked it done. */
    fun onEose(
        key: K,
        relay: NormalizedRelayUrl,
    ): Boolean {
        stalledRelays[key]?.remove(relay)
        pager.onEose(key, relay)
        loadTracker.onSettled(relay)
        val done = pager.isDone(key, relay)
        updateStatus(key)
        recomputeExhausted(key)
        return done
    }

    /** [relay] rejected the REQ (e.g. auth-required): settle it and flag it stalled (kept, retryable). */
    fun onClosed(
        key: K,
        relay: NormalizedRelayUrl,
        message: String,
    ) {
        loadTracker.onSettled(relay)
        markStalled(key, relay, "CLOSED: $message")
        updateStatus(key)
        recomputeExhausted(key)
    }

    /** [relay] is unreachable right now: settle it and flag it stalled (kept, retryable). */
    fun onCannotConnect(
        key: K,
        relay: NormalizedRelayUrl,
        message: String,
    ) {
        loadTracker.onSettled(relay)
        markStalled(key, relay, "cannot connect: $message")
        updateStatus(key)
        recomputeExhausted(key)
    }

    // The tracker's silence watchdog fired: the still-pending relays went quiet after their REQ. Flag them
    // (for the active key) stalled but kept, so the window can settle instead of hanging on a dead relay.
    private fun onSilenced(relays: Set<NormalizedRelayUrl>) {
        val key = activeKey ?: return
        relays.forEach { markStalled(key, it, "no response (silence timeout)") }
        updateStatus(key)
        recomputeExhausted(key)
    }

    private fun markStalled(
        key: K,
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        val firstTime = stalledRelays.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(relay)
        if (firstTime) Log.d(TAG) { "[$name] ${relay.url} stalled — $reason (kept, advance to retry)" }
    }

    // --- Display-flow management. ---

    /**
     * Repoints the display flows to [key] (call on subscribe / when the on-screen scope changes), then
     * refreshes them. A no-op repoint (same key) just refreshes. Cursors in [pager] are untouched, so a
     * previously-paged key restores its progress instead of restarting.
     */
    fun activate(key: K) {
        if (activeKey != key) {
            activeKey = key
            loadTracker.reset()
            _exhausted.value = exhaustedByKey[key] ?: false
            _relayCount.value = 0
            _stalledCount.value = 0
            _reachedBack.value = null
            _relayProgress.value = emptyMap()
        }
        updateStatus(key)
    }

    /** Recomputes the display flows from [key]'s cursors. No-op when [key] is not the active key. */
    fun updateStatus(key: K) {
        if (activeKey != key) return
        val relays = relaysFor(key) ?: emptySet()
        _relayCount.value = loadTracker.count()
        val floor = floorFor(key)
        _reachedBack.value = pager.deepestReached(key, relays, floor)
        val stalled = stalledRelays[key] ?: emptySet()
        _stalledCount.value = relays.count { it in stalled && !pager.isDone(key, it) }
        _relayProgress.value =
            relays.associateWith { relay ->
                RelayPagingProgress(
                    reachedUntil = pager.reachedUntilFor(key, relay, floor),
                    done = pager.isDone(key, relay),
                    stalled = relay in stalled && !pager.isDone(key, relay),
                )
            }
    }

    // Exhausted once every relay is either done (empty page) or stalled (unreachable) — nothing more is
    // reachable right now. A merely parked relay (more to load, just not advancing) keeps this false.
    private fun recomputeExhausted(key: K) {
        val relays = relaysFor(key) ?: return
        if (relays.isEmpty()) return
        val stalled = stalledRelays[key] ?: emptySet()
        val pending = relays.any { !pager.isDone(key, it) && it !in stalled }
        val ex = !pending
        val was = exhaustedByKey[key] ?: false
        exhaustedByKey[key] = ex
        if (ex && !was) {
            val done = relays.filter { pager.isDone(key, it) }.map { it.url }
            val stuck = relays.filter { it in stalled && !pager.isDone(key, it) }.map { it.url }
            Log.d(TAG) { "[$name] window settled (nothing more reachable) — done=$done stalled=$stuck" }
        }
        if (activeKey == key) _exhausted.value = ex
    }

    companion object {
        private const val TAG = "DMPagination"

        const val DEFAULT_PAGE_LIMIT = 10000

        // One week — matches the DM live-tail floor (everything newer is the always-on tail's job).
        const val DEFAULT_LIVE_TAIL_SECONDS = 7L * TimeUtils.ONE_DAY
    }
}
