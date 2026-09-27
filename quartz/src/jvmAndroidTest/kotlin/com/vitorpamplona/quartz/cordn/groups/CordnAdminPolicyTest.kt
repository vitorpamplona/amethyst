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
package com.vitorpamplona.quartz.cordn.groups

import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.mls.group.MlsGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That `admin_pubkeys` actually gates commits, the way the reference client
 * gates its `addMember` / `removeMember` / `updateGroupMetadata`.
 *
 * The engine calls [CordnGroupPolicy.authorizeCommit] before building a local
 * commit AND before applying an inbound one, so these tests cover both
 * directions at once: a commit this policy refuses to build is the same commit
 * it refuses to apply from someone else. That matters more than the local error
 * — applying a commit the rest of the group rejects forks the epoch.
 */
class CordnAdminPolicyTest {
    private val alice = "a1".repeat(32)
    private val bob = "b2".repeat(32)

    private fun identity(pubKeyHex: String) = CordnCredential.of(pubKeyHex).identity

    /** A group created by [creator] whose metadata names [admins]. */
    private fun group(
        creator: String,
        admins: List<String>,
    ): MlsGroup =
        MlsGroup.create(
            identity = identity(creator),
            policy = CordnGroupPolicy,
            initialExtensions = listOf(CordnGroupMetadata(name = "Admin test", adminPubkeys = admins).toExtension()),
        )

    @Test
    fun anEmptyAdminListLetsAnyMemberRewriteMetadata() {
        val egalitarian = group(creator = alice, admins = emptyList())
        val before = egalitarian.epoch

        // spec/01.md §5.3: empty means egalitarian, permanently — not a
        // bootstrap window that later closes.
        egalitarian.proposeGroupContextExtensions(egalitarian.extensions)
        egalitarian.commit()

        assertEquals(before + 1, egalitarian.epoch)
        assertTrue(CordnGroupPolicy.isLocalAdmin(egalitarian.view()))
    }

    @Test
    fun aNonAdminCannotRewriteMetadata() {
        val group = group(creator = alice, admins = listOf(bob))
        assertFalse(CordnGroupPolicy.isLocalAdmin(group.view()), "alice must not be an admin or this proves nothing")

        group.proposeGroupContextExtensions(group.extensions)
        assertFailsWith<IllegalStateException> { group.commit() }
    }

    @Test
    fun anAdminCan() {
        val group = group(creator = alice, admins = listOf(alice))
        assertTrue(CordnGroupPolicy.isLocalAdmin(group.view()))
        val before = group.epoch

        group.proposeGroupContextExtensions(group.extensions)
        group.commit()

        assertEquals(before + 1, group.epoch)
    }

    /**
     * The regression that matters most: a cordn credential stores the account
     * key as 64 ASCII hex characters, so hexing the credential bytes yields 128
     * characters. An admin check that compared an account pubkey against that
     * would match nothing — and since the gate only fires when a commit needs
     * an admin, the failure would look like "admins can never commit" rather
     * than like a decoding bug.
     */
    @Test
    fun theAdminListIsComparedInTheCredentialsOwnEncoding() {
        val group = group(creator = alice, admins = listOf(alice))

        val credentialHex = group.view().let { it.memberIdentityHex(it.myLeafIndex) }
        assertEquals(128, credentialHex?.length, "a cordn credential hexes to twice the pubkey length")
        assertTrue(credentialHex != alice, "if these were equal this test would be vacuous")

        assertTrue(group.view().let { CordnGroupPolicy.isAdminLeaf(it, it.myLeafIndex) })
    }

    @Test
    fun anUpdateIsNotAnAdminAction() {
        val group = group(creator = alice, admins = listOf(bob))
        val before = group.epoch

        // Only add, remove and group_context_extensions are gated. Keeping the
        // rest open is what stops a non-admin being trapped in a group whose
        // keys they may never rotate.
        group.proposeSigningKeyRotation()
        group.commit()

        assertEquals(before + 1, group.epoch)
    }

    @Test
    fun anEmptyCommitIsNotAnAdminAction() {
        val group = group(creator = alice, admins = listOf(bob))
        val before = group.epoch

        group.commit()

        assertEquals(before + 1, group.epoch)
    }
}
