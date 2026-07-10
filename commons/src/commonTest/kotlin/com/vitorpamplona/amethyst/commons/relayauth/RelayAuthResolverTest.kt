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
package com.vitorpamplona.amethyst.commons.relayauth

import kotlin.test.Test
import kotlin.test.assertEquals

class RelayAuthResolverTest {
    private fun inputs(
        storedOverride: RelayAuthDecision? = null,
        isBlocked: Boolean = false,
        policy: RelayAuthPolicy = RelayAuthPolicy.CUSTOM,
        toggles: RelayAuthCustomToggles = RelayAuthCustomToggles(),
        isInMyRelayList: Boolean = false,
        servesTrustedVenue: Boolean = false,
        servesFollowedReadCounterparty: Boolean = false,
        servesFollowedWriteCounterparty: Boolean = false,
        servesStrangerWriteCounterparty: Boolean = false,
        hasAttributablePurpose: Boolean = true,
    ) = RelayAuthInputs(
        storedOverride = storedOverride,
        isBlocked = isBlocked,
        policy = policy,
        toggles = toggles,
        isInMyRelayList = isInMyRelayList,
        servesTrustedVenue = servesTrustedVenue,
        servesFollowedReadCounterparty = servesFollowedReadCounterparty,
        servesFollowedWriteCounterparty = servesFollowedWriteCounterparty,
        servesStrangerWriteCounterparty = servesStrangerWriteCounterparty,
        hasAttributablePurpose = hasAttributablePurpose,
    )

    private fun resolve(inputs: RelayAuthInputs) = RelayAuthResolver.resolve(inputs)

    @Test
    fun blockedRelayAlwaysDeniesEvenWithAllowOverrideAndAlwaysPolicy() {
        assertEquals(
            RelayAuthVerdict.DENY,
            resolve(
                inputs(
                    isBlocked = true,
                    storedOverride = RelayAuthDecision.ALLOW,
                    policy = RelayAuthPolicy.ALWAYS,
                ),
            ),
        )
    }

    @Test
    fun explicitOverrideBeatsPolicy() {
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(storedOverride = RelayAuthDecision.DENY, policy = RelayAuthPolicy.ALWAYS)))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(storedOverride = RelayAuthDecision.ALLOW, policy = RelayAuthPolicy.NEVER)))
    }

    @Test
    fun neverAndAlwaysAreUnconditional() {
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(policy = RelayAuthPolicy.NEVER, isInMyRelayList = true)))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(policy = RelayAuthPolicy.ALWAYS, hasAttributablePurpose = false)))
    }

    @Test
    fun customMyRelaysAndVenuesToggleGatesOwnRelaysAndVenues() {
        // On (default): my own relay and any joined venue auto-auth.
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(isInMyRelayList = true)))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(servesTrustedVenue = true)))
        // Off: even my own relay prompts.
        val off = RelayAuthCustomToggles(myRelaysAndVenues = false)
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(isInMyRelayList = true, toggles = off)))
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(servesTrustedVenue = true, toggles = off)))
    }

    @Test
    fun customReadFollowsToggleGatesReadingFollows() {
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(servesFollowedReadCounterparty = true)))
        assertEquals(
            RelayAuthVerdict.ASK,
            resolve(inputs(servesFollowedReadCounterparty = true, toggles = RelayAuthCustomToggles(readFollows = false))),
        )
    }

    @Test
    fun customMessageFollowsToggleGatesMessagingFollows() {
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(servesFollowedWriteCounterparty = true)))
        assertEquals(
            RelayAuthVerdict.ASK,
            resolve(inputs(servesFollowedWriteCounterparty = true, toggles = RelayAuthCustomToggles(messageFollows = false))),
        )
    }

    @Test
    fun customMessageStrangersIsOffByDefault() {
        // Default off: messaging a stranger prompts...
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(servesStrangerWriteCounterparty = true)))
        // ...on: auto-auth.
        assertEquals(
            RelayAuthVerdict.ALLOW,
            resolve(inputs(servesStrangerWriteCounterparty = true, toggles = RelayAuthCustomToggles(messageStrangers = true))),
        )
    }

    @Test
    fun customHasNoToggleForReadingStrangers() {
        // Reading a non-followed author (no matching category) always prompts, even with every
        // toggle on — there is deliberately no "read strangers" trust category.
        val allOn = RelayAuthCustomToggles(myRelaysAndVenues = true, readFollows = true, messageFollows = true, messageStrangers = true)
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, hasAttributablePurpose = true)))
    }

    @Test
    fun customFallsThroughForUncoveredSituation() {
        // Nothing matches -> prompt when we know why, else silent deny.
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(hasAttributablePurpose = true)))
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(hasAttributablePurpose = false)))
    }
}
