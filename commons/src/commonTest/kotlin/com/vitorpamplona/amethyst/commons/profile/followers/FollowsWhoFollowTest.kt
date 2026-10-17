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
package com.vitorpamplona.amethyst.commons.profile.followers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class FollowsWhoFollowTest {
    private val target = "aa".repeat(32)
    private val alice = "a1".repeat(32)
    private val bob = "b0".repeat(32)
    private val carol = "c0".repeat(32)
    private val dave = "d0".repeat(32)

    private fun list(
        owner: HexKey,
        createdAt: Long,
        vararg follows: HexKey,
    ) = ContactListEvent("11".repeat(32), owner, createdAt, follows.map { arrayOf("p", it) }.toTypedArray(), "", "22".repeat(64))

    @Test
    fun countsOnlyFollowsWhoseNewestListTagsTheTarget() {
        val lists =
            mapOf(
                alice to list(alice, 10, target, bob),
                bob to list(bob, 10, carol),
                carol to list(carol, 10, target),
                // dave has no list in the cache
            )

        val found = followersAmong(listOf(alice, bob, carol, dave), target, { lists[it] }, { false })

        assertEquals(listOf(alice, carol), found)
    }

    @Test
    fun aListThatDropsTheTargetStopsCounting() {
        val lists = mutableMapOf(alice to list(alice, 10, target), bob to list(bob, 10, target))
        assertEquals(listOf(alice, bob), followersAmong(listOf(alice, bob), target, { lists[it] }, { false }))

        // Alice unfollows: her replacement list no longer tags the target.
        lists[alice] = list(alice, 20, bob)

        assertEquals(listOf(bob), followersAmong(listOf(alice, bob), target, { lists[it] }, { false }))
    }

    @Test
    fun leavesOutBlockedFollowsAndTheTargetItself() {
        val lists = mapOf(alice to list(alice, 10, target), bob to list(bob, 10, target), target to list(target, 10, target))

        val found = followersAmong(listOf(alice, bob, target), target, { lists[it] }, { it == bob })

        assertEquals(listOf(alice), found)
    }
}
