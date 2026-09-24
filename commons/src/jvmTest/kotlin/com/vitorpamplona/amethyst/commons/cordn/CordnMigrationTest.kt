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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnCarriedKeyPackage
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnDeviceTip
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnDocumentSeal
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnGroupDocument
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnHandoffCode
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnLastResortKeyPackage
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnTipEntry
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnTipInventory
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessageCodec
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole handoff, old phone to new phone, without a phone.
 *
 * The cases here are the ones where a migration fails as *plausible data*
 * rather than as an error: a group silently missing, state that decrypts but
 * was written by a stranger, or bytes that are not the ones the tip named.
 */
class CordnMigrationTest {
    private val account = NostrSignerInternal(KeyPair())

    /** Real codec output, so this travels the same bytes the store writes. */
    private val entryOne =
        CordnDeliveredMessageCodec.encode(
            CordnDeliveredMessage(CordnEnvelope.build("aa".repeat(32), 1_757_000_000L, 9, content = "first"), cursor = 1),
        )

    private val entryTwo =
        CordnDeliveredMessageCodec.encode(
            CordnDeliveredMessage(CordnEnvelope.build("aa".repeat(32), 1_757_000_060L, 9, content = "second"), cursor = 2),
        )
    private val relay = RelayUrlNormalizer.normalize("wss://tip.example")

    private val snapshot =
        CordnMigrationSnapshot(
            accountPubKey = account.pubKey,
            groups =
                listOf(
                    group("gid-1", cursor = 7),
                    group("gid-2", cursor = 0, joinedViaRequest = true),
                ),
            lastResortKeyPackage = CordnLastResortKeyPackage("a2s=", "cHJpdg=="),
            keyPackages = listOf(CordnCarriedKeyPackage("cc".repeat(32), "ref-1", "YnVuZGxl")),
        )

    @Test
    fun `a snapshot survives the round trip`() =
        runTest {
            val world = World(this)

            val code = world.old().publish(snapshot, setOf(relay))
            val received = world.new(account).fetch(code)

            assertEquals(snapshot, received)
        }

    @Test
    fun `every group makes it, with its cursor and its flags`() =
        runTest {
            val world = World(this)

            val received = world.new(account).fetch(world.old().publish(snapshot, setOf(relay)))

            assertEquals(listOf("gid-1", "gid-2"), received.groups.map { it.gid })
            assertEquals(7L, received.groups[0].cursor)
            assertTrue(received.groups[1].joinedViaRequest)
        }

    @Test
    fun `the draft, read position and coordinator relays survive`() =
        runTest {
            val world = World(this)

            val received = world.new(account).fetch(world.old().publish(snapshot, setOf(relay)))

            assertEquals("cm9vbQ==", received.groups[0].roomStateBase64)
            assertEquals("ZWNobw==", received.groups[0].echoStateBase64)
            assertEquals(listOf("wss://coord.example"), received.groups[0].coordinatorRelays)
        }

    @Test
    fun `the conversation travels, because nothing else can carry it`() =
        runTest {
            // The one piece of a handoff that has no second source. A group's
            // MLS state can be re-derived from the coordinator's stream and a
            // KeyPackage can be republished, but a cordn message is readable
            // exactly once — at ingest — and the cursor in this very document
            // has already moved past it. A device seeded without the messages
            // arrives holding every group and no conversation, for good.
            val world = World(this)
            val withHistory =
                snapshot.copy(
                    groups = listOf(group("gid-1", cursor = 7, messages = listOf(entryOne, entryTwo))),
                )

            val received = world.new(account).fetch(world.old().publish(withHistory, setOf(relay)))

            assertEquals(listOf(entryOne, entryTwo), received.groups[0].messages)
        }

    @Test
    fun `a group with no history carries no messages field`() =
        runTest {
            // The field is additive, so an empty list must not become an empty
            // array in the document and come back as something other than what
            // went in — the round-trip equality above depends on it.
            val world = World(this)

            val received = world.new(account).fetch(world.old().publish(snapshot, setOf(relay)))

            assertEquals(emptyList<String>(), received.groups[0].messages)
        }

