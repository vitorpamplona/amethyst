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
package com.vitorpamplona.quartz.marmot.mls.components

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.marmot.appComponents.AppComponentIds
import com.vitorpamplona.quartz.marmot.appComponents.accountIdentityProof.AccountIdentityProofV2
import com.vitorpamplona.quartz.marmot.mip01Groups.MlsCiphersuite
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parses the current-profile KeyPackage in `marmot-current-profile.json` all the
 * way down: MLSMessage -> KeyPackage -> LeafNode -> `app_data_dictionary` ->
 * individual components.
 *
 * These bytes were produced by the OpenMLS fork MDK builds on, so this is the
 * test that says our draft-extensions codecs agree with the reference on real
 * traffic rather than on our own reading of the spec. A JSON echo of the
 * component map could not: the generator would just be handing us back what we
 * told it to write.
 */
class AppDataDictionaryInteropTest {
    @Serializable
    private data class Joiner(
        @SerialName("account_pubkey") val accountPubKey: String,
        @SerialName("signature_pub") val signaturePub: String,
        @SerialName("key_package") val keyPackage: String,
        @SerialName("key_package_dictionary") val keyPackageDictionary: Map<String, String>,
        @SerialName("leaf_dictionary") val leafDictionary: Map<String, String>,
    )

    @Serializable
    private data class Vector(
        val joiner: Joiner,
        @SerialName("required_capabilities") val requiredCapabilities: RequiredCapabilities,
    )

    @Serializable
    private data class RequiredCapabilities(
        val extensions: List<String>,
        val proposals: List<String>,
    )

    private val vector: Vector =
        JsonMapper.jsonInstance.decodeFromString<Vector>(
            TestResourceLoader().loadString("mls/marmot-current-profile.json"),
        )

    private val keyPackage: MlsKeyPackage by lazy {
        val message = MlsMessage.decodeTls(TlsReader(vector.joiner.keyPackage.hexToByteArray()))
        assertEquals(WireFormat.KEY_PACKAGE, message.wireFormat)
        MlsKeyPackage.decodeTls(TlsReader(message.payload))
    }

    private fun hexId(id: Int) = AppComponentIds.toHex(id)

    @Test
    fun theReferenceKeyPackageStillParsesAndVerifies() {
        // Guard the layers underneath: if the KeyPackage itself stopped
        // parsing, every dictionary assertion below would be vacuous.
        assertTrue(keyPackage.verifySignature(), "MDK-shaped current-profile KeyPackage must verify")
        assertEquals(1, keyPackage.cipherSuite)
    }

    @Test
    fun theLeafAdvertisesTheDraftExtensionAndProposal() {
        val capabilities = keyPackage.leafNode.capabilities
        assertTrue(
            capabilities.extensions.contains(AppDataDictionary.EXTENSION_TYPE),
            "a current-profile leaf advertises app_data_dictionary (0x0006)",
        )
        assertTrue(
            capabilities.proposals.contains(0x0008),
            "a current-profile leaf advertises app_data_update (0x0008)",
        )
        assertEquals(listOf("0x0006"), vector.requiredCapabilities.extensions)
        assertEquals(listOf("0x0008"), vector.requiredCapabilities.proposals)
    }

    @Test
    fun theLeafDictionaryDecodesToTheGeneratorsComponents() {
        val dictionary = AppDataDictionary.fromExtensions(keyPackage.leafNode.extensions)
        assertNotNull(dictionary, "the member leaf must carry a 0x0006 extension")

        assertEquals(
            vector.joiner.leafDictionary.keys
                .sorted(),
            dictionary.componentIds.map { hexId(it) },
            "decoded component ids must match the generator's, in ascending order",
        )
        for ((idHex, dataHex) in vector.joiner.leafDictionary) {
            val id = idHex.removePrefix("0x").toInt(16)
            assertEquals(dataHex, dictionary[id]?.toHexKey(), "component $idHex data")
        }
    }

