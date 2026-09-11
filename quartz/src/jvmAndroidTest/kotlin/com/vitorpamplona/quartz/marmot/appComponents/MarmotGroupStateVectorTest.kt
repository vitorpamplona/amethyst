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

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.marmot.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.marmot.mls.components.ComponentData
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Decodes the GroupContext component bytes from `marmot-current-profile.json`
 * — produced by the OpenMLS fork MDK builds on — and checks that each Marmot
 * component means what the generator intended and re-encodes byte-identically.
 *
 * The re-encode half is the point. These bytes live in the signed GroupContext
 * and feed the epoch key schedule, so a codec that reads correctly but writes a
 * different encoding of the same value would desynchronize the group the first
 * time we authored a commit.
 */
class MarmotGroupStateVectorTest {
    @Serializable
    private data class GroupStateJson(
        @SerialName("nostr_group_id") val nostrGroupId: String,
        val relays: List<String>,
        val name: String,
        val description: String,
        val admins: List<String>,
        @SerialName("epoch1_group_context_dictionary") val dictionary: Map<String, String>,
    )

    @Serializable
    private data class Vector(
        @SerialName("group_state") val groupState: GroupStateJson,
    )

    private val vector: Vector =
        JsonMapper.jsonInstance.decodeFromString<Vector>(
            TestResourceLoader().loadString("mls/marmot-current-profile.json"),
        )

    private val dictionary: AppDataDictionary by lazy {
        AppDataDictionary(
            vector.groupState.dictionary.map { (idHex, dataHex) ->
                ComponentData(idHex.removePrefix("0x").toInt(16), dataHex.hexToByteArray())
            },
        )
    }

    private val state: MarmotGroupState by lazy { MarmotGroupState.fromDictionary(dictionary) }

    private fun raw(componentId: Int) = assertNotNull(dictionary[componentId], "component missing from the vector")

    @Test
    fun theRequiredComponentListIncludesTheLeafOnlyProof() {
        assertEquals(
            listOf(
                AppComponentIds.GROUP_PROFILE_V1,
                AppComponentIds.ADMIN_POLICY_V1,
                AppComponentIds.NOSTR_ROUTING_V1,
                AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2,
                AppComponentIds.GROUP_LIFECYCLE_V1,
            ).sorted(),
            state.requiredComponents,
        )
        assertTrue(state.isCurrentProfile)
        // 0x8009 is required but leaf-only: required does not imply present.
        assertTrue(state.requires(AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2))
        assertNull(dictionary[AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2])
    }

    @Test
    fun profileDecodesAndReEncodes() {
        val profile = assertNotNull(state.profile)
        assertEquals(vector.groupState.name, profile.name)
        assertEquals(vector.groupState.description, profile.description)
        assertContentEquals(raw(AppComponentIds.GROUP_PROFILE_V1), profile.encode())
    }

    @Test
    fun adminPolicyDecodesAndReEncodes() {
        val policy = assertNotNull(state.adminPolicy)
        assertEquals(vector.groupState.admins, policy.adminHexKeys)
        assertContentEquals(raw(AppComponentIds.ADMIN_POLICY_V1), policy.encode())

        val admin =
            vector.groupState.admins
                .single()
                .hexToByteArray()
        assertTrue(policy.contains(admin))
        // Listed is not enough — the account also has to hold a leaf.
        assertTrue(policy.isActiveAdmin(admin, listOf(admin)))
        assertTrue(!policy.isActiveAdmin(admin, emptyList()))
    }

    @Test
    fun nostrRoutingDecodesAndReEncodes() {
        val routing = assertNotNull(state.routing)
        assertEquals(vector.groupState.nostrGroupId, routing.nostrGroupIdHex)
        assertEquals(vector.groupState.relays.sorted(), routing.relays)
        assertContentEquals(raw(AppComponentIds.NOSTR_ROUTING_V1), routing.encode())
    }

    @Test
    fun lifecycleDecodesAsActive() {
        assertEquals(GroupLifecycleV1.ACTIVE, state.lifecycle)
        assertTrue(!state.isDisbanded)
        assertContentEquals(raw(AppComponentIds.GROUP_LIFECYCLE_V1), GroupLifecycleV1.ACTIVE.encode())
    }

    @Test
    fun optionalComponentsAreAbsentRatherThanDefaulted() {
        // The fixture group carries no image and no retention. Absent is a
        // distinct state from "present and disabled", so these must be null.
        assertNull(state.image)
        assertNull(state.retention)
    }

    @Test
    fun rebuildingTheDictionaryFromComponentsReproducesTheVector() {
        // Round-trips the whole GroupContext dictionary through our builder,
        // which is the path a group we create ourselves would take.
        val rebuilt =
            MarmotGroupState.buildDictionary(
                adminPolicy = assertNotNull(state.adminPolicy),
                routing = assertNotNull(state.routing),
                profile = assertNotNull(state.profile),
                lifecycle = GroupLifecycleV1.ACTIVE,
            )
        assertContentEquals(dictionary.toBytes(), rebuilt.toBytes())
        assertEquals(state, MarmotGroupState.fromDictionary(rebuilt))
    }

    @Test
    fun theAdminKeyIsTheAccountIdentityNotASeparateAuthorizationKey() {
        // Same 32-byte x-only key a member carries as its BasicCredential
        // identity — this is what makes a multi-device account share one entry.
        val policy = assertNotNull(state.adminPolicy)
        assertEquals(1, policy.admins.size)
        assertEquals(32, policy.admins.single().size)
        assertEquals(vector.groupState.admins.single(), policy.admins.single().toHexKey())
    }
}
