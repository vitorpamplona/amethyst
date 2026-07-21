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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource

import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The group-chat REQ builders widen their timeline kind set with the Buzz stream kinds
 * only for relays [BuzzRelayDialect] has marked — vanilla NIP-29 REQs stay untouched.
 */
class BuzzTimelineKindsTest {
    private val buzzRelay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!
    private val vanillaRelay = RelayUrlNormalizer.normalizeOrNull("wss://groups.example.com/")!!

    @Before
    fun setup() {
        BuzzRelayDialect.clearForTesting()
    }

    @After
    fun tearDown() {
        BuzzRelayDialect.clearForTesting()
    }

    @Test
    fun vanillaRelayKeepsThePlainNip29KindSet() {
        assertEquals(RELAY_GROUP_TIMELINE_KINDS, relayGroupTimelineKinds(vanillaRelay))
    }

    @Test
    fun buzzRelayGetsTheExtendedKindSet() {
        BuzzRelayDialect.mark(buzzRelay)

        val kinds = relayGroupTimelineKinds(buzzRelay)
        assertTrue(kinds.containsAll(RELAY_GROUP_TIMELINE_KINDS))
        assertTrue(kinds.containsAll(BUZZ_RELAY_GROUP_TIMELINE_EXTRA_KINDS))

        // The vanilla relay is unaffected by the buzz mark on another relay.
        assertEquals(RELAY_GROUP_TIMELINE_KINDS, relayGroupTimelineKinds(vanillaRelay))
    }

    @Test
    fun openChatTailFilterCarriesBuzzKindsOnlyOnBuzzRelays() {
        BuzzRelayDialect.mark(buzzRelay)

        val buzzFilter = buildRelayGroupOpenChatTailFilter(GroupId("g1", buzzRelay), sinceEpoch = 0L)
        val vanillaFilter = buildRelayGroupOpenChatTailFilter(GroupId("g1", vanillaRelay), sinceEpoch = 0L)

        assertTrue(buzzFilter.filter.kinds!!.contains(StreamMessageV2Event.KIND))
        assertFalse(vanillaFilter.filter.kinds!!.contains(StreamMessageV2Event.KIND))
    }
}
