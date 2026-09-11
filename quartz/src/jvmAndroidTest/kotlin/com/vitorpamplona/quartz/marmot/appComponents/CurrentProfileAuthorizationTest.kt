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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Closes the gap Stage 1 left open: a current-profile group keeps its admin
 * list in `marmot.group.admin-policy.v1` (`0x8003`), not in MIP-01's
 * `marmot_group_data` (`0xF2EE`), so the authorization gates had nothing to
 * read and let everything through.
 *
 * The tests below drive real groups rather than calling the gates directly,
 * because the gates only matter if the commit path actually reaches them.
 */
class CurrentProfileAuthorizationTest {
    private val aliceAccount = "11".repeat(32).hexToByteArray()
    private val bobAccount = "22".repeat(32).hexToByteArray()

    /** A group whose GroupContext carries a current-profile dictionary naming [admins]. */
    private fun currentProfileGroup(
        creator: ByteArray,
        admins: List<ByteArray>,
    ): MlsGroup {
        val group = MlsGroup.create(creator)
        val dictionary =
            MarmotGroupState.buildDictionary(
                adminPolicy = AdminPolicyV1.of(admins),
                routing = NostrRoutingV1.of(ByteArray(32) { 0x5a }, listOf("wss://relay.example")),
                profile = GroupProfileV1("Interop", ""),
            )
        // Installed through GroupContextExtensions during bootstrap, before any
        // admin is named — the same relaxation the legacy path uses.
        group.proposeGroupContextExtensions(listOf(dictionary.toExtension()))
        group.commit()
        return group
    }

    @Test
    fun theAdminSetIsReadFromTheComponentNotFromMarmotGroupData() {
        val group = currentProfileGroup(aliceAccount, listOf(aliceAccount))

        assertEquals(setOf(aliceAccount.toHexKey()), group.currentAdminIdentities())
        assertTrue(group.isLocalAdmin())
        // Nothing is carrying the MIP-01 extension; the admins came from 0x8003.
        assertEquals(null, group.currentMarmotData())
        assertTrue(group.currentGroupState().isCurrentProfile)
    }

    @Test
    fun aNonAdminCannotCommitAComponentChange() {
        // Alice creates the group and names only herself as admin, then adds
        // Bob. Bob is a member but not an admin.
        val alice = currentProfileGroup(aliceAccount, listOf(aliceAccount))
        val bobBundle = alice.createKeyPackage(bobAccount, ByteArray(0))
        val add = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(add.welcomeBytes!!, bobBundle)

        assertTrue(!bob.isLocalAdmin())
        assertEquals(setOf(aliceAccount.toHexKey()), bob.currentAdminIdentities())

        bob.proposeAppDataUpdate(
            AppComponentIds.GROUP_PROFILE_V1,
            GroupProfileV1("hijacked", "").encode(),
        )
        assertFailsWith<IllegalStateException>("a non-admin must not be able to rewrite group state") {
            bob.commit()
        }
    }

    @Test
    fun anAdminCanCommitAComponentChange() {
        val alice = currentProfileGroup(aliceAccount, listOf(aliceAccount))
        alice.proposeAppDataUpdate(
            AppComponentIds.GROUP_PROFILE_V1,
            GroupProfileV1("renamed", "by an admin").encode(),
        )
        alice.commit()

        assertEquals("renamed", alice.currentGroupState().profile?.name)
    }

    @Test
    fun adminshipCanBeHandedToAnotherMember() {
        val alice = currentProfileGroup(aliceAccount, listOf(aliceAccount))
        val bobBundle = alice.createKeyPackage(bobAccount, ByteArray(0))
        alice.addMember(bobBundle.keyPackage.toTlsBytes())

        // Bob holds a leaf, so promoting him and stepping down is valid: the
        // group still has an active admin afterwards.
        alice.proposeAppDataUpdate(AdminPolicyV1.COMPONENT_ID, AdminPolicyV1.of(listOf(bobAccount)).encode())
        alice.commit()

        assertEquals(setOf(bobAccount.toHexKey()), alice.currentAdminIdentities())
        assertTrue(!alice.isLocalAdmin(), "Alice gave up her own admin rights")
    }

    @Test
    fun anAdminSetWithNoMemberLeafIsRejected() {
        // The admin/leaf coupling rule: every key in `admins` must name an
        // account holding a current leaf. Promoting a non-member would create
        // a phantom admin that activates the instant a matching leaf appears,
        // with no commit anyone else observed.
        val alice = currentProfileGroup(aliceAccount, listOf(aliceAccount))
        alice.proposeAppDataUpdate(AdminPolicyV1.COMPONENT_ID, AdminPolicyV1.of(listOf(bobAccount)).encode())

        assertFailsWith<IllegalStateException> { alice.commit() }
    }

    @Test
    fun removingTheAdminPolicyComponentIsRejectedAsDepletion() {
        // The admin policy is the sole admin authority and must remain present
        // for the group's lifetime, so an AppDataUpdate remove targeting it can
        // never be valid — not even from an admin.
        val alice = currentProfileGroup(aliceAccount, listOf(aliceAccount))
        alice.proposeAppDataRemoval(AdminPolicyV1.COMPONENT_ID)

        assertFailsWith<IllegalStateException> { alice.commit() }
    }

    @Test
    fun aMalformedUnrelatedComponentDoesNotBlockAuthorization() {
        // Authorization reads only 0x8003. A corrupt profile component is a
        // defect worth surfacing where the profile is used, but it must not
        // take the admin check down with it and freeze the group.
        val alice = currentProfileGroup(aliceAccount, listOf(aliceAccount))
        val corrupted =
            AppDataDictionary
                .fromExtensionsOrEmpty(alice.groupContextExtensionsSnapshot())
                .with(AppComponentIds.GROUP_PROFILE_V1, byteArrayOf(0x7f))

        alice.proposeGroupContextExtensions(listOf(corrupted.toExtension()))
        alice.commit()

        assertEquals(setOf(aliceAccount.toHexKey()), alice.currentAdminIdentities())
        assertTrue(alice.isLocalAdmin())
        assertFailsWith<IllegalArgumentException>("the corrupt component still surfaces when read") {
            alice.currentGroupState()
        }
    }
}
