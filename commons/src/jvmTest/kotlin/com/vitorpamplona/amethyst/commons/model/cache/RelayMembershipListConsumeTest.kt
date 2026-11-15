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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Kind 13534 (NIP-43 membership list) must reach the replaceable cache. Its quartz class used to
 * extend plain Event, so it had no address and every copy failed with "Event must be addressable:
 * 13534", which also kept the Buzz community roster from ever updating. Both events were signed by a
 * local geode relay: a member joins, then leaves.
 */
class RelayMembershipListConsumeTest {
    private val withMember =
        """
        {"id":"6c779e89478f489315540255c1e033c2e929544f787d1ec47a806a62daa64b1e","pubkey":"b645824ff06a105b114ed2ecd53bb0304444246d7083e249753fc832a675ddfe","created_at":1794770444,"kind":13534,"tags":[["-"],["member","91ab519c21439f179707cc12d8e7188babfeddfac3edc4cb7ae3a5434d5146b5"]],"content":"","sig":"ea528e2eb8820c241572ef9b108ce52088ed4010fed576992a55b9123d6863c0047d21e09f693924c2397fa07a1cd075f53bfafc27582c9ebbdbcca92d9076b2"}
        """.trimIndent()

    private val afterLeaving =
        """
        {"id":"cedb7bb0f09a42dc5ccbc4064e5d22a414c37ee309cf4f66ad641019ead7a153","pubkey":"b645824ff06a105b114ed2ecd53bb0304444246d7083e249753fc832a675ddfe","created_at":1794770456,"kind":13534,"tags":[["-"]],"content":"","sig":"706b374047abe09f4f570595f5b5b98387743e670c0a9135c2ed21e9087dadbc936a9947ad8b01c27ec32344f2586052529245d09e6e5ecaa2f3ea2a062407a8"}
        """.trimIndent()

    @Test
    fun storesTheListAndKeepsTheNewest() {
        val first = Event.fromJson(withMember)
        val second = Event.fromJson(afterLeaving)
        assertIs<RelayMembershipListEvent>(first)
        assertIs<RelayMembershipListEvent>(second)

        val cache = EventCache()
        assertTrue(cache.justConsume(first, null, true))
        assertSame(first, cache.getAddressableNoteIfExists(first.address())?.event)

        assertTrue(cache.justConsume(second, null, true))
        assertSame(second, cache.getAddressableNoteIfExists(first.address())?.event)
    }
}