    @Test
    fun theLeafDictionaryReEncodesByteIdentically() {
        // The dictionary lives inside the signed LeafNode, so a re-encoding
        // that differs by even one byte would invalidate the signature. This is
        // the assertion that makes the codec safe to write with, not just read.
        val raw = keyPackage.leafNode.extensions.single { it.extensionType == AppDataDictionary.EXTENSION_TYPE }
        val dictionary = AppDataDictionary.decode(raw.extensionData)
        assertContentEquals(raw.extensionData, dictionary.toBytes())
        assertEquals(raw, dictionary.toExtension())
    }

    @Test
    fun theKeyPackageDictionaryCarriesLastResortAsAnEmptyComponent() {
        // Last resort is a KeyPackage-level COMPONENT with empty data, not an
        // MLS extension type — the MIP-era profile used extension 0x000a for
        // this, which is now the self_remove proposal type.
        val dictionary = AppDataDictionary.fromExtensions(keyPackage.extensions)
        assertNotNull(dictionary, "a last-resort KeyPackage carries its own 0x0006 extension")
        assertEquals(listOf(AppComponentIds.LAST_RESORT_KEY_PACKAGE), dictionary.componentIds)
        assertContentEquals(ByteArray(0), dictionary[AppComponentIds.LAST_RESORT_KEY_PACKAGE])
        assertEquals(
            vector.joiner.keyPackageDictionary.keys,
            dictionary.componentIds.map { hexId(it) }.toSet(),
        )

        // It is a separate dictionary from the embedded LeafNode's.
        val leafDictionary = AppDataDictionary.fromExtensions(keyPackage.leafNode.extensions)
        assertNotNull(leafDictionary)
        assertNull(
            leafDictionary[AppComponentIds.LAST_RESORT_KEY_PACKAGE],
            "last resort belongs to the KeyPackage, not to its leaf",
        )
    }

    @Test
    fun theSupportedComponentsListDecodes() {
        val dictionary = AppDataDictionary.fromExtensions(keyPackage.leafNode.extensions)!!
        val supported = ComponentsList.decode(dictionary[ComponentsList.APP_COMPONENTS_ID]!!)

        assertTrue(
            supported.contains(AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2),
            "the leaf must advertise support for 0x8009 alongside carrying its data",
        )
        assertTrue(supported.contains(ComponentsList.APP_COMPONENTS_ID))
        assertEquals(supported.sorted(), supported, "ids arrive sorted")
        assertContentEquals(
            dictionary[ComponentsList.APP_COMPONENTS_ID],
            ComponentsList.encode(supported),
            "re-encoding the supported list must reproduce the signed bytes",
        )
    }

    @Test
    fun safeAadIsAnExplicitEmptyList() {
        val dictionary = AppDataDictionary.fromExtensions(keyPackage.leafNode.extensions)!!
        val safeAad = dictionary[ComponentsList.SAFE_AAD_ID]
        assertNotNull(safeAad, "advertising app_data_dictionary requires understanding safe_aad")
        assertEquals(emptyList(), ComponentsList.decode(safeAad))
        assertContentEquals(safeAad, ComponentsList.encode(emptyList()))
    }

    @Test
    fun theProofComponentPulledFromTheParsedLeafValidates() {
        // Ties Stage 2 to Stage 1: the proof is read out of a real parsed
        // LeafNode rather than out of a JSON convenience field, and checked
        // against that same leaf's credential and signature key.
        val dictionary = AppDataDictionary.fromExtensions(keyPackage.leafNode.extensions)!!
        val leaf = keyPackage.leafNode

        val identity = (leaf.credential as Credential.Basic).identity
        assertEquals(
            vector.joiner.accountPubKey,
            identity.toHexKey(),
            "the BasicCredential identity is the raw 32-byte account key",
        )
        assertEquals(vector.joiner.signaturePub, leaf.signatureKey.toHexKey())

        assertEquals(
            AccountIdentityProofV2.Result.VALID,
            AccountIdentityProofV2.validate(
                componentData = dictionary[AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2],
                credentialIdentity = identity,
                mlsSignatureKey = leaf.signatureKey,
                ciphersuite = MlsCiphersuite.DEFAULT,
            ),
        )
    }

