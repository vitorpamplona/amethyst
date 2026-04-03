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
package com.vitorpamplona.quartz.marmot.mip03GroupMessages

/**
 * Deterministic commit conflict resolution for MLS over Nostr (MIP-03).
 *
 * When multiple members submit Commits for the same epoch, exactly one must win:
 * 1. Lowest created_at timestamp wins
 * 2. If timestamps are equal, lexicographically smallest event id wins
 * 3. All other competing Commits for that epoch are discarded
 *
 * This ensures all group members converge on the same group state without
 * requiring a central coordinator.
 */
object CommitOrdering {
    /**
     * Comparator for ordering GroupEvents deterministically.
     * The "winner" (lowest value) should be applied; all others are discarded.
     */
    val comparator: Comparator<GroupEvent> =
        compareBy<GroupEvent> { it.createdAt }
            .thenBy { it.id }

    /**
     * Selects the winning Commit from a set of competing Commits for the same epoch.
     *
     * @param commits competing Commits for the same MLS epoch
     * @return the winning Commit that should be applied, or null if empty
     */
    fun selectWinner(commits: List<GroupEvent>): GroupEvent? {
        if (commits.isEmpty()) return null
        return commits.minWithOrNull(comparator)
    }

    /**
     * Determines if a given commit is the winner among competing commits.
     *
     * @param candidate the commit to check
     * @param competitors all competing commits for the same epoch (including candidate)
     * @return true if candidate is the winning commit
     */
    fun isWinner(
        candidate: GroupEvent,
        competitors: List<GroupEvent>,
    ): Boolean {
        val winner = selectWinner(competitors) ?: return false
        return winner.id == candidate.id
    }

    /**
     * Tracks pending commits per epoch and resolves conflicts.
     *
     * Accumulate commits as they arrive from relays, then call [resolve]
     * to determine which commit wins for each epoch.
     */
    class EpochCommitTracker {
        private val pendingByEpoch = mutableMapOf<Long, MutableList<GroupEvent>>()

        /**
         * Adds a commit for a given epoch.
         *
         * @param epoch the MLS epoch number this commit targets
         * @param commit the GroupEvent containing the commit
         */
        fun addCommit(
            epoch: Long,
            commit: GroupEvent,
        ) {
            pendingByEpoch.getOrPut(epoch) { mutableListOf() }.add(commit)
        }

        /**
         * Returns pending commits for a specific epoch.
         */
        fun pendingForEpoch(epoch: Long): List<GroupEvent> = pendingByEpoch[epoch] ?: emptyList()

        /**
         * Resolves the winning commit for a specific epoch.
         *
         * @param epoch the MLS epoch to resolve
         * @return the winning commit, or null if no commits exist for this epoch
         */
        fun resolve(epoch: Long): GroupEvent? = selectWinner(pendingByEpoch[epoch] ?: emptyList())

        /**
         * Clears pending commits for an epoch after it has been resolved.
         */
        fun clearEpoch(epoch: Long) {
            pendingByEpoch.remove(epoch)
        }

        /**
         * Returns all epochs that have pending commits.
         */
        fun pendingEpochs(): Set<Long> = pendingByEpoch.keys.toSet()

        /**
         * Clears all pending state.
         */
        fun clear() {
            pendingByEpoch.clear()
        }
    }
}
