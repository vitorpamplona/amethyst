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
package com.vitorpamplona.amethyst.commons.cordn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * The three things a sync loop needs from a group manager.
 *
 * [CordnGroupManager] implements it and is the only production implementation;
 * the interface exists so the loop can be tested for what it actually does.
 * Everything interesting about it — not spinning on an empty account, not
 * dying on a coordinator outage, resetting its backoff, re-subscribing when a
 * group is joined — is a reaction to a coordinator that is slow, failing or
 * changing underfoot. Driving those through a real transport would mean
 * provoking a network failure on a schedule, and the timing test that resulted
 * would be the flakiest thing in the suite.
 */
interface CordnSyncSource {
    /** The groups to sync. The loop re-subscribes when this changes. */
    val gids: StateFlow<Set<String>>

    suspend fun catchUp(onDelivery: (CordnGroupManager.Delivery) -> Unit): Int

    /** Suspends until the coordinator closes the stream. */
    suspend fun subscribe(
        timeoutMs: Long,
        onDelivery: (CordnGroupManager.Delivery) -> Unit,
    )
}

/**
 * Keeps one coordinator's groups up to date for as long as it runs.
 *
 * The shape is `spec/02.md`'s: drain history with `catch_up`, then hold a live
 * `subscribe`, and when that stream closes, do it again. The cursor makes the
 * seam safe — a subscription resumes from where the catch-up stopped, so the
 * two phases cannot leave a hole between them.
 *
 * ## What this class is actually for
 *
 * `catchUp` and `subscribe` are one call each; wrapping them would be pointless
 * if the happy path were the whole story. It is not. Three things make an
 * unattended loop different from a call:
 *
 * - **A failure must not end the loop.** A coordinator that is down for a
 *   minute is ordinary. A loop that propagates that exception stops syncing for
 *   the rest of the session and looks, from the UI, exactly like a quiet group.
 *   So every attempt is caught, recorded on [CoordinatorHealth], and retried
 *   with a backoff that **resets on success** — without the reset, one bad
 *   stretch leaves the loop at its maximum delay forever.
 * - **An account with no groups must not spin.** Both calls return immediately
 *   when the manager holds nothing, so the obvious `while (true)` burns a core
 *   on a brand-new account. This one parks on [CordnGroupManager.gids] until
 *   there is something to sync.
 * - **A group joined mid-subscription must not wait.** The subscription is
 *   opened for a fixed set of `gid`s, so a group joined a moment later is not
 *   in it. Rather than leave the user staring at an empty room until the
 *   stream times out, a change to the group set cancels the subscription and
 *   re-opens it.
 *
 * Nothing here is Marmot's sync. Marmot subscribes to relays by filter and lets
 * the relay decide what to send; cordn asks one coordinator for one group's
 * stream from one cursor. They are different enough that a shared loop would be
 * a conditional, not an abstraction.
 */
