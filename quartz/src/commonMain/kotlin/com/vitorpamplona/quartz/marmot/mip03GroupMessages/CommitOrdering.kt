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
     * Key for tracking pending commits: must be unique per (group, epoch) pair
     * since epoch numbers are not globally unique across different groups.
     */
    data class GroupEpochKey(
        val groupId: String,
        val epoch: Long,
    )

    /**
     * Tracks pending commits per (group, epoch) and resolves conflicts.
     *
     * Accumulate commits as they arrive from relays, then call [resolve]
     * to determine which commit wins for each (group, epoch).
     */
    class EpochCommitTracker {
        private val lock = kotlinx.atomicfu.locks.SynchronizedObject()
        private val pendingByGroupEpoch = mutableMapOf<GroupEpochKey, MutableList<GroupEvent>>()

        companion object {
            /** Maximum number of (group, epoch) entries to track before evicting oldest. */
            const val MAX_TRACKED_EPOCHS = 1000
        }

        /**
         * Adds a commit for a given group and epoch.
         *
         * @param groupId the Nostr group ID
         * @param epoch the MLS epoch number this commit targets
         * @param commit the GroupEvent containing the commit
         */
        fun addCommit(
            groupId: String,
            epoch: Long,
            commit: GroupEvent,
        ) = kotlinx.atomicfu.locks.synchronized(lock) {
            val key = GroupEpochKey(groupId, epoch)
            pendingByGroupEpoch.getOrPut(key) { mutableListOf() }.add(commit)

            // Evict oldest entries if the tracker grows too large
            if (pendingByGroupEpoch.size > MAX_TRACKED_EPOCHS) {
                val oldestKeys =
                    pendingByGroupEpoch.keys
                        .sortedBy { it.epoch }
                        .take(pendingByGroupEpoch.size - MAX_TRACKED_EPOCHS)
                for (oldKey in oldestKeys) {
                    pendingByGroupEpoch.remove(oldKey)
                }
            }
        }

        /**
         * Returns pending commits for a specific group and epoch.
         */
        fun pendingForEpoch(
            groupId: String,
            epoch: Long,
        ): List<GroupEvent> =
            kotlinx.atomicfu.locks.synchronized(lock) {
                pendingByGroupEpoch[GroupEpochKey(groupId, epoch)]?.toList() ?: emptyList()
            }

        /**
         * Resolves the winning commit for a specific group and epoch.
         *
         * @param groupId the Nostr group ID
         * @param epoch the MLS epoch to resolve
         * @return the winning commit, or null if no commits exist for this (group, epoch)
         */
        fun resolve(
            groupId: String,
            epoch: Long,
        ): GroupEvent? =
            kotlinx.atomicfu.locks.synchronized(lock) {
                selectWinner(pendingByGroupEpoch[GroupEpochKey(groupId, epoch)] ?: emptyList())
            }

        /**
         * Clears pending commits for a (group, epoch) after it has been resolved.
         */
        fun clearEpoch(
            groupId: String,
            epoch: Long,
        ) = kotlinx.atomicfu.locks.synchronized(lock) {
            pendingByGroupEpoch.remove(GroupEpochKey(groupId, epoch))
        }

        /**
         * Returns all (group, epoch) keys that have pending commits.
         */
        fun pendingGroupEpochs(): Set<GroupEpochKey> =
            kotlinx.atomicfu.locks.synchronized(lock) {
                pendingByGroupEpoch.keys.toSet()
            }

        /**
         * Clears all pending state.
         */
        fun clear() =
            kotlinx.atomicfu.locks.synchronized(lock) {
                pendingByGroupEpoch.clear()
            }
    }
}
