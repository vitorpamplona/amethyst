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
package com.vitorpamplona.quartz.openagents

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.openagents.core.ArtifactRef
import com.vitorpamplona.quartz.openagents.core.DefinitionRef
import com.vitorpamplona.quartz.openagents.core.EventRef
import com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery.BindingContract
import com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery.CapabilityDefinition
import com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery.CapabilityDiscoveryEvent
import com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery.ServiceContract
import com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery.ServiceLane
import com.vitorpamplona.quartz.openagents.nipExtExtensions.listing.ExtensionListingEvent
import com.vitorpamplona.quartz.openagents.nipExtExtensions.release.ExtensionReleaseEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OpenAgents' NIP-CAP capability heads (30180), NIP-EXT listings (30184) and releases (3184).
 *
 * The fixtures are real signed events, read from wss://relay.openagents.com on 2026-10-16, as the
 * openagents CLI publishes them — so these tests pin what Quartz reads against what is actually on
 * the wire, not against a reading of the spec.
 */
class OpenAgentsEventsTest {
    private val capability = """{"id":"f48a9db3e4306797c25b0b96032fecc82b646efbe3d832a6f3de71fd4392cb10","pubkey":"4f71c67c02e9803b605ee5ad7759a3ff8dd212e4db9fcde0db8f5eeed8e3e3ae","created_at":1790568244,"kind":30180,"tags":[["d","cjtest"],["t","oa:cap:v1"],["t","oa:profile:adapter"],["t","oa:transport:nostr-cj"]],"content":"{\"v\":1,\"requires\":[\"oa-x402-v1\"],\"id\":\"4f71c67c02e9803b605ee5ad7759a3ff8dd212e4db9fcde0db8f5eeed8e3e3ae:x402/cjtest\",\"profile\":\"adapter\",\"summary\":\"a paid nostr:openagents:1 resource (x402 exact/lnbtc)\",\"input\":{\"digest\":\"sha256:d3f0fba059ab4261d970244b6e84ec893188daaea06713ea0169239d9db5e40a\",\"size\":44,\"media_type\":\"application/schema+json\"},\"output\":{\"digest\":\"sha256:d3f0fba059ab4261d970244b6e84ec893188daaea06713ea0169239d9db5e40a\",\"size\":44,\"media_type\":\"application/schema+json\"},\"effects\":{\"reads\":[],\"writes\":[],\"network\":[\"remote\"],\"process\":false,\"delegates\":false,\"spend\":false},\"minimum\":{},\"support\":{\"bounds\":{},\"cancellation\":\"unsupported\",\"idempotency\":\"none\",\"evidence\":[]},\"binding_contract\":{\"interface\":\"openagents.x402.native.v1\",\"transport\":\"nostr-cj\",\"operations\":[\"cjtest\"],\"remote\":{\"worker\":\"4f71c67c02e9803b605ee5ad7759a3ff8dd212e4db9fcde0db8f5eeed8e3e3ae\",\"relays\":[\"wss://relay.openagents.com\"]},\"x402\":{\"v\":\"openagents.x402-discovery.v1\",\"protocol\":\"x402-v2\",\"scheme\":\"exact\",\"asset\":\"BTC\",\"method\":\"bolt11\",\"flow\":\"upfront\",\"bindings\":[\"nostr:openagents:1\"],\"receivers\":[{\"network\":\"lnbtc:000000000933ea01ad0ee984209779ba\",\"pay_to\":\"0234ec9c1d85794e8b7f0aae04a1f257118aa267eb1c2ed8f58f7f6619f9a2ee0a\"}],\"merchant\":\"cjtest\",\"recovery\":\"native-record-v1\",\"recovery_contract\":null}}}","sig":"2361732a79751fce86ce5b9e6db7ed94e3ca56836392c49eb89a3b7e7399e896e7b122d95c5b13352c0d202551bcf0724b3ccf2ad531cb517c10bac00c7b1c04"}"""