    @Test
    fun `the key packages travel, so a Welcome in flight is not lost`() =
        runTest {
            val world = World(this)

            val received = world.new(account).fetch(world.old().publish(snapshot, setOf(relay)))

            assertEquals(snapshot.keyPackages, received.keyPackages)
            assertEquals(snapshot.lastResortKeyPackage, received.lastResortKeyPackage)
        }

    @Test
    fun `an account that is not the writer cannot read the tip`() =
        runTest {
            // The tip is NIP-44 sealed to the owner, so a different account
            // fails at the decrypt before any signature check.
            val world = World(this)
            val code = world.old().publish(snapshot, setOf(relay))

            val thrown =
                runCatching { world.new(NostrSignerInternal(KeyPair())).fetch(code) }.exceptionOrNull()

            assertTrue(thrown is CordnMigrationException)
        }

    @Test
    fun `a blob that does not match its address is refused`() =
        runTest {
            // The §6 check. Without it a storage server could substitute MLS
            // state of its choosing and the new phone would adopt it.
            val world = World(this)
            val code = world.old().publish(snapshot, setOf(relay))
            world.store.corruptOne()

            val thrown = runCatching { world.new(account).fetch(code) }.exceptionOrNull()

            assertTrue(thrown is CordnMigrationException)
            assertTrue(thrown!!.message!!.contains("does not hash"))
        }

    @Test
    fun `a missing blob fails loudly rather than migrating a subset`() =
        runTest {
            // A partial migration is the worst outcome: the user arrives on the
            // new phone with some conversations and no indication any are gone.
            val world = World(this)
            val code = world.old().publish(snapshot, setOf(relay))
            world.store.dropOne()

            val thrown = runCatching { world.new(account).fetch(code) }.exceptionOrNull()

            assertTrue(thrown is CordnMigrationException)
            assertTrue(thrown!!.message!!.contains("no server served"))
        }

    @Test
    fun `a migration written by another MLS engine is refused with a reason`() =
        runTest {
            // The cordn-web case. §4.2 leaves clientState library-private, so
            // their ClientState is unreadable here — and it arrives at a valid
            // address under a correctly owner-signed tip, so every other check
            // passes and only the format marker catches it. Built by hand
            // because our own publish() can never produce it.
            val world = World(this)
            world.publishForeignTip("ts-mls")

            val thrown =
                runCatching {
                    world.new(account).fetch(
                        CordnHandoffCode(
                            ephemeralPubKey = world.published.last().pubKey,
                            dTag =
                                world.published
                                    .last()
                                    .tags
                                    .first { it[0] == "d" }[1],
                            relays = listOf(relay.url),
                        ),
                    )
                }.exceptionOrNull()

            assertTrue(thrown is CordnMigrationException)
            assertTrue(thrown!!.message!!.contains("different MLS engine"))
        }

    @Test
    fun `a tip whose inner event was signed by someone else is refused`() =
        runTest {
            // Not reachable by an outsider — the outer content is a self-seal,
            // so only the owner can produce something the owner will decrypt.
            // It IS reachable by a bug on the writing side, and adopting MLS
            // state under a credential naming someone else would make every
            // Commit the new phone sent get rejected by the whole group.
            val world = World(this)
            world.publishTipSignedBy(NostrSignerInternal(KeyPair()))

            val thrown = runCatching { world.new(account).fetch(world.lastCode()) }.exceptionOrNull()

            assertTrue(thrown is CordnMigrationException)
            assertTrue(thrown!!.message!!.contains("different account"))
        }

    @Test
    fun `a code with no relays cannot be published`() =
        runTest {
            val world = World(this)

            val thrown = runCatching { world.old().publish(snapshot, emptySet()) }.exceptionOrNull()

            assertTrue(thrown is IllegalArgumentException)
        }

