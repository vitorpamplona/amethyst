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
package com.vitorpamplona.amethyst.desktop.followpacks

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent

data class BulkFollowPreview(
    val totalMembers: Int,
    val newUsers: List<User>,
    val existingUsers: List<User>,
) {
    val newCount: Int get() = newUsers.size
    val existingCount: Int get() = existingUsers.size
}

/**
 * Stateless helpers for the Follow-all action.
 *
 * computePreview() resolves pack `p` tags to materialized Users
 * (creating placeholders for unknown pubkeys so relay hints survive),
 * and partitions them into new vs already-followed.
 *
 * commit() forwards to Kind3FollowListState.follow(users) which is
 * Mutex-guarded against concurrent calls and dedupes inside Quartz.
 */
object BulkFollowAction {
    /**
     * Resolves every pubkey in [pack] to a User, including those without
     * cached metadata (so the bulk-follow always carries relay hints).
     */
    fun computePreview(
        pack: FollowListEvent,
        cache: DesktopLocalCache,
        currentFollows: Set<HexKey>,
        myPubkey: HexKey,
    ): BulkFollowPreview {
        val newUsers = mutableListOf<User>()
        val existingUsers = mutableListOf<User>()
        pack.follows().forEach { userTag ->
            // Skip self-follow attempts (gap N6)
            if (userTag.pubKey == myPubkey) return@forEach
            val user = cache.getOrCreateUser(userTag.pubKey)
            if (userTag.pubKey in currentFollows) {
                existingUsers.add(user)
            } else {
                newUsers.add(user)
            }
        }
        return BulkFollowPreview(
            totalMembers = pack.follows().size,
            newUsers = newUsers,
            existingUsers = existingUsers,
        )
    }

    /**
     * Publishes the bulk-follow. Returns the new ContactListEvent or null if
     * publish/sign failed.
     */
    suspend fun commit(
        usersToAdd: List<User>,
        iAccount: DesktopIAccount,
        relayManager: RelayConnectionManager,
        cache: DesktopLocalCache,
    ): Boolean {
        if (!iAccount.isWriteable()) return false
        if (usersToAdd.isEmpty()) return true
        return try {
            val newContactList = iAccount.kind3FollowList.follow(usersToAdd)
            cache.consume(newContactList, null, wasVerified = false)
            relayManager.broadcastToAll(newContactList)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    /** Unfollows every pack member that the user currently follows. */
    suspend fun commitUnfollowAll(
        pack: FollowListEvent,
        iAccount: DesktopIAccount,
        relayManager: RelayConnectionManager,
        cache: DesktopLocalCache,
    ): Boolean {
        if (!iAccount.isWriteable()) return false
        val toRemove =
            pack.followIds().mapNotNull { hex ->
                if (hex != iAccount.pubKey) cache.getOrCreateUser(hex) else null
            }
        if (toRemove.isEmpty()) return true
        return try {
            val newContactList = iAccount.kind3FollowList.unfollow(toRemove) ?: return false
            cache.consume(newContactList, null, wasVerified = false)
            relayManager.broadcastToAll(newContactList)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }
}
