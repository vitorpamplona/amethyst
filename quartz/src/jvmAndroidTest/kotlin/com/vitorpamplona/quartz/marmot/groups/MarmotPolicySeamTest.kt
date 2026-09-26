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
package com.vitorpamplona.quartz.marmot.groups

import com.vitorpamplona.quartz.marmot.appComponents.AdminPolicyV1
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.marmot.appComponents.MarmotGroupState
import com.vitorpamplona.quartz.marmot.appComponents.NostrRoutingV1
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.group.MlsGroupPolicy
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * That MIP-03 enforcement now travels with [MarmotGroupPolicy] rather than
 * with the engine.
 *
 * Every other authorization test asserts that Marmot refuses. This one asserts
 * the other half, which is what the extraction actually changed: the same
 * group, at the same state, accepts the same commit once the policy is gone.
 * If someone re-hardcodes the rules into `MlsGroup` these tests fail, and a
 * cordn or plain RFC 9420 group would be back to inheriting Marmot's rules
 * without asking.
 */
class MarmotPolicySeamTest {
    private val alice = "11".repeat(32).hexToByteArray()
    private val bob = "22".repeat(32).hexToByteArray()

    /** A group created by [creator] whose admin policy names only [admins]. */
    private fun groupAdminedBy(
        creator: ByteArray,
        admins: List<ByteArray>,
    ): MlsGroup {
        val group = MlsGroup.create(creator, policy = MarmotGroupPolicy)
        val dictionary =
            MarmotGroupState.buildDictionary(
                adminPolicy = AdminPolicyV1.of(admins),
                routing = NostrRoutingV1.of(ByteArray(32) { 0x5a }, listOf("wss://relay.example")),
                profile = GroupProfileV1("Seam", ""),
            )
        // Bootstrap: installed before any admin is named, which MIP-01 allows.
        group.proposeGroupContextExtensions(listOf(dictionary.toExtension()))
        group.commit()
        return group
    }

    @Test
    fun marmotsPolicyRefusesANonAdminExtensionChange() {
        val group = groupAdminedBy(creator = alice, admins = listOf(bob))
        assertTrue(!group.isLocalAdmin(), "alice must not be an admin for this to test anything")

        group.proposeGroupContextExtensions(group.extensions)
        assertFailsWith<IllegalStateException>("a non-admin must not be able to rewrite group state") {
            group.commit()
        }
    }

    @Test
    fun theSameCommitIsAcceptedOnceTheGroupCarriesNoPolicy() {
        val marmot = groupAdminedBy(creator = alice, admins = listOf(bob))
        val epochBefore = marmot.epoch

        // Same state, same proposal, no binding AUTHORIZATION rules — but the
        // same extension types still declared. A Marmot group carries types its
        // older leaves do not advertise, and RFC 9420 §13.4 is enforced from
        // leaf capabilities, so dropping the declaration too would fail this
        // commit for a reason that has nothing to do with who may commit.
        val noAuthorizationRules =
            object : MlsGroupPolicy {
                override val knownExtensionTypes = MarmotGroupPolicy.knownExtensionTypes
            }
        val plain = MlsGroup.restore(marmot.saveState(), noAuthorizationRules)
        plain.proposeGroupContextExtensions(plain.extensions)
        plain.commit()

        assertEquals(
            epochBefore + 1,
            plain.epoch,
            "RFC 9420 places no limit on who may commit; only the binding does",
        )
    }

    @Test
    fun marmotsProfileTravelsWithItsPolicy() {
        val plain = MlsGroup.create(alice)
        val marmot = MlsGroup.create(alice, policy = MarmotGroupPolicy)

        assertTrue(
            plain.extensions.none { it.extensionType == MlsGroup.REQUIRED_CAPABILITIES_EXTENSION_TYPE },
            "the engine's own default must require nothing",
        )
        assertTrue(
            marmot.extensions.any { it.extensionType == MlsGroup.REQUIRED_CAPABILITIES_EXTENSION_TYPE },
            "naming MarmotGroupPolicy must still install required_capabilities",
        )
        assertEquals(
            listOf(MarmotCapabilities.MARMOT_GROUP_DATA_EXTENSION_TYPE),
            MarmotGroupPolicy.defaultLeafCapabilities.extensions,
            "and the MIP-era leaf set, which used to be MlsGroup.create's hardcoded default",
        )
    }
}
