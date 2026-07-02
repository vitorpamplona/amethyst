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
        policy: RelayAuthPolicy = RelayAuthPolicy.TRUSTED_FOLLOWS,
        isInMyRelayList: Boolean = false,
        servesFollowedWriteCounterparty: Boolean = false,
        servesFollowedReadCounterparty: Boolean = false,
        servesTrustedVenue: Boolean = false,
        readTrustEnabled: Boolean = false,
        hasAttributablePurpose: Boolean = true,
    ) = RelayAuthInputs(
        storedOverride = storedOverride,
        isBlocked = isBlocked,
        policy = policy,
        isInMyRelayList = isInMyRelayList,
        servesFollowedWriteCounterparty = servesFollowedWriteCounterparty,
        servesFollowedReadCounterparty = servesFollowedReadCounterparty,
        servesTrustedVenue = servesTrustedVenue,
        readTrustEnabled = readTrustEnabled,
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
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(policy = RelayAuthPolicy.NEVER, servesFollowedWriteCounterparty = true)))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(policy = RelayAuthPolicy.ALWAYS, hasAttributablePurpose = false)))
    }

    @Test
    fun ifInMyListAllowsOnlyMyRelaysElseAsksWhenAttributable() {
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(policy = RelayAuthPolicy.IF_IN_MY_LIST, isInMyRelayList = true)))
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(policy = RelayAuthPolicy.IF_IN_MY_LIST, isInMyRelayList = false)))
    }

    @Test
    fun trustedFollowsAllowsWriteToFollowedCounterparty() {
        // Sending a DM / delivering a notification to someone I follow -> auto-auth.
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(servesFollowedWriteCounterparty = true)))
    }

    @Test
    fun trustedFollowsDoesNotAutoAllowReadUnlessSubToggleOn() {
        // Reading a followed author's outbox: prompts by default (decision D, conservative)...
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(servesFollowedReadCounterparty = true, readTrustEnabled = false)))
        // ...auto-auths only when the read sub-toggle is enabled.
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(servesFollowedReadCounterparty = true, readTrustEnabled = true)))
    }

    @Test
    fun trustedFollowsAllowsVenueYouJoinedOrFollow() {
        // A public chat / community / live stream you've joined (or whose owner you follow) —
        // auto-auth for both reading and posting, regardless of the read sub-toggle.
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(servesTrustedVenue = true, readTrustEnabled = false)))
    }

    @Test
    fun trustedFollowsFallsThroughForStranger() {
        // Not my relay, no followed counterparty -> prompt when we know why, else silent deny.
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(hasAttributablePurpose = true)))
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(hasAttributablePurpose = false)))
    }
}
