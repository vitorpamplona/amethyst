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
package com.vitorpamplona.quartz.marmot.transport

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.marmot.appComponents.AppComponentIds
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The kind:30443 shape the current profile requires, and the relay-discovery
 * rule that replaced MIP-00's kind:10051 list.
 */
@OptIn(ExperimentalEncodingApi::class)
class CurrentProfileKeyPackageEventTest {
    @Serializable
    private data class Joiner(
        @SerialName("key_package") val keyPackage: String,
    )

    @Serializable
    private data class Vector(
        val joiner: Joiner,
    )

    private val vector: Vector =
        JsonMapper.jsonInstance.decodeFromString<Vector>(
            TestResourceLoader().loadString("mls/marmot-current-profile.json"),
        )

    /** The MDK-shaped KeyPackage, re-wrapped as the base64 a kind:30443 carries. */
    private val keyPackageBase64: String = Base64.encode(vector.joiner.keyPackage.hexToByteArray())

    private val decoded: MlsKeyPackage by lazy {
        val message = MlsMessage.decodeTls(TlsReader(vector.joiner.keyPackage.hexToByteArray()))
        MlsKeyPackage.decodeTls(TlsReader(message.payload))
    }

    private fun currentProfileEvent(): KeyPackageEvent =
        runBlocking {
            val identity = (decoded.leafNode.credential as Credential.Basic).identity
            // The event must be authored by the credential identity, so sign
            // with that exact account key.
            val signer = NostrSignerInternal(KeyPair(privKey = identity.copyOf().also { it[31] = it[31] }))
            signer.sign(
                KeyPackageEvent.buildCurrentProfile(
                    keyPackageBase64 = keyPackageBase64,
                    dTagSlot = "ab".repeat(32),
                    keyPackageRef = "cd".repeat(32),
                    appComponentIds = listOf("0x0001", "0x8001"),
                ),
            )
        }

    @Test
    fun theCurrentProfileTagSetOmitsEncodingAndRelays() {
        val event = currentProfileEvent()

        assertEquals(listOf(KeyPackageEvent.CURRENT_PROFILE_EXTENSION), event.mlsExtensions())
        assertTrue(event.mlsProposals()!!.contains(KeyPackageEvent.APP_DATA_UPDATE_PROPOSAL))
        assertTrue(event.mlsProposals()!!.contains("0x000a"))

        // The current profile forbids both. A receiver decodes each field by
        // the rule that defines it, and KeyPackage events do not repeat the
        // author's relays because discovery is their NIP-65 write set.
        assertNull(event.encoding(), "the encoding tag is forbidden")
        assertNull(event.relays(), "kind 30443 does not repeat relays")

        val components = event.appComponents()!!
        assertTrue(components.contains("0x8009"), "app_components MUST name 0x8009")
        assertEquals(components.sorted(), components, "id-list values are emitted sorted")
        assertTrue(event.isCurrentProfile())
    }

    @Test
    fun theLegacyBuilderStillProducesTheMipEraShape() {
        // Groups already on disk keep working; the two shapes are told apart
        // by the presence of app_components, not by a version tag.
        val legacy =
            runBlocking {
                NostrSignerInternal(KeyPair()).sign(
                    KeyPackageEvent.build(
                        keyPackageBase64 = keyPackageBase64,
                        dTagSlot = "ab".repeat(32),
                        keyPackageRef = "cd".repeat(32),
                        relays = listOf(RelayUrlNormalizer.normalizeOrNull("wss://relay.example")!!),
                    ),
                )
            }
        assertFalse(legacy.isCurrentProfile())
        assertEquals("base64", legacy.encoding())
        assertTrue(legacy.mlsExtensions()!!.contains("0xf2ee"))
        assertTrue(KeyPackageUtils.isValid(legacy), "the MIP-era shape stays valid")
    }

    @Test
    fun aCurrentProfileEventCarryingAnEncodingTagIsRejected() {
        val event =
            runBlocking {
                NostrSignerInternal(KeyPair()).sign(
                    KeyPackageEvent.buildCurrentProfile(
                        keyPackageBase64 = keyPackageBase64,
                        dTagSlot = "ab".repeat(32),
                        keyPackageRef = "cd".repeat(32),
                        appComponentIds = listOf("0x0001"),
                    ) {
                        // Smuggle in the forbidden tag.
                        addUnique(arrayOf("encoding", "base64"))
                    },
                )
            }
        assertFalse(KeyPackageUtils.isValid(event))
    }

    @Test
    fun theKeyPackageLifetimeBoundIsEnforced() {
        val lifetime = decoded.leafNode.lifetime!!
        val midpoint = (lifetime.notBefore + lifetime.notAfter) / 2

        assertTrue(KeyPackageUtils.hasValidLifetime(decoded, midpoint))
        assertFalse(KeyPackageUtils.hasValidLifetime(decoded, lifetime.notBefore - 1))
        assertFalse(KeyPackageUtils.hasValidLifetime(decoded, lifetime.notAfter + 1))
        assertTrue(
            lifetime.notAfter - lifetime.notBefore <= KeyPackageUtils.MAX_LIFETIME_SECONDS,
            "the reference generator stays inside the 84-day + 1h bound",
        )
    }

    @Test
    fun theEmbeddedProofIsValidatedNotJustAdvertised() {
        // The app_components TAG is only an advertisement; a producer can
        // write anything there. The decoded LeafNode is what decides.
        val identity = (decoded.leafNode.credential as Credential.Basic).identity
        assertTrue(KeyPackageUtils.hasValidAccountIdentityProof(decoded, identity))
        assertFalse(
            KeyPackageUtils.hasValidAccountIdentityProof(decoded, ByteArray(32) { 0x01 }),
            "a proof must not validate against a different account",
        )
    }

    @Test
    fun keyPackageDiscoveryUsesTheNip65WriteSet() {
        val outbox = setOf(RelayUrlNormalizer.normalizeOrNull("wss://outbox.example")!!)
        val legacy = setOf(RelayUrlNormalizer.normalizeOrNull("wss://legacy-10051.example")!!)

        // Publishing must reach the NIP-65 write set: a conformant peer looks
        // there and nowhere else. The legacy list is unioned in, never
        // substituted for it.
        assertTrue(KeyPackageFetcher.publishRelaysFor(myOutbox = outbox).containsAll(outbox))
        val both = KeyPackageFetcher.publishRelaysFor(myOutbox = outbox, legacyKeyPackageRelayList = legacy)
        assertTrue(both.containsAll(outbox))
        assertTrue(both.containsAll(legacy))

        val fetch =
            KeyPackageFetcher.fetchRelaysFor(
                targetOutbox = outbox,
                myOutbox = emptySet(),
                targetKeyPackageRelays = legacy,
            )
        assertTrue(fetch.containsAll(outbox))
    }

    @Test
    fun componentIdsRenderAsFourLowercaseHexDigits() {
        // The id-list tag values are compared as exact strings, so the
        // rendering is part of the wire format.
        assertEquals("0x0001", AppComponentIds.toHex(AppComponentIds.APP_COMPONENTS))
        assertEquals("0x8009", AppComponentIds.toHex(AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2))
        assertEquals("0x800c", AppComponentIds.toHex(AppComponentIds.GROUP_LIFECYCLE_V1))
    }
}