    // --- strictness -----------------------------------------------------------

    @Test
    fun outOfOrderAndDuplicateEntriesAreRejectedOnDecode() {
        val ordered =
            AppDataDictionary(
                listOf(
                    ComponentData(0x0001, byteArrayOf(1)),
                    ComponentData(0x8003, byteArrayOf(2)),
                ),
            )
        assertEquals(listOf(0x0001, 0x8003), ordered.componentIds)

        // Hand-build the same two entries in the wrong order, and then twice
        // over. A decoder that normalized instead of rejecting would accept two
        // encodings of one dictionary, and peers hashing the signed bytes would
        // then disagree about which is canonical.
        fun rawDictionary(vararg entries: ComponentData): ByteArray {
            val inner = TlsWriter()
            entries.forEach { it.encodeTls(inner) }
            val outer = TlsWriter()
            outer.putOpaqueVarInt(inner.toByteArray())
            return outer.toByteArray()
        }

        val a = ComponentData(0x0001, byteArrayOf(1))
        val b = ComponentData(0x8003, byteArrayOf(2))
        assertContentEquals(rawDictionary(a, b), ordered.toBytes(), "sanity: the helper builds the same bytes")

        assertFailsWith<IllegalArgumentException> { AppDataDictionary.decode(rawDictionary(b, a)) }
        assertFailsWith<IllegalArgumentException> { AppDataDictionary.decode(rawDictionary(a, a)) }
    }

    @Test
    fun trailingBytesAreRejected() {
        val dictionary = AppDataDictionary(listOf(ComponentData(0x8001, byteArrayOf(7))))
        assertFailsWith<IllegalArgumentException> { AppDataDictionary.decode(dictionary.toBytes() + 0x00) }
        assertFailsWith<IllegalArgumentException> {
            ComponentsList.decode(ComponentsList.encode(listOf(0x8001)) + 0x00)
        }
    }

    @Test
    fun mutatorsKeepTheDictionarySortedAndUnique() {
        val dictionary =
            AppDataDictionary.EMPTY
                .with(AppComponentIds.GROUP_LIFECYCLE_V1, byteArrayOf(0))
                .with(AppComponentIds.GROUP_PROFILE_V1, byteArrayOf(1))
                .with(ComponentsList.APP_COMPONENTS_ID, ComponentsList.encode(listOf(0x8001)))

        assertEquals(
            listOf(ComponentsList.APP_COMPONENTS_ID, AppComponentIds.GROUP_PROFILE_V1, AppComponentIds.GROUP_LIFECYCLE_V1),
            dictionary.componentIds,
        )

        val replaced = dictionary.with(AppComponentIds.GROUP_PROFILE_V1, byteArrayOf(9))
        assertEquals(dictionary.componentIds, replaced.componentIds, "replacing does not add a second entry")
        assertContentEquals(byteArrayOf(9), replaced[AppComponentIds.GROUP_PROFILE_V1])

        val removed = replaced.without(AppComponentIds.GROUP_PROFILE_V1)
        assertNull(removed[AppComponentIds.GROUP_PROFILE_V1])
        assertEquals(removed.componentIds, removed.without(0x9999).componentIds, "removing an absent id is a no-op")

        // Round-trip whatever we built, so the mutators cannot drift from the codec.
        assertEquals(removed, AppDataDictionary.decode(removed.toBytes()))
    }

    @Test
    fun moreThanOneDictionaryExtensionIsMalformed() {
        val one = AppDataDictionary(listOf(ComponentData(0x8001, byteArrayOf(1)))).toExtension()
        val two = AppDataDictionary(listOf(ComponentData(0x8002, byteArrayOf(2)))).toExtension()
        assertFailsWith<IllegalArgumentException> { AppDataDictionary.fromExtensions(listOf(one, two)) }
        assertNull(AppDataDictionary.fromExtensions(emptyList()))
        assertEquals(AppDataDictionary.EMPTY, AppDataDictionary.fromExtensionsOrEmpty(emptyList()))
    }
}