    private val listing = """{"id":"cb0f3c8c64a4c0b97806758562994e421ceac48e28428a2f5aff9d628a65adcb","pubkey":"097201496ea40ca7adf5cf803910f46ea7d1ae073d52d84fe46bdf9be18b002d","created_at":1790996823,"kind":30184,"tags":[["d","explain-error-check"],["t","oa:ext:listing:v1"]],"content":"{\"v\":1,\"requires\":[],\"type\":\"listing\",\"package\":\"097201496ea40ca7adf5cf803910f46ea7d1ae073d52d84fe46bdf9be18b002d:explain-error-check\",\"state\":\"published\",\"release\":{\"id\":\"20407eadbdc045ad99f9e66e2577b025bb0a8dbcd2350fec63e04017471b36ee\",\"pubkey\":\"097201496ea40ca7adf5cf803910f46ea7d1ae073d52d84fe46bdf9be18b002d\",\"kind\":3184},\"title\":\"Explain this error (payment check)\",\"description\":\"Reads a failing command's output, finds the file and line in your project it points at, and explains the likely cause and a likely fix.\",\"meta\":{\"blobs\":[\"https://relay.openagents.com\"]}}","sig":"d55e9b26b07ad7802c4dbc2c067d49947ff2f72e5d7f584addb636ce8701724b6aa642d59fdb2ef53ebba6e4be1caadff78b6e39d15f207940155343714772fb"}"""

    private val release = """{"id":"20407eadbdc045ad99f9e66e2577b025bb0a8dbcd2350fec63e04017471b36ee","pubkey":"097201496ea40ca7adf5cf803910f46ea7d1ae073d52d84fe46bdf9be18b002d","created_at":1790996823,"kind":3184,"tags":[["t","oa:ext:release:v1"]],"content":"{\"v\":1,\"requires\":[],\"type\":\"release\",\"package\":\"097201496ea40ca7adf5cf803910f46ea7d1ae073d52d84fe46bdf9be18b002d:explain-error-check\",\"version\":\"0.1.1\",\"manifest\":{\"digest\":\"sha256:7111701e793aa7496216716cb2343c4ae2dbdcb1feff55b1c2a21892efe87678\",\"size\":1244,\"media_type\":\"application/json\",\"schema\":\"openagents.package.v1\"}}","sig":"0bf6e7801b314c20e7d8dceef1ebeabcb30049b313e1b9a34a18bfd69a8821d3d829cafe8329f7aba21a1cf73ab864f8af102744d6b8a61ebc49cb26a384fab1"}"""

    private val paidRelease = """{"id":"f59b6399bdf9ec4969eb7f28f541630b4b92b7395356c51716489f92906c275d","pubkey":"097201496ea40ca7adf5cf803910f46ea7d1ae073d52d84fe46bdf9be18b002d","created_at":1790996248,"kind":3184,"tags":[["t","oa:ext:release:v1"]],"content":"{\"v\":1,\"requires\":[],\"type\":\"release\",\"package\":\"097201496ea40ca7adf5cf803910f46ea7d1ae073d52d84fe46bdf9be18b002d:explain-error-check\",\"version\":\"0.1.0\",\"manifest\":{\"digest\":\"sha256:155a1d8e61ac57657223ec145b78ff45c4074fc0222af2f7f70941aeef78158b\",\"size\":1244,\"media_type\":\"application/json\",\"schema\":\"openagents.package.v1\"},\"fee_msat\":10000,\"payout\":\"spark1pgss8je5hl8eprmtgml379sdxewt82pnsvazh7jf8xlu7c5gkkcljp3j2lzey0\"}","sig":"48426b7c2bf66cb569837304747b6c91821933f726fe3192b09e26f89de04824d53f37f6d1c83ee1e90c96db0ac8f0cb3bdd5a70fd0e344138b46c6151ec7664"}"""

    private val root = "097201496ea40ca7adf5cf803910f46ea7d1ae073d52d84fe46bdf9be18b002d"
    private val worker = "4f71c67c02e9803b605ee5ad7759a3ff8dd212e4db9fcde0db8f5eeed8e3e3ae"
    private val manifest =
        ArtifactRef(
            digest = "sha256:7111701e793aa7496216716cb2343c4ae2dbdcb1feff55b1c2a21892efe87678",
            size = 1244,
            mediaType = "application/json",
            schema = "openagents.package.v1",
        )

