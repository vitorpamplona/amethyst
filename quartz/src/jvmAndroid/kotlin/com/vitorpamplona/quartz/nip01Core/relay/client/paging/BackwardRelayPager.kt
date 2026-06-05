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
 * **one page at a time, per relay, on demand**, by `until`+`limit` ([RelayLoadingCursors]) — with each
 * relay advancing independently the moment *it* settles, never paced by the slowest one.
 *
 * This is the **single-active orchestrator** around the paging state. The state itself (the per-relay
 * [RelayLoadingCursors] cursors) does NOT live here — it lives on the owning domain object (a `Chatroom`
 * for a conversation, a `ChatroomList` for the account-level feeds), so its lifetime matches the cached
 * messages it describes. One orchestrator drives whichever scope is on screen; calling [bind] repoints
 * it at that scope's cursors. This is safe because history relays only ever arm while their on-screen
 * markers are visible, so a backgrounded scope produces no callbacks to mis-route.
 *
 * What it owns (all transient, recomputed on each [bind]): the in-flight + silence tracking
 * ([PerRelayLoadTracker]), the stalled-relay set, and the display [StateFlow]s ([relayProgress],
 * [exhausted], [reachedBack], [relayCount], [stalledCount]). The persistent cursors and the pinned
 * history floor live on the bound [RelayLoadingCursors].
 *
 * What it does NOT own (the caller supplies these — they are protocol- and framework-specific):
 *  - **Building the actual REQ filters.** The caller reads [armedRelays] + [requestedUntilFor] and
 *    assembles its own `RelayBasedFilter`s (the kinds / authors / `#p` tags differ per feed).
 *  - **The subscription lifecycle.** The caller wires its `INostrClient` subscription and forwards
 *    relay callbacks here via [onEvent] / [onEose] / [onClosed] / [onCannotConnect], then re-issues
 *    its filter (e.g. `invalidateFilters()`) after [advance] / [advanceAll] return true.
 *  - **Which scope's cursors + which relays.** Supplied together by [bind].
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
class BackwardRelayPager(
    // Short label for the DMPagination logs (e.g. "giftwrap.history", "convo.nip04.history").
    private val name: String,
    // Asked of every relay per page; large on purpose (a whole band in one page), and caps per-request
    // volume. A relay returning fewer is its own cap, NOT exhaustion — only an empty page ends a relay.
    val pageLimit: Int = DEFAULT_PAGE_LIMIT,
    // How far below "now" the history floor sits — paging starts here and walks backward. Defaults to
    // the one-week live-tail boundary: everything newer is the always-on tail's job.
    private val liveTailSeconds: Long = DEFAULT_LIVE_TAIL_SECONDS,
) {
    private val loadTracker = PerRelayLoadTracker(name, onSilenced = ::onSilenced)

    // The active scope, set by [bind]: its persistent per-relay cursors (which live on the owning domain
    // object) and the lookup for the relay set it fans out to.
    @Volatile
    private var cursors: RelayLoadingCursors? = null

    @Volatile
    private var relaysFor: () -> Collection<NormalizedRelayUrl>? = { null }

    // Relays not advancing for the active scope (auth CLOSE / unreachable / silent). Transient: cleared
    // and recomputed on each [bind]; a stalled relay is kept (its sub stays open) and retried on advance.
    private val stalledRelays = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()

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

    // The session-pinned floor for the active scope — kept on its cursors so it persists with the scope
    // and does not drift forward on recompute (which would re-trigger an undelivered relay's sentinel).
    private fun floor(): Long {
        val c = cursors ?: return TimeUtils.now() - liveTailSeconds
        return c.floor ?: (TimeUtils.now() - liveTailSeconds).also { c.floor = it }
    }

    /**
     * Repoints to a scope (call on subscribe / when the on-screen scope changes): its persistent
     * [scopeCursors] (held on the owning model object), the [scope] for the silence watchdog, and the
     * [relaysForScope] lookup. Resets the transient orchestration (in-flight, stalled) and recomputes
     * the display flows from the bound cursors — so a previously-paged scope restores its progress
     * instead of restarting.
     */
    fun bind(
        scopeCursors: RelayLoadingCursors,
        scope: CoroutineScope,
        relaysForScope: () -> Collection<NormalizedRelayUrl>?,
    ) {
        cursors = scopeCursors
        relaysFor = relaysForScope
        loadTracker.bind(scope)
        loadTracker.reset()
        stalledRelays.clear()
        updateStatus()
        recomputeExhausted()
    }

    // --- Filter building support: the caller assembles the actual REQ from these. ---

    /** Relays of the active scope that have been advanced (armed) and aren't done — i.e. carry a REQ. */
    fun armedRelays(relays: Collection<NormalizedRelayUrl>): List<NormalizedRelayUrl> = cursors?.armedRelays(relays) ?: emptyList()

    /** The `until` [relay]'s next page should carry (null if it isn't armed / no scope bound). */
    fun requestedUntilFor(relay: NormalizedRelayUrl): Long? = cursors?.requestedUntilFor(relay)

    // --- Demand-driven advance (the caller re-issues its filter when these return true). ---

    /** Steps a single [relay] to its next, older page. @return true if it actually advanced. */
    fun advance(relay: NormalizedRelayUrl): Boolean {
        if (!arm(relay)) return false
        _exhausted.value = false
        updateStatus()
        return true
    }

    /** Steps every not-done, not-in-flight relay of the active scope one page. For a scope too small to scroll. */
    fun advanceAll(): Boolean {
        val relays = relaysFor() ?: return false
        var any = false
        relays.forEach { if (arm(it)) any = true }
        if (any) {
            _exhausted.value = false
            updateStatus()
        }
        return any
    }

    // Moves one relay's cursor to its next page and marks it in-flight. Returns false if it can't advance
    // (no scope bound, unknown relay, already fetching, or already done). Caller batches the recompute.
    private fun arm(relay: NormalizedRelayUrl): Boolean {
        val c = cursors ?: return false
        val relays = relaysFor() ?: return false
        if (relay !in relays) return false
        if (loadTracker.isInFlight(relay)) return false
        if (!c.advance(relay, floor())) return false
        stalledRelays.remove(relay)
        loadTracker.onAdvance(relay)
        return true
    }

    // --- Subscription callbacks: the owner forwards these from its SubscriptionListener. ---

    /** Records one delivered event for [relay] (a sign of life + a page tally entry). */
    fun onEvent(
        relay: NormalizedRelayUrl,
        createdAt: Long,
    ) {
        loadTracker.onActivity()
        cursors?.onEvent(relay, createdAt)
        stalledRelays.remove(relay)
    }

    /** Finalizes [relay]'s page on EOSE. @return true if this EOSE is the one that marked it done. */
    fun onEose(relay: NormalizedRelayUrl): Boolean {
        val c = cursors ?: return false
        stalledRelays.remove(relay)
        c.onEose(relay)
        loadTracker.onSettled(relay)
        val done = c.isDone(relay)
        updateStatus()
        recomputeExhausted()
        return done
    }

    /** [relay] rejected the REQ (e.g. auth-required): settle it and flag it stalled (kept, retryable). */
    fun onClosed(
        relay: NormalizedRelayUrl,
        message: String,
    ) {
        loadTracker.onSettled(relay)
        markStalled(relay, "CLOSED: $message")
        updateStatus()
        recomputeExhausted()
    }

    /** [relay] is unreachable right now: settle it and flag it stalled (kept, retryable). */
    fun onCannotConnect(
        relay: NormalizedRelayUrl,
        message: String,
    ) {
        loadTracker.onSettled(relay)
        markStalled(relay, "cannot connect: $message")
        updateStatus()
        recomputeExhausted()
    }

    // The tracker's silence watchdog fired: the still-pending relays went quiet after their REQ. Flag them
    // stalled but kept, so the window can settle instead of hanging on a dead relay.
    private fun onSilenced(relays: Set<NormalizedRelayUrl>) {
        relays.forEach { markStalled(it, "no response (silence timeout)") }
        updateStatus()
        recomputeExhausted()
    }

    private fun markStalled(
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        if (stalledRelays.add(relay)) Log.d(TAG) { "[$name] ${relay.url} stalled — $reason (kept, advance to retry)" }
    }

    // --- Display-flow recompute (from the bound cursors). ---

    /** Recomputes the display flows from the active scope's cursors. */
    fun updateStatus() {
        val c = cursors
        val relays = relaysFor() ?: emptySet()
        _relayCount.value = loadTracker.count()
        val floor = floor()
        _reachedBack.value = c?.deepestReached(relays, floor)
        _stalledCount.value = relays.count { it in stalledRelays && c?.isDone(it) != true }
        _relayProgress.value =
            relays.associateWith { relay ->
                RelayPagingProgress(
                    reachedUntil = c?.reachedUntilFor(relay, floor) ?: floor,
                    done = c?.isDone(relay) ?: false,
                    stalled = relay in stalledRelays && c?.isDone(relay) != true,
                )
            }
    }

    // Exhausted once every relay is either done (empty page) or stalled (unreachable) — nothing more is
    // reachable right now. A merely parked relay (more to load, just not advancing) keeps this false.
    private fun recomputeExhausted() {
        val c = cursors ?: return
        val relays = relaysFor() ?: return
        if (relays.isEmpty()) return
        val pending = relays.any { !c.isDone(it) && it !in stalledRelays }
        val ex = !pending
        if (ex && !_exhausted.value) {
            val done = relays.filter { c.isDone(it) }.map { it.url }
            val stuck = relays.filter { it in stalledRelays && !c.isDone(it) }.map { it.url }
            Log.d(TAG) { "[$name] window settled (nothing more reachable) — done=$done stalled=$stuck" }
        }
        _exhausted.value = ex
    }

    companion object {
        private const val TAG = "DMPagination"

        const val DEFAULT_PAGE_LIMIT = 10000

        // One week — matches the DM live-tail floor (everything newer is the always-on tail's job).
        const val DEFAULT_LIVE_TAIL_SECONDS = 7L * TimeUtils.ONE_DAY
    }
}
