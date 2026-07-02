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
package com.vitorpamplona.amethyst.commons.wot

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Friends-of-friends trust score computed from the active user's follow
 * graph. For every pubkey X the score is the count of accounts in the
 * active user's follow set who also follow X.
 *
 * Scores are exposed via a Compose-observable [SnapshotStateMap] with
 * per-key subscriber isolation — only avatars whose specific pubkey
 * scored differently recompose when the map mutates.
 *
 * All internal state is mutated from a single writer coroutine
 * ([writerLoop]) on [Dispatchers.Default], so concurrent
 * [applyKind3] / [onFollowSetChange] / [markReadyOnce] calls from
 * different threads are serialized without extra locking.
 */
@Stable
class WoTService(
    private val scope: CoroutineScope,
    /** Dispatcher for the internal writer coroutine. Tests override with `Dispatchers.Unconfined` for synchronous behavior. */
    private val writerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * Sparse per-pubkey score map. Entries with count 0 are removed
     * (not stored as 0) to keep the Compose subscriber tracking tight.
     * Callers should read as `scores[pubkey] ?: 0`.
     */
    private val _scores: SnapshotStateMap<HexKey, Int> = mutableStateMapOf()
    val scores: SnapshotStateMap<HexKey, Int> get() = _scores

    // Reverse index: target pubkey → set of my-follows who follow them.
    private val reverseIndex = HashMap<HexKey, MutableSet<HexKey>>()

    // Per-follower cached follow set (excluding self / follower itself).
    // Enables diff-based updates when a follower republishes their kind-3.
    private val perFollowerSnapshot = HashMap<HexKey, Set<HexKey>>()

    private var myFollows: Set<HexKey> = emptySet()
    private var selfPubkey: HexKey? = null
    private var readyMarked = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val ops = Channel<Op>(capacity = Channel.UNLIMITED)

    init {
        scope.launch(writerDispatcher) { writerLoop() }
    }

    /** Update the active user's follow set (and self pubkey). */
    fun onFollowSetChange(
        newFollows: Set<HexKey>,
        newSelf: HexKey?,
    ) {
        ops.trySend(Op.FollowSet(newFollows, newSelf))
    }

    /**
     * Ingest a kind-3 event for a followed pubkey. Ignored when the
     * event's author isn't in the current follow set. Follow lists are
     * capped at [MAX_FOLLOWS_PER_EVENT] to bound CPU cost against a
     * hostile publisher.
     */
    fun applyKind3(
        follower: HexKey,
        follows: Set<HexKey>,
    ) {
        val bounded =
            if (follows.size > MAX_FOLLOWS_PER_EVENT) {
                follows.take(MAX_FOLLOWS_PER_EVENT).toSet()
            } else {
                follows
            }
        ops.trySend(Op.Kind3(follower, bounded))
    }

    /**
     * Mark the service as ready to render badges. Idempotent — subsequent
     * calls are no-ops. Trigger from the first EOSE on the batch kind-3
     * REQ, or from a startup-timeout fallback, whichever fires first.
     */
    fun markReadyOnce() {
        ops.trySend(Op.MarkReady)
    }

    /** Clear all state. Used on logout / account switch. */
    fun clear() {
        ops.trySend(Op.Clear)
    }

    /**
     * Returns a plain [Map] snapshot of current scores for headless
     * callers (e.g. the amy CLI) that don't run inside a Compose
     * composition. O(N) copy from the underlying [SnapshotStateMap].
     */
    fun scoresSnapshot(): Map<HexKey, Int> = HashMap(_scores)

    private sealed interface Op {
        data class FollowSet(
            val newFollows: Set<HexKey>,
            val newSelf: HexKey?,
        ) : Op

        data class Kind3(
            val follower: HexKey,
            val follows: Set<HexKey>,
        ) : Op

        data object MarkReady : Op

        data object Clear : Op
    }

    private suspend fun writerLoop() {
        for (op in ops) {
            Snapshot.withMutableSnapshot {
                when (op) {
                    is Op.FollowSet -> handleFollowSet(op.newFollows, op.newSelf)
                    is Op.Kind3 -> handleKind3(op.follower, op.follows)
                    Op.MarkReady -> handleMarkReady()
                    Op.Clear -> handleClear()
                }
            }
        }
    }

    private fun handleFollowSet(
        newFollows: Set<HexKey>,
        newSelf: HexKey?,
    ) {
        val removed = myFollows - newFollows
        myFollows = newFollows
        selfPubkey = newSelf

        // Guardrail — massive follow lists don't produce a useful WoT signal.
        if (myFollows.size > MAX_FOLLOWS) {
            reverseIndex.clear()
            perFollowerSnapshot.clear()
            _scores.clear()
            handleMarkReady()
            return
        }

        // Uncredit any follower we're no longer following.
        removed.forEach { follower ->
            val prevFollows = perFollowerSnapshot.remove(follower) ?: return@forEach
            prevFollows.forEach { target ->
                val set = reverseIndex[target] ?: return@forEach
                set.remove(follower)
                if (set.isEmpty()) reverseIndex.remove(target)
                updateScore(target)
            }
        }
        // Added followers will be credited when their kind-3 arrives via applyKind3.
    }

    private fun handleKind3(
        follower: HexKey,
        follows: Set<HexKey>,
    ) {
        if (follower !in myFollows) return

        val old = perFollowerSnapshot[follower] ?: emptySet()
        val excluded = setOfNotNull(follower, selfPubkey)
        val effective = follows - excluded
        val added = effective - old
        val removed = old - effective
        perFollowerSnapshot[follower] = effective

        added.forEach { target ->
            reverseIndex.getOrPut(target) { hashSetOf() }.add(follower)
            updateScore(target)
        }
        removed.forEach { target ->
            val set = reverseIndex[target] ?: return@forEach
            set.remove(follower)
            if (set.isEmpty()) reverseIndex.remove(target)
            updateScore(target)
        }
    }

    private fun handleMarkReady() {
        if (!readyMarked) {
            readyMarked = true
            _isReady.value = true
        }
    }

    private fun handleClear() {
        reverseIndex.clear()
        perFollowerSnapshot.clear()
        _scores.clear()
        myFollows = emptySet()
        selfPubkey = null
        readyMarked = false
        _isReady.value = false
    }

    private fun updateScore(target: HexKey) {
        val n = reverseIndex[target]?.size ?: 0
        if (n > 0) _scores[target] = n else _scores.remove(target)
    }

    companion object {
        /** Skip WoT entirely for accounts following more than this many pubkeys. */
        const val MAX_FOLLOWS = 2000

        /** Cap follows per kind-3 event to bound CPU cost against a hostile publisher. */
        const val MAX_FOLLOWS_PER_EVENT = 5000
    }
}