    @Test
    fun factoryBuildsAllThreeAndTheyAreSearchable() {
        listOf(capability, listing, release, paidRelease).forEach {
            val event = Event.fromJson(it)
            assertTrue(event.verify(), "signature of ${event.id}")
            assertIs<SearchableEvent>(event)
        }
        assertIs<CapabilityDiscoveryEvent>(Event.fromJson(capability))
        assertIs<ExtensionListingEvent>(Event.fromJson(listing))
        assertIs<ExtensionReleaseEvent>(Event.fromJson(release))
        assertTrue(EventFactory.isKnownKind(CapabilityDiscoveryEvent.KIND))
        assertTrue(EventFactory.isKnownKind(ExtensionListingEvent.KIND))
        assertTrue(EventFactory.isKnownKind(ExtensionReleaseEvent.KIND))
    }

    @Test
    fun listingReadsItsBodyAndAgreesWithItsTags() {
        val event = assertIs<ExtensionListingEvent>(Event.fromJson(listing))
        assertEquals("explain-error-check", event.slug())
        assertEquals(root, event.packageId()?.root)
        assertEquals("Explain this error (payment check)", event.title())
        assertTrue(event.isPublished())
        assertEquals(EventRef("20407eadbdc045ad99f9e66e2577b025bb0a8dbcd2350fec63e04017471b36ee", root, 3184), event.release())
        assertTrue(event.isConsistent())
        assertEquals(listOf("20407eadbdc045ad99f9e66e2577b025bb0a8dbcd2350fec63e04017471b36ee"), event.linkedEventIds())
        assertEquals(
            "Explain this error (payment check)\n" +
                "Reads a failing command's output, finds the file and line in your project it points at, and explains the likely cause and a likely fix.\n" +
                "explain-error-check",
            event.indexableContent(),
        )
    }

    @Test
    fun releaseReadsItsBodyAndLinksItsListing() {
        val event = assertIs<ExtensionReleaseEvent>(Event.fromJson(release))
        assertEquals("explain-error-check", event.packageSlug())
        assertEquals("0.1.1", event.version())
        assertEquals(manifest, event.manifest())
        assertEquals(0L, event.feeMsat())
        assertNull(event.payout())
        assertTrue(event.isConsistent())
        assertEquals(listOf("30184:$root:explain-error-check"), event.linkedAddressIds())
        assertEquals("explain-error-check\n0.1.1", event.indexableContent())

        val paid = assertIs<ExtensionReleaseEvent>(Event.fromJson(paidRelease))
        assertEquals(10000L, paid.feeMsat())
        assertEquals("spark1pgss8je5hl8eprmtgml379sdxewt82pnsvazh7jf8xlu7c5gkkcljp3j2lzey0", paid.payout())
        assertTrue(paid.isConsistent())
    }

    @Test
    fun listingAndReleaseOfTheSamePackageLinkUp() {
        val listingEvent = assertIs<ExtensionListingEvent>(Event.fromJson(listing))
        val releaseEvent = assertIs<ExtensionReleaseEvent>(Event.fromJson(release))
        assertEquals(releaseEvent.toEventRef(), listingEvent.release())
        assertEquals(listingEvent.address(), releaseEvent.listingAddress())
    }

    @Test
    fun capabilityReadsItsContractAndHintsItsWorker() {
        val event = assertIs<CapabilityDiscoveryEvent>(Event.fromJson(capability))
        assertFalse(event.isHead())
        assertEquals("x402/cjtest", event.qualifiedId()?.path())
        assertEquals("adapter", event.profile())
        assertEquals(listOf("nostr-cj"), event.transports())
        assertEquals(listOf("cjtest"), event.operations())
        assertEquals("openagents.x402.native.v1", event.interfaceId())
        assertFalse(event.isTest())
        assertTrue(event.isConsistent())
        assertEquals(listOf(worker), event.linkedPubKeys())
        assertEquals(listOf(worker to "wss://relay.openagents.com/"), event.pubKeyHints().map { it.pubkey to it.relay.url })
        assertEquals(
            "a paid nostr:openagents:1 resource (x402 exact/lnbtc)\nx402/cjtest\ncjtest\nopenagents.x402.native.v1",
            event.indexableContent(),
        )
    }

