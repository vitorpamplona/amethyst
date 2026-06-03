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
package com.vitorpamplona.quartz.nip72ModCommunities.rules

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertTrue

class CommunityRulesEventFactoryTest {
    /**
     * Regression test for the ClassCastException seen in production when
     * publishing community rules: the factory used to fall back to the generic
     * [Event] for kind 34551 because [CommunityRulesEvent] was never registered,
     * so `signer.sign(...)` produced an [Event] that couldn't be cast back to
     * [CommunityRulesEvent] in `Account.sendCommunityRules`.
     */
    @Test
    fun factoryBuildsCommunityRulesEventForKind34551() {
        val event: Event =
            EventFactory.create(
                id = "00".repeat(32),
                pubKey = "11".repeat(32),
                createdAt = 1_700_000_000L,
                kind = CommunityRulesEvent.KIND,
                tags = arrayOf(arrayOf("d", "my-community")),
                content = "",
                sig = "22".repeat(64),
            )

        assertTrue(
            event is CommunityRulesEvent,
            "Expected a CommunityRulesEvent but got ${event::class.simpleName}",
        )
    }
}
