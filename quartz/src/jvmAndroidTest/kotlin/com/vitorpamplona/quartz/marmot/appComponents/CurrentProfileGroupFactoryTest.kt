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
import com.vitorpamplona.quartz.marmot.appComponents.accountIdentityProof.AccountIdentityProofV2
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip01Groups.MlsCiphersuite
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.marmot.mls.components.ComponentsList
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Groups and KeyPackages we build ourselves, checked against the shape the
 * OpenMLS fork produces in `marmot-current-profile.json`.
 *
 * Every previous test read the reference. This one writes, which is the half
 * that decides whether MDK will accept anything we author.
 */
class CurrentProfileGroupFactoryTest {
    @Serializable
    private data class Joiner(
        @SerialName("key_package") val keyPackage: String,
        @SerialName("key_package_dictionary") val keyPackageDictionary: Map<String, String>,
        @SerialName("leaf_dictionary") val leafDictionary: Map<String, String>,
    )

    @Serializable
    private data class GroupStateJson(
        @SerialName("epoch0_group_context_dictionary") val epoch0: Map<String, String>,
    )

    @Serializable
    private data class Vector(
        val joiner: Joiner,
        @SerialName("group_state") val groupState: GroupStateJson,
    )

    private val vector: Vector =
        JsonMapper.jsonInstance.decodeFromString<Vector>(
            TestResourceLoader().loadString("mls/marmot-current-profile.json"),
        )

    private val signer = NostrSignerInternal(KeyPair())

    @Test
    fun ourKeyPackageMatchesTheReferenceShape() =
        runBlocking<Unit> {
            val bundle = CurrentProfileGroupFactory.createKeyPackage(signer)
            val kp = bundle.keyPackage

            assertTrue(kp.verifySignature(), "our KeyPackage must verify")

            // Same leaf dictionary component set as the reference produces.
            val leafDictionary = assertNotNull(AppDataDictionary.fromExtensions(kp.leafNode.extensions))
            assertEquals(
                vector.joiner.leafDictionary.keys
                    .map { it.removePrefix("0x").toInt(16) }
                    .sorted(),
                leafDictionary.componentIds,
            )

            // Last resort lives in the KeyPackage's own dictionary, as a
            // component with empty data — not as an MLS extension type.
            val kpDictionary = assertNotNull(AppDataDictionary.fromExtensions(kp.extensions))
            assertEquals(
                vector.joiner.keyPackageDictionary.keys
                    .map { it.removePrefix("0x").toInt(16) },
                kpDictionary.componentIds,
            )
            assertContentEquals(ByteArray(0), kpDictionary[AppComponentIds.LAST_RESORT_KEY_PACKAGE])

            // Capabilities advertise the draft extension the current profile
            // needs, plus the legacy 0xF2EE group-data extension.
            //
            // The extra entry is deliberate and is NOT drift from the MDK
            // reference. A capability says "this client can handle it", and a
            // group that REQUIRES 0xF2EE refuses to add a leaf that does not
            // advertise it — so without this a current-profile KeyPackage
            // would be un-addable to every legacy group that already exists.
            // Advertising more than a group requires is always acceptable;
            // advertising less is what gets a leaf rejected.
            assertEquals(
                listOf(AppDataDictionary.EXTENSION_TYPE, MarmotGroupData.EXTENSION_ID_INT),
                kp.leafNode.capabilities.extensions,
            )
            assertTrue(
                kp.leafNode.capabilities.proposals
                    .contains(0x0008),
            )
            assertTrue(
                kp.leafNode.capabilities.proposals
                    .contains(0x000a),
            )
        }

    @Test
    fun ourLeafProofValidatesAgainstItsOwnLeaf() =
        runBlocking<Unit> {
            val kp = CurrentProfileGroupFactory.createKeyPackage(signer).keyPackage
            val dictionary = AppDataDictionary.fromExtensions(kp.leafNode.extensions)!!
            val identity = (kp.leafNode.credential as Credential.Basic).identity

            assertEquals(signer.pubKey, identity.toHexKey())
            assertEquals(
                AccountIdentityProofV2.Result.VALID,
                AccountIdentityProofV2.validate(
                    componentData = dictionary[AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2],
                    credentialIdentity = identity,
                    mlsSignatureKey = kp.leafNode.signatureKey,
                    ciphersuite = MlsCiphersuite.DEFAULT,
                ),
                "the proof must bind this leaf's own signature key",
            )

            // Advertised support and carried data are separate requirements.
            val supported = ComponentsList.decode(dictionary[ComponentsList.APP_COMPONENTS_ID]!!)
            assertTrue(supported.contains(AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2))
            assertNotNull(dictionary[ComponentsList.SAFE_AAD_ID], "safe_aad is advertised explicitly")
        }