    @Test
    fun `nothing stored means nothing advertised`() =
        runTest {
            // Publishing a tip that names blobs no server holds would hand the
            // user a code that fails on the other phone, after the old one is
            // already locked.
            val world = World(this, acceptUploads = false)

            val thrown = runCatching { world.old().publish(snapshot, setOf(relay)) }.exceptionOrNull()

            assertTrue(thrown is CordnMigrationException)
        }

    @Test
    fun `the code grants no write and names no owner key`() =
        runTest {
            val world = World(this)

            val code = world.old().publish(snapshot, setOf(relay))

            assertTrue(!code.grantsWrite)
            assertTrue(code.ephemeralPubKey != account.pubKey)
        }

    @Test
    fun `the tip event leaks neither the owner nor cordn`() =
        runTest {
            val world = World(this)
            world.old().publish(snapshot, setOf(relay))

            val tip = world.published.single()

            assertTrue(tip.pubKey != account.pubKey)
            assertEquals(CordnDeviceTip.OUTER_KIND, tip.kind)
            assertTrue(!tip.content.contains(account.pubKey))
            assertTrue(!tip.content.contains("gid-1"))
            assertEquals(listOf("d"), tip.tags.map { it[0] })
        }

    private fun group(
        gid: String,
        cursor: Long,
        joinedViaRequest: Boolean = false,
        messages: List<String> = emptyList(),
    ) = CordnMigrationGroup(
        coordinatorPubKey = "cc".repeat(32),
        coordinatorRelays = listOf("wss://coord.example"),
        gid = gid,
        clientStateBase64 = "c3RhdGUt$gid",
        cursor = cursor,
        roomStateBase64 = "cm9vbQ==",
        echoStateBase64 = "ZWNobw==",
        joinedViaRequest = joinedViaRequest,
        messages = messages,
    )

    /** The two phones, one relay and one storage server, in memory. */
    private inner class World(
        scope: CoroutineScope,
        acceptUploads: Boolean = true,
    ) {
        val store = FakeBlobStore(acceptUploads)
        val published = mutableListOf<Event>()
        val client = LoopbackClient(scope, published)

        fun old() = CordnMigration(client, account, store)

        fun new(signer: NostrSignerInternal) = CordnMigration(client, signer, store)

        fun lastCode() =
            CordnHandoffCode(
                ephemeralPubKey = published.last().pubKey,
                dTag = published.last().tags.first { it[0] == "d" }[1],
                relays = listOf(relay.url),
            )

        /** A tip the owner sealed but somebody else signed inside. */
        suspend fun publishTipSignedBy(other: NostrSignerInternal) {
            val dek = CordnDocumentSeal.newKey()
            val inventory =
                CordnTipInventory(
                    groups = emptyList(),
                    meta = null,
                    dekPrivateKey = dek.privKey!!.toHexString(),
                    servers = listOf(FakeBlobStore.SERVER),
                )
            val inner =
                other.sign<Event>(
                    createdAt = TimeUtils.now(),
                    kind = CordnDeviceTip.INNER_KIND,
                    tags = CordnDeviceTip.tags(inventory),
                    content = "",
                )
            val outer =
                NostrSignerInternal(KeyPair()).sign<Event>(
                    createdAt = TimeUtils.now(),
                    kind = CordnDeviceTip.OUTER_KIND,
                    tags = arrayOf(arrayOf("d", "mismatched-d")),
                    content = account.nip44Encrypt(JacksonMapper.toJson(inner), account.pubKey),
                )
            client.publish(outer, setOf(relay))
        }

        /**
         * Publishes a tip a foreign client would have written: a real document
         * at a real address under a real owner signature, whose clientState
         * only its own engine can read.
         */
        suspend fun publishForeignTip(format: String) {
            val dek = CordnDocumentSeal.newKey()
            val document =
                CordnGroupDocument(
                    gid = "gid-theirs",
                    coordinator = "cc".repeat(32),
                    clientState = "dGhlaXJz",
                    cursor = 0,
                    clientStateFormat = format,
                )
            val blob = CordnDocumentSeal.seal(document, dek)
            store.put(blob)

            val inventory =
                CordnTipInventory(
                    groups = listOf(CordnTipEntry(CordnDocumentSeal.address(blob), "gid-theirs")),
                    meta = null,
                    dekPrivateKey = dek.privKey!!.toHexString(),
                    servers = listOf(FakeBlobStore.SERVER),
                )

            val inner =
                account.sign<Event>(
                    createdAt = TimeUtils.now(),
                    kind = CordnDeviceTip.INNER_KIND,
                    tags = CordnDeviceTip.tags(inventory),
                    content = "",
                )
            val ephemeral = KeyPair()
            val outer =
                NostrSignerInternal(ephemeral).sign<Event>(
                    createdAt = TimeUtils.now(),
                    kind = CordnDeviceTip.OUTER_KIND,
                    tags = arrayOf(arrayOf("d", "theirs-d")),
                    content = account.nip44Encrypt(JacksonMapper.toJson(inner), account.pubKey),
                )
            client.publish(outer, setOf(relay))
        }

        /** The DEK the last publish minted, read back out of the tip. */
        suspend fun dek(): KeyPair {
            val inner = account.nip44Decrypt(published.last().content, account.pubKey)
            val parsed =
                CordnDeviceTip.parse(
                    com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
                        .fromJson(inner),
                )
            return KeyPair(privKey = parsed.dekPrivateKey.hexToByteArray())
        }
    }