class CordnSyncLoop(
    private val source: CordnSyncSource,
    /**
     * Where deliveries go.
     *
     * A callback rather than a `SharedFlow`, deliberately: a flow with no
     * replay drops what it emits when nothing is collecting, and "the app was
     * backgrounded so those messages are gone" is not a thing a chat client may
     * do. The caller decides what durable place these land in.
     */
    private val onDelivery: (CordnGroupManager.Delivery) -> Unit,
    private val subscribeTimeoutMs: Long = DEFAULT_SUBSCRIBE_TIMEOUT_MS,
    private val minBackoffMs: Long = DEFAULT_MIN_BACKOFF_MS,
    private val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
) {
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Stopped)

    /** What the loop is doing, for the UI to show without inventing it. */
    val state: StateFlow<State> = _state.asStateFlow()

    sealed interface State {
        data object Stopped : State

        /** Nothing to sync: this account holds no groups on this coordinator. */
        data object NoGroups : State

        data object CatchingUp : State

        /** A subscription is open. */
        data object Live : State

        /**
         * The last attempt failed and the next is [inMs] away.
         *
         * Carries the reason because the alternative is a UI that can only say
         * "not syncing", which is the same thing it says when the loop is fine
         * and the group is quiet.
         */
        data class Retrying(
            val attempt: Int,
            val inMs: Long,
            val reason: String?,
        ) : State
    }

    /**
     * Starts the loop in [scope]; a second call while it runs does nothing.
     *
     * Two loops on one session would both `catch_up` the same groups and both
     * feed [onDelivery], so every message would surface twice.
     */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    /**
     * Stops the loop and waits for it to actually be stopped. Safe to call
     * when it is not running.
     *
     * `cancel()` alone returns while the loop's `finally` blocks are still
     * running, and one of those persists the group state a subscription
     * ingested. `CordnRuntime.importArchive` stops the loops, deletes the
     * account's directory and restores from the archive — so a persist that
     * landed a moment late wrote a group back onto disk after the delete, and
     * the restore then read it in again. That is the merge importArchive
     * exists to prevent.
     *
     * Nothing was noticed while the persist silently failed on cancellation;
     * making it survive cancellation is what made the missing join matter.
     */
    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        _state.value = State.Stopped
    }

    private suspend fun run() =
        coroutineScope {
            var attempt = 0
            while (isActive) {
                try {
                    val gids = source.gids.value
                    if (gids.isEmpty()) {
                        // Park rather than spin: both calls below are no-ops
                        // with no groups, so looping here would be a busy wait.
                        _state.value = State.NoGroups
                        source.gids.first { it.isNotEmpty() }
                        continue
                    }

                    _state.value = State.CatchingUp
                    source.catchUp(onDelivery)

                    _state.value = State.Live
                    subscribeUntilGroupsChange(gids)

                    // Any completed pass is a working coordinator, including a
                    // subscription that simply timed out.
                    attempt = 0
                } catch (e: CancellationException) {
                    // Only OUR cancellation may end the loop. A coordinator
                    // that does not answer inside the RPC budget surfaces as
                    // `TimeoutCancellationException`, which IS a
                    // `CancellationException` -- rethrowing it ended the loop
                    // for the rest of the session, so one 20-second hiccup
                    // turned into permanent silence with the UI still saying
                    // "catching up". Measured on device: the first
                    // `msg_fetch_many` timeout stopped cordn sync outright.
                    if (!isActive) throw e

                    attempt++
                    val wait = backoffFor(attempt)
                    _state.value = State.Retrying(attempt, wait, e.message)
                    delay(wait)
                } catch (e: Exception) {
                    attempt++
                    val wait = backoffFor(attempt)
                    _state.value = State.Retrying(attempt, wait, e.message)
                    delay(wait)
                }
            }
        }

    /**
     * Holds a subscription until it closes or the group set changes.
     *
     * The watcher is what makes a freshly joined group live immediately instead
     * of at the next timeout.
     */
    private suspend fun subscribeUntilGroupsChange(opened: Set<String>) =
        coroutineScope {
            val subscription =
                async {
                    // `msg_sub_many` spends a total-time budget and throws when
                    // it runs out. That is the stream ending on schedule, not
                    // an outage -- the loop's job is to open the next one -- so
                    // it must not reach the backoff, and it must not reach the
                    // cancellation clause above either.
                    try {
                        source.subscribe(subscribeTimeoutMs, onDelivery)
                    } catch (e: TimeoutCancellationException) {
                        // budget spent; fall through and re-subscribe
                    }
                }
            val changed = async { source.gids.first { it != opened } }

            select {
                subscription.onAwait { changed.cancel() }
                changed.onAwait { subscription.cancel() }
            }
        }

    /** Exponential, capped, and reset by the caller on any success. */
    private fun backoffFor(attempt: Int): Long {
        var wait = minBackoffMs
        repeat(attempt - 1) {
            if (wait >= maxBackoffMs) return maxBackoffMs
            wait *= 2
        }
        return wait.coerceAtMost(maxBackoffMs)
    }

    companion object {
        /**
         * How long one subscription stays open before the loop re-opens it.
         *
         * A ceiling on how long a silently dead stream goes unnoticed, not a
         * polling interval — the coordinator pushes within it.
         */
        const val DEFAULT_SUBSCRIBE_TIMEOUT_MS = 60_000L

        const val DEFAULT_MIN_BACKOFF_MS = 1_000L

        /**
         * Long enough not to hammer a coordinator that is down, short enough
         * that a user who reopens the app is not waiting on a dead timer.
         */
        const val DEFAULT_MAX_BACKOFF_MS = 60_000L
    }
}