    @Test
    fun everyKeyPackageGetsAFreshLeafKeyAndProof() =
        runBlocking<Unit> {
            val a = CurrentProfileGroupFactory.createKeyPackage(signer).keyPackage
            val b = CurrentProfileGroupFactory.createKeyPackage(signer).keyPackage
            assertTrue(!a.leafNode.signatureKey.contentEquals(b.leafNode.signatureKey))

            // A proof authorizes ONE leaf key, so it must not carry over.
            val proofA = AppDataDictionary.fromExtensions(a.leafNode.extensions)!![AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2]
            assertEquals(
                AccountIdentityProofV2.Result.BAD_SIGNATURE,
                AccountIdentityProofV2.validate(
                    componentData = proofA,
                    credentialIdentity = signer.pubKey.hexToByteArray(),
                    mlsSignatureKey = b.leafNode.signatureKey,
                    ciphersuite = MlsCiphersuite.DEFAULT,
                ),
            )
        }

    @Test
    fun ourGroupContextMatchesTheReferenceComponentSet() =
        runBlocking<Unit> {
            val group =
                CurrentProfileGroupFactory.createGroup(
                    signer = signer,
                    nostrGroupId = ByteArray(32) { 0x5a },
                    relays = listOf("wss://relay.damus.io", "wss://nos.lol"),
                    profile = GroupProfileV1("Marmot interop", "current-profile fixture"),
                )

            val dictionary = group.appDataDictionary()
            assertEquals(
                vector.groupState.epoch0.keys
                    .map { it.removePrefix("0x").toInt(16) }
                    .sorted(),
                dictionary.componentIds,
                "same GroupContext components as the reference emits",
            )

            val state = group.currentGroupState()
            assertTrue(state.isCurrentProfile)
            assertEquals("Marmot interop", state.profile?.name)
            assertEquals(GroupLifecycleV1.ACTIVE, state.lifecycle)
            assertEquals(listOf(signer.pubKey), state.adminPolicy?.adminHexKeys)
            assertEquals(listOf("wss://nos.lol", "wss://relay.damus.io"), state.routing?.relays)

            // 0x8009 is required but leaf-only: it must NOT appear in the
            // GroupContext dictionary.
            assertTrue(state.requires(AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2))
            assertNull(dictionary[AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2])
        }

    @Test
    fun theCreatorIsTheSoleAdminAndCanGovern() =
        runBlocking<Unit> {
            val group =
                CurrentProfileGroupFactory.createGroup(
                    signer = signer,
                    nostrGroupId = ByteArray(32) { 0x5a },
                    relays = listOf("wss://relay.example"),
                )

            assertEquals(setOf(signer.pubKey), group.currentAdminIdentities())
            assertTrue(group.isLocalAdmin())

            // An admin-gated component change goes through.
            group.proposeAppDataUpdate(
                AppComponentIds.GROUP_PROFILE_V1,
                GroupProfileV1("renamed", "").encode(),
            )
            group.commit()
            assertEquals("renamed", group.currentGroupState().profile?.name)
        }

    @Test
    fun weCanAddAReferenceGeneratedKeyPackageToOurOwnGroup() =
        runBlocking<Unit> {
            // The end-to-end direction that matters for interop: a KeyPackage
            // authored by the OpenMLS fork, added to a group we built.
            val group =
                CurrentProfileGroupFactory.createGroup(
                    signer = signer,
                    nostrGroupId = ByteArray(32) { 0x5a },
                    relays = listOf("wss://relay.example"),
                )

            val message = MlsMessage.decodeTls(TlsReader(vector.joiner.keyPackage.hexToByteArray()))
            val theirs = MlsKeyPackage.decodeTls(TlsReader(message.payload))

            val result = group.addMember(theirs.toTlsBytes())
            assertTrue(result.commitBytes.isNotEmpty())
            assertNotNull(result.welcomeBytes)
            assertEquals(2, group.memberCount)
            assertEquals(1L, group.epoch)
        }
}
