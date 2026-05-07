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
package com.vitorpamplona.quartz.nip86RelayManagement.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BanStoreTest {
    @Test
    fun pubkeyBanIsCaseInsensitive() {
        val s = BanStore()
        s.banPubkey("ABCDEF1234".padEnd(64, '0'), "spam")
        assertTrue(s.isBanned("abcdef1234".padEnd(64, '0')))
        s.unbanPubkey("abcdef1234".padEnd(64, '0'))
        assertFalse(s.isBanned("ABCDEF1234".padEnd(64, '0')))
    }

    @Test
    fun allowListEmptyMeansEveryoneAllowed() {
        val s = BanStore()
        assertFalse(s.hasAllowList())
        // No allow list → policy decision is purely deny-based; the
        // store doesn't say a pubkey IS allowed unless it's listed.
        assertFalse(s.isAllowedPubkey("aaaa".padEnd(64, '0')))
    }

    @Test
    fun allowListNonEmptyTracksMembers() {
        val s = BanStore()
        s.allowPubkey("aa".padEnd(64, '0'), "trusted")
        assertTrue(s.hasAllowList())
        assertTrue(s.isAllowedPubkey("aa".padEnd(64, '0')))
        assertFalse(s.isAllowedPubkey("bb".padEnd(64, '0')))
        s.unallowPubkey("aa".padEnd(64, '0'))
        assertFalse(s.hasAllowList())
    }

    @Test
    fun eventBanRoundTrip() {
        val s = BanStore()
        s.banEvent("ee".padEnd(64, '0'), "policy")
        assertTrue(s.isBannedEvent("EE".padEnd(64, '0')))
        s.allowEvent("ee".padEnd(64, '0'))
        assertFalse(s.isBannedEvent("ee".padEnd(64, '0')))
    }

    @Test
    fun kindAllowDenyRules() {
        val s = BanStore()
        // Empty allow + empty deny → every kind is allowed.
        assertTrue(s.isKindAllowed(1))

        s.allowKind(1)
        s.allowKind(7)
        // Allow non-empty → only listed kinds are allowed.
        assertTrue(s.isKindAllowed(1))
        assertFalse(s.isKindAllowed(4))

        s.disallowKind(7)
        // Disallowing a kind removes it from the allow list and blocks.
        assertFalse(s.isKindAllowed(7))
        assertTrue(s.isKindAllowed(1))
        assertEquals(listOf(1), s.listAllowedKinds())
        assertEquals(listOf(7), s.listDisallowedKinds())
    }

    @Test
    fun listsReflectStateForAuditTrail() {
        val s = BanStore()
        s.banPubkey("aa".padEnd(64, '0'), "spam")
        s.banPubkey("bb".padEnd(64, '0'), null)
        val banned = s.listBannedPubkeys().toMap()
        assertEquals("spam", banned["aa".padEnd(64, '0')])
        assertEquals(null, banned["bb".padEnd(64, '0')])
    }
}