    @Test
    fun visitorsAgreeWithIndexedContent() {
        listOf(capability, listing, release).forEach {
            val event = Event.fromJson(it) as SearchableEvent
            val fields = mutableListOf<String>()
            event.forEachIndexableField { field ->
                if (!field.isNullOrEmpty()) fields.add(field)
                true
            }
            assertEquals(event.indexableContent(), fields.joinToString(event.indexableSeparator()))
        }
    }

    @Test
    fun weightedTiersKeepMarkersOutOfTheHashtagRole() {
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Explain this error (payment check)", "explain-error-check"),
                secondary = listOf("Reads a failing command's output, finds the file and line in your project it points at, and explains the likely cause and a likely fix."),
            ),
            SearchFieldExtractor.extract(Event.fromJson(listing)),
        )
        assertEquals(
            IndexableFields.Tiered(primary = listOf("explain-error-check"), secondary = listOf("0.1.1")),
            SearchFieldExtractor.extract(Event.fromJson(release)),
        )
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("x402/cjtest", "cjtest"),
                secondary = listOf("a paid nostr:openagents:1 resource (x402 exact/lnbtc)"),
                text = "openagents.x402.native.v1",
            ),
            SearchFieldExtractor.extract(Event.fromJson(capability)),
        )
    }

    @Test
    fun malformedBodiesIndexNothingAndThrowNothing() {
        val signer = NostrSignerSync(KeyPair())
        val junk = "not json"
        listOf(CapabilityDiscoveryEvent.KIND, ExtensionListingEvent.KIND, ExtensionReleaseEvent.KIND).forEach { kind ->
            val event = EventFactory.create<Event>("1".repeat(64), signer.pubKey, 1L, kind, emptyArray(), junk, "0".repeat(128))
            assertEquals("", (event as SearchableEvent).indexableContent())
        }
        val wrongShape = EventFactory.create<Event>("1".repeat(64), signer.pubKey, 1L, ExtensionListingEvent.KIND, emptyArray(), "{\"title\":3}", "0".repeat(128))
        assertEquals("", (wrongShape as SearchableEvent).indexableContent())
    }

    @Test
    fun builtListingWritesTheBodyTheCliWrites() {
        val signer = NostrSignerSync(KeyPair())
        val releaseRef = EventRef("2".repeat(64), signer.pubKey, ExtensionReleaseEvent.KIND)
        val signed = signer.sign<ExtensionListingEvent>(ExtensionListingEvent.build(signer.pubKey, "disk-cleanup", releaseRef, "Disk cleanup", "Frees space."))
        assertEquals(
            "{\"v\":1,\"requires\":[],\"type\":\"listing\",\"package\":\"${signer.pubKey}:disk-cleanup\",\"state\":\"published\"," +
                "\"release\":{\"id\":\"${"2".repeat(64)}\",\"pubkey\":\"${signer.pubKey}\",\"kind\":3184}," +
                "\"title\":\"Disk cleanup\",\"description\":\"Frees space.\"}",
            signed.content,
        )
        assertEquals(listOf("d", "t"), signed.tags.map { it[0] }.sorted())
        assertTrue(signed.tags.any { it[0] == "t" && it[1] == "oa:ext:listing:v1" })
        assertTrue(signed.isConsistent())

        // A hidden listing writes its release as an explicit null: the contracts tell omission
        // and null apart, and null is the value they specify there.
        val hidden = signer.sign<ExtensionListingEvent>(ExtensionListingEvent.buildHidden(signer.pubKey, "disk-cleanup", "Disk cleanup", "Frees space."))
        assertTrue(hidden.content.contains("\"state\":\"hidden\",\"release\":null,"), hidden.content)
        assertTrue(hidden.isHidden())
        assertNull(hidden.release())
        assertTrue(hidden.isConsistent())
    }

    @Test
    fun builtReleaseWritesTheBodyTheCliWrites() {
        val signer = NostrSignerSync(KeyPair())
        val signed = signer.sign<ExtensionReleaseEvent>(ExtensionReleaseEvent.build(signer.pubKey, "explain-error-check", "0.1.1", manifest))
        assertEquals(
            "{\"v\":1,\"requires\":[],\"type\":\"release\",\"package\":\"${signer.pubKey}:explain-error-check\",\"version\":\"0.1.1\"," +
                "\"manifest\":{\"digest\":\"${manifest.digest}\",\"size\":1244,\"media_type\":\"application/json\",\"schema\":\"openagents.package.v1\"}}",
            signed.content,
        )
        assertEquals(listOf(listOf("t", "oa:ext:release:v1")), signed.tags.map { it.toList() })
        assertTrue(signed.isConsistent())
    }

    @Test
    fun aReleaseSignedOutsideItsNamespaceHasNoListing() {
        val signer = NostrSignerSync(KeyPair())
        val squatter = signer.sign<ExtensionReleaseEvent>(ExtensionReleaseEvent.build(root, "explain-error-check", "9.9.9", manifest))
        assertFalse(squatter.isConsistent())
        assertNull(squatter.listingAddress())
        assertEquals(emptyList(), squatter.linkedAddressIds())
    }

    @Test
    fun builtCapabilityTagsFollowItsDefinition() {
        val signer = NostrSignerSync(KeyPair())
        val definition = CapabilityDefinition.parse(Event.fromJson(capability).content).copy(id = "${signer.pubKey}:x402/cjtest")
        val signed = signer.sign<CapabilityDiscoveryEvent>(CapabilityDiscoveryEvent.build("cjtest", definition, test = true))
        assertEquals(
            listOf(listOf("d", "cjtest"), listOf("t", "oa:cap:v1"), listOf("t", "oa:profile:adapter"), listOf("t", "oa:transport:nostr-cj"), listOf("t", "oa:test")),
            signed.tags.map { it.toList() },
        )
        assertTrue(signed.isTest())
        assertTrue(signed.isConsistent())
        assertEquals(definition, signed.capability())
    }

    @Test
    fun serviceHeadsTagEveryLaneAndHintTheNostrWorker() {
        val signer = NostrSignerSync(KeyPair())
        val definition =
            CapabilityDefinition(
                id = "${signer.pubKey}:systemone/shared",
                profile = CapabilityDefinition.PROFILE_SERVICE,
                summary = "Shared decision service",
                bindingContract =
                    BindingContract(
                        interfaceId = "openagents.systemone.v1",
                        service =
                            ServiceContract(
                                lanes =
                                    listOf(
                                        ServiceLane(transport = "http", endpoint = "https://gateway.example", call = "/v1/systemone", models = "/v1/models"),
                                        ServiceLane(transport = "nostr-cj", worker = worker, relays = listOf("wss://relay.example", "not a relay"), requestKind = 25910),
                                    ),
                            ),
                    ),
            )
        val signed = signer.sign<CapabilityDiscoveryEvent>(CapabilityDiscoveryEvent.build("shared", definition))
        assertEquals(listOf("http", "nostr-cj"), signed.transports())
        assertTrue(signed.isConsistent())
        assertEquals(listOf(PubKeyHintPair(worker, "wss://relay.example/")), signed.pubKeyHints().map { PubKeyHintPair(it.pubkey, it.relay.url) })
    }

    @Test
    fun aHeadReferencesItsDefinition() {
        val signer = NostrSignerSync(KeyPair())
        val declaration = EventRef("3".repeat(64), signer.pubKey, ExtensionReleaseEvent.KIND)
        val ref = DefinitionRef("${signer.pubKey}:pkg/echo", manifest, declaration)
        val signed = signer.sign<CapabilityDiscoveryEvent>(CapabilityDiscoveryEvent.buildHead("echo", ref, "adapter", listOf("http")))
        assertTrue(signed.isHead())
        assertEquals("adapter", signed.profile())
        assertEquals("pkg/echo", signed.qualifiedId()?.path())
        assertEquals(listOf(declaration.id), signed.linkedEventIds())
        assertTrue(signed.isConsistent())
        assertEquals("pkg/echo", signed.indexableContent())
    }

    private data class PubKeyHintPair(
        val pubkey: String,
        val relay: String,
    )
}
