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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions.Companion.BAN
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions.Companion.KICK
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions.Companion.MANAGE_ROLES
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions.Companion.MENTION_EVERYONE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordPermissionsTest {
    @Test
    fun bitPositionsAreFrozen() {
        assertEquals(0, MANAGE_ROLES)
        assertEquals(3, KICK)
        assertEquals(4, BAN)
        assertEquals(9, MENTION_EVERYONE)
    }

    @Test
    fun hasChecksIndividualBits() {
        val p = ConcordPermissions.of(KICK, BAN)
        assertTrue(p.has(KICK))
        assertTrue(p.has(BAN))
        assertFalse(p.has(MANAGE_ROLES))
    }

    @Test
    fun unionIsBitwiseOr() {
        val a = ConcordPermissions.of(KICK)
        val b = ConcordPermissions.of(BAN)
        val u = a union b
        assertTrue(u.has(KICK))
        assertTrue(u.has(BAN))
        // effective permissions are the union of a member's roles' bits
        assertEquals(ConcordPermissions.of(KICK, BAN).bits, u.bits)
    }

    @Test
    fun wireEncodingIsDecimalString() {
        // KICK(3) | BAN(4) = 0b11000 = 24
        assertEquals("24", ConcordPermissions.of(KICK, BAN).toWire())
        assertEquals(ConcordPermissions.of(KICK, BAN).bits, ConcordPermissions.fromWire("24").bits)
    }

    @Test
    fun highBitsSurviveDecimalRoundTripWithoutFloatingPointLoss() {
        // Bit 63 set — a value that would be corrupted if parsed as a JSON double.
        val hi = ConcordPermissions(1uL shl 63)
        val wire = hi.toWire()
        assertEquals("9223372036854775808", wire)
        assertEquals(hi.bits, ConcordPermissions.fromWire(wire).bits)
    }

    @Test
    fun blankParsesToNoneAndGarbageIsRejected() {
        assertEquals(ConcordPermissions.NONE.bits, ConcordPermissions.fromWire("").bits)
        assertNull(ConcordPermissions.fromWireOrNull("not-a-number"))
        assertNull(ConcordPermissions.fromWireOrNull("-1"))
    }

    @Test
    fun entityKindWireMappingMatchesReference() {
        assertEquals(ControlEntityKind.METADATA, ControlEntityKind.of("0"))
        assertEquals(ControlEntityKind.ROLE, ControlEntityKind.of("1"))
        assertEquals(ControlEntityKind.CHANNEL, ControlEntityKind.of("2"))
        assertEquals(ControlEntityKind.GRANT, ControlEntityKind.of("3"))
        assertEquals(ControlEntityKind.BANLIST, ControlEntityKind.of("4"))
        assertEquals(ControlEntityKind.INVITE_LIVE, ControlEntityKind.of("6"))
        assertEquals(ControlEntityKind.INVITE_REGISTRY, ControlEntityKind.of("8"))
        assertEquals(ControlEntityKind.INVITE_REVOKED, ControlEntityKind.of("9"))
        assertEquals(ControlEntityKind.DISSOLVED, ControlEntityKind.of("10"))
        assertNull(ControlEntityKind.of("7")) // retired/unknown
    }
}