    private class FakeBlobStore(
        private val accept: Boolean,
    ) : CordnBlobStore {
        val blobs = LinkedHashMap<String, ByteArray>()

        override suspend fun put(blob: ByteArray): List<String> {
            if (!accept) return emptyList()
            blobs[sha256(blob).toHexString()] = blob
            return listOf(SERVER)
        }

        override suspend fun get(
            address: String,
            servers: List<String>,
        ): ByteArray? = blobs[address]

        /** Serve different bytes under the first group's address. */
        fun corruptOne() {
            val key = blobs.keys.first()
            blobs[key] = "not the document".encodeToByteArray()
        }

        fun dropOne() {
            blobs.remove(blobs.keys.first())
        }

        companion object {
            const val SERVER = "https://blobs.example"
        }
    }

    /**
     * One relay, in memory: accepts a publish with an OK and serves matching
     * events back on a REQ.
     */
    private class LoopbackClient(
        private val scope: CoroutineScope,
        private val published: MutableList<Event>,
    ) : INostrClient by EmptyNostrClient() {
        private val listeners = mutableListOf<RelayConnectionListener>()

        override fun addConnectionListener(listener: RelayConnectionListener) {
            listeners += listener
        }

        override fun removeConnectionListener(listener: RelayConnectionListener) {
            listeners -= listener
        }

        override fun publish(
            event: Event,
            relayList: Set<NormalizedRelayUrl>,
        ) {
            published += event
            // publishAndConfirm waits for an OK; a relay that only swallowed
            // the event would look like a timeout.
            val ok = OkMessage.accepted(event.id)
            relayList.forEach { url ->
                scope.launch(Dispatchers.Unconfined) {
                    listeners.toList().forEach { it.onIncomingMessage(FakeRelay(url), "", ok) }
                }
            }
        }

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            val target = listener ?: return
            scope.launch(Dispatchers.Unconfined) {
                filters.forEach { (relay, list) ->
                    published
                        .filter { event -> list.any { it.match(event) } }
                        .forEach { target.onEvent(it, false, relay, null) }
                    target.onEose(relay, null)
                }
            }
        }

        override fun unsubscribe(subId: String) = Unit
    }

    private class FakeRelay(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = true

        override fun disconnect() = Unit

        override fun sendIfConnected(cmd: Command) = Unit

        override fun sendOrConnectAndSync(cmd: Command) = Unit
    }
}
