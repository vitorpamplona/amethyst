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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Notices when this device has fallen off a group's epoch chain: the other members
 * keep talking and none of it decrypts here.
 *
 * A fork like that does not heal on its own. The members who moved on hold no copy of
 * the epoch this device is stuck at, and this device cannot apply their commits, so
 * every message they send fails with "decrypts on no canonical epoch". Groups broken
 * this way before the own-commit echo fix never recovered, and nothing told the user.
 *
 * The same error is also routine: an event from BEFORE this device joined, or older
 * than the retained epochs, fails exactly like that. So only failures NEWER than the
 * last event this group decrypted here count, a single burst does not trip it (they
 * must span [minSpanSec] of sender time), and one successful decrypt clears it. A
 * group with no baseline (never decrypted anything here and not seeded) is never
 * flagged: there is nothing to be behind.
 */
class MarmotDesyncDetector(
    private val threshold: Int = 3,
    private val minSpanSec: Long = 60,
) {
    private class Tracker {
        var lastSuccessAt: Long = 0
        val failures = mutableMapOf<HexKey, Long>()
        var desynced = false
    }

    private val lock = KmpLock()
    private val groups = mutableMapOf<HexKey, Tracker>()

    /** Baseline for a group restored or joined with no decrypt yet this session. */
    fun seed(
        groupId: HexKey,
        createdAt: Long,
    ) = lock.withLock {
        val t = groups.getOrPut(groupId) { Tracker() }
        if (createdAt > t.lastSuccessAt) t.lastSuccessAt = createdAt
    }

    /** Something at [createdAt] decrypted: the group is on the chain. Returns true if that cleared a desync. */
    fun onDecrypted(
        groupId: HexKey,
        createdAt: Long,
    ): Boolean =
        lock.withLock {
            val t = groups.getOrPut(groupId) { Tracker() }
            if (createdAt > t.lastSuccessAt) t.lastSuccessAt = createdAt
            t.failures.clear()
            val was = t.desynced
            t.desynced = false
            was
        }

    /** An app message that decrypts on no epoch here. Returns true when this call newly flags the group. */
    fun onUndecryptable(
        groupId: HexKey,
        eventId: HexKey,
        createdAt: Long,
    ): Boolean =
        lock.withLock {
            val t = groups[groupId] ?: return@withLock false
            if (t.lastSuccessAt == 0L || createdAt <= t.lastSuccessAt || t.desynced) return@withLock false
            t.failures[eventId] = createdAt
            val span = t.failures.values.max() - t.failures.values.min()
            if (t.failures.size >= threshold && span >= minSpanSec) {
                t.desynced = true
                true
            } else {
                false
            }
        }

    fun isDesynced(groupId: HexKey): Boolean = lock.withLock { groups[groupId]?.desynced == true }

    fun forget(groupId: HexKey) {
        lock.withLock { groups.remove(groupId) }
    }
}
