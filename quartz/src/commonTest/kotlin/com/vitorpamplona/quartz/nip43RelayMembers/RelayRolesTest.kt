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
package com.vitorpamplona.quartz.nip43RelayMembers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip43RelayMembers.inviteRequest.RelayInviteRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.RelayJoinRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.tags.MemberTag
import com.vitorpamplona.quartz.nip43RelayMembers.list.tags.RelayMember
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import com.vitorpamplona.quartz.nip70ProtectedEvts.isProtected
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayRolesTest {
    private val signer = NostrSignerSync(KeyPair())
    private val alice = "c308e1f882c1f1dff2a43d4294239ddeec04e575f2d1aad1fa21ea7684e61fb5"
    private val bob = "ee1d336e13779e4d4c527b988429d96de16088f958cbf6c074676ac9cfd9c958"

    @Test
    fun roleEventBuildsTheSpecExampleAndParsesBack() {
        val template = RelayRoleEvent.build(RelayRole("28b7e50f", "king", "ruler of the relay", 37, 1), createdAt = 1000)
        assertEquals(RelayRoleEvent.KIND, template.kind)
        assertContentEquals(arrayOf("-"), template.tags[0])
        assertContentEquals(arrayOf("d", "28b7e50f"), template.tags[1])
        assertContentEquals(arrayOf("label", "king"), template.tags[2])
        assertContentEquals(arrayOf("description", "ruler of the relay"), template.tags[3])
        assertContentEquals(arrayOf("color", "37"), template.tags[4])
        assertContentEquals(arrayOf("order", "1"), template.tags[5])

        val signed = assertIs<RelayRoleEvent>(signer.sign(template))
        assertTrue(signed.isProtected())
        assertEquals("28b7e50f", signed.roleId())
        assertEquals(RelayRole("28b7e50f", "king", "ruler of the relay", 37, 1), signed.role())
    }

    @Test
    fun roleEventOptionalTagsAndBadValues() {
        val minimal = assertIs<RelayRoleEvent>(signer.sign(RelayRoleEvent.build(RelayRole("r1"))))
        assertEquals(RelayRole("r1"), minimal.role())

        // A foreign event with an out-of-range hue and a non-numeric order.
        val odd =
            assertIs<RelayRoleEvent>(
                signer.sign<Event>(
                    1000,
                    RelayRoleEvent.KIND,
                    arrayOf(arrayOf("-"), arrayOf("d", "r2"), arrayOf("color", "400"), arrayOf("order", "first")),
                    "",
                ),
            )
        assertNull(odd.color())
        assertNull(odd.order())

        assertFailsWith<IllegalArgumentException> { RelayRoleEvent.build(RelayRole("r3", color = 361)) }
    }

    @Test
    fun memberTagCarriesOptionalRoles() {
        val withRoles = arrayOf("member", bob, "28b7e50f", "", "abc", "28b7e50f")
        assertEquals(bob, MemberTag.parse(withRoles))
        assertEquals(RelayMember(bob, listOf("28b7e50f", "abc")), MemberTag.parseMember(withRoles))
        assertEquals(RelayMember(alice), MemberTag.parseMember(arrayOf("member", alice)))
        assertNull(MemberTag.parseMember(arrayOf("member", "short")))

        assertContentEquals(arrayOf("member", bob, "r1", "r2"), MemberTag.assemble(bob, listOf("r1", "r2")))
        assertContentEquals(arrayOf("member", alice), MemberTag.assemble(RelayMember(alice)))
    }

    @Test
    fun membershipListWithRolesKeepsPlainMembersBackwardCompatible() {
        val template =
            RelayMembershipListEvent.buildWithRoles(
                listOf(RelayMember(alice), RelayMember(bob, listOf("28b7e50f"))),
            )
        val signed = assertIs<RelayMembershipListEvent>(signer.sign(template))
        assertTrue(signed.isProtected())
        assertEquals(listOf(alice, bob), signed.members())
        assertEquals(listOf(RelayMember(alice), RelayMember(bob, listOf("28b7e50f"))), signed.membersWithRoles())

        val legacy = assertIs<RelayMembershipListEvent>(signer.sign(RelayMembershipListEvent.build(listOf(alice))))
        assertEquals(listOf(RelayMember(alice)), legacy.membersWithRoles())
    }

    @Test
    fun joinRequestCarriesClaimAndProtectedTag() {
        val signed = assertIs<RelayJoinRequestEvent>(signer.sign(RelayJoinRequestEvent.build("invite-code")))
        assertTrue(signed.isProtected())
        assertEquals("invite-code", signed.claim())

        assertFailsWith<IllegalArgumentException> { RelayJoinRequestEvent.build(" ") }
    }

    @Suppress("DEPRECATION")
    @Test
    fun deprecatedInviteRequestStillParses() {
        val signed =
            signer.sign<Event>(1000, RelayInviteRequestEvent.KIND, arrayOf(arrayOf("-"), arrayOf("claim", "abc")), "")
        assertIs<RelayInviteRequestEvent>(signed)
    }
}
