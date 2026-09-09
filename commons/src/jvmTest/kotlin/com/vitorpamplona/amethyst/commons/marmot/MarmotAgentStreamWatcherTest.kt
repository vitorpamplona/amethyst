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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamPublisher
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRecordV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.InMemoryAgentTextStreamSequenceStore
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.PreviewStatus
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicException
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicStream
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicTransport
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The front end's half of an agent text stream: notice the kind:1200 that
 * arrived in a group, render the live preview it points at, and hand back to
 * the durable kind:9 when it lands.
 *
 * Everything the renderer needs to be honest is decided here — whether the
 * preview may be shown as confirmed, and whether it turned out to be the
 * stream the publisher actually sent.
 */
class MarmotAgentStreamWatcherTest {
    private val nostrGroupId = "c".repeat(64)

    /** A transport whose records the test pushes by hand. */
    private class FakeTransport : MarmotQuicTransport {
        val records = MutableSharedFlow<AgentTextStreamRecordV1>(replay = 32)
        val subscribed = CompletableDeferred<String>()
        var failEveryCandidate = false

        override suspend fun publish(
            candidate: String,
            streamId: ByteArray,
            startEventId: ByteArray,
        ): MarmotQuicStream = error("the watcher never publishes")

        override suspend fun subscribe(
            candidate: String,
            streamId: ByteArray,
            startEventId: ByteArray,
        ): MarmotQuicStream {
            if (failEveryCandidate) {
                throw MarmotQuicException(MarmotQuicException.Kind.HandshakeFailed, "no route to $candidate")
            }
            if (!subscribed.isCompleted) subscribed.complete(candidate)
            return object : MarmotQuicStream {
                override suspend fun send(record: AgentTextStreamRecordV1) = error("read only")

                override fun incoming(): Flow<AgentTextStreamRecordV1> = records

                override suspend fun finish() = Unit

                override suspend fun close() = Unit
            }
        }
    }

    private fun manager() =
        MarmotManager(
            NostrSignerInternal(KeyPair()),
            WatcherStateStore(),
            WatcherMessageStore(),
            WatcherBundleStore(),
        )

    private suspend fun aGroupWithAStream(
        manager: MarmotManager,
        brokers: List<String> = listOf("quic://broker.invalid:4450"),
    ): Pair<String, String> {
        manager.createGroup(
            nostrGroupId,
            MarmotGroupData(nostrGroupId = nostrGroupId, name = "stream group", relays = listOf("wss://relay.invalid")),
        )
        val streamId = "a".repeat(64)
        val start = manager.buildAgentStreamStart(nostrGroupId, streamId, brokers)
        return streamId to start.innerEvent.id
    }

    @Test
    fun aPreviewAppearsAsRecordsArriveAndIsNeverShownAsConfirmed() =
        runBlocking {
            val manager = manager()
            val transport = FakeTransport()
            val (streamId, startEventId) = aGroupWithAStream(manager)
            val watcher = MarmotAgentStreamWatcher(manager, transport, this)

            watcher.watchLatest(nostrGroupId)
            withTimeout(5_000) { transport.subscribed.await() }

            val crypto = manager.agentTextStreamCrypto(nostrGroupId, hex(streamId), hex(startEventId))
            val publisher = AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore())
            transport.records.emit(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "half an ".encodeToByteArray()))
            transport.records.emit(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "answer".encodeToByteArray()))

            val preview = withTimeout(5_000) { watcher.preview.first { it?.text == "half an answer" } }
            assertNotNull(preview)
            assertEquals(streamId, preview.streamId)
            assertEquals(PreviewStatus.LIVE, preview.status)
            assertTrue(
                !preview.isConfirmed,
                "a live preview is provisional — a renderer must be able to tell it apart from durable content",
            )
            watcher.stop()
        }

    @Test
    fun theFinalMessageConfirmsAPreviewThatMatchesIt() =
        runBlocking {
            val manager = manager()
            val transport = FakeTransport()
            val (streamId, startEventId) = aGroupWithAStream(manager)
            val watcher = MarmotAgentStreamWatcher(manager, transport, this)
            watcher.watchLatest(nostrGroupId)
            withTimeout(5_000) { transport.subscribed.await() }

            val crypto = manager.agentTextStreamCrypto(nostrGroupId, hex(streamId), hex(startEventId))
            val publisher = AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore())
            transport.records.emit(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "the answer".encodeToByteArray()))
            withTimeout(5_000) { watcher.preview.first { it?.text == "the answer" } }

            watcher.onFinal(streamId, publisher.transcript.hash.asHex(), publisher.transcript.chunkCount)
            val confirmed = assertNotNull(withTimeout(5_000) { watcher.preview.first { it?.isConfirmed == true } })
            assertEquals("the answer", confirmed.text)
            watcher.stop()
        }

    @Test
    fun aFinalThatDisagreesDiscardsThePreviewInsteadOfShowingIt() =
        runBlocking {
            val manager = manager()
            val transport = FakeTransport()
            val (streamId, startEventId) = aGroupWithAStream(manager)
            val watcher = MarmotAgentStreamWatcher(manager, transport, this)
            watcher.watchLatest(nostrGroupId)
            withTimeout(5_000) { transport.subscribed.await() }

            val crypto = manager.agentTextStreamCrypto(nostrGroupId, hex(streamId), hex(startEventId))
            val publisher = AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore())
            transport.records.emit(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "tampered".encodeToByteArray()))
            withTimeout(5_000) { watcher.preview.first { it?.text == "tampered" } }

            // A transcript that does not match means records were dropped,
            // reordered or injected even though each one opened. The durable
            // kind:9 is the answer; the preview must go.
            watcher.onFinal(streamId, "0".repeat(64), 1)
            withTimeout(5_000) { watcher.preview.first { it == null } }
            watcher.stop()
        }

    @Test
    fun aFinalAlreadyInTheLogSettlesThePreviewWithoutTheUiSayingSo() =
        runBlocking {
            val manager = manager()
            val transport = FakeTransport()
            val (streamId, startEventId) = aGroupWithAStream(manager)
            val watcher = MarmotAgentStreamWatcher(manager, transport, this)
            watcher.watchLatest(nostrGroupId)
            withTimeout(5_000) { transport.subscribed.await() }

            val crypto = manager.agentTextStreamCrypto(nostrGroupId, hex(streamId), hex(startEventId))
            val publisher = AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore())
            transport.records.emit(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "done".encodeToByteArray()))
            withTimeout(5_000) { watcher.preview.first { it?.text == "done" } }

            // The durable message lands in the group log the ordinary way. A
            // front end only reports "the feed moved"; the watcher does the
            // rest.
            manager.buildAgentStreamFinal(
                nostrGroupId,
                streamId,
                publisher.transcript.hash.asHex(),
                publisher.transcript.chunkCount,
                "done",
            )
            watcher.watchLatest(nostrGroupId)

            val confirmed = assertNotNull(withTimeout(5_000) { watcher.preview.first { it?.isConfirmed == true } })
            assertEquals("done", confirmed.text)
            watcher.stop()
        }

    @Test
    fun aGroupWithNoBrokerCandidateShowsNoPreviewAtAll() =
        runBlocking {
            val manager = manager()
            val transport = FakeTransport()
            aGroupWithAStream(manager, brokers = emptyList())
            val watcher = MarmotAgentStreamWatcher(manager, transport, this)

            watcher.watchLatest(nostrGroupId)
            // Zero candidates is valid: the preview is simply unavailable and
            // every member still gets the final message.
            assertNull(watcher.preview.value)
            watcher.stop()
        }

    @Test
    fun anUnreachableBrokerLeavesTheGroupUsableWithoutAPreview() =
        runBlocking {
            val manager = manager()
            val transport = FakeTransport().also { it.failEveryCandidate = true }
            aGroupWithAStream(manager)
            val watcher = MarmotAgentStreamWatcher(manager, transport, this)

            watcher.watchLatest(nostrGroupId)
            assertNull(
                watcher.preview.value,
                "a candidate that will not connect is skipped, not fatal",
            )
            watcher.stop()
        }

    @Test
    fun aPlatformWithoutQuicSimplyNeverPreviews() =
        runBlocking {
            val manager = manager()
            aGroupWithAStream(manager)
            val watcher = MarmotAgentStreamWatcher(manager, transport = null, scope = this)

            watcher.watchLatest(nostrGroupId)
            assertNull(watcher.preview.value)
            watcher.stop()
        }

    private fun hex(s: String) = ByteArray(s.length / 2) { ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte() }

    private fun ByteArray.asHex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

private class WatcherStateStore : MlsGroupStateStore {
    private val states = mutableMapOf<String, ByteArray>()
    private val retained = mutableMapOf<String, List<ByteArray>>()

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        states[nostrGroupId] = state
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = states[nostrGroupId]

    override suspend fun delete(nostrGroupId: String) {
        states.remove(nostrGroupId)
        retained.remove(nostrGroupId)
    }

    override suspend fun listGroups(): List<String> = states.keys.toList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        retained[nostrGroupId] = retainedSecrets
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retained[nostrGroupId] ?: emptyList()
}

private class WatcherMessageStore : MarmotMessageStore {
    private val messages = mutableMapOf<String, MutableList<String>>()
    private val epochs = mutableMapOf<String, MutableMap<String, Long>>()

    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) {
        val log = messages.getOrPut(nostrGroupId) { mutableListOf() }
        if (innerEventJson !in log) log.add(innerEventJson)
    }

    override suspend fun loadMessages(nostrGroupId: String): List<String> = messages[nostrGroupId]?.toList() ?: emptyList()

    override suspend fun delete(nostrGroupId: String) {
        messages.remove(nostrGroupId)
        epochs.remove(nostrGroupId)
    }

    override suspend fun recordEpoch(
        nostrGroupId: String,
        innerEventId: String,
        epoch: Long,
    ) {
        epochs.getOrPut(nostrGroupId) { mutableMapOf() }[innerEventId] = epoch
    }

    override suspend fun loadEpochs(nostrGroupId: String): Map<String, Long> = epochs[nostrGroupId]?.toMap() ?: emptyMap()
}

private class WatcherBundleStore : KeyPackageBundleStore {
    private var snapshot: ByteArray? = null

    override suspend fun save(snapshot: ByteArray) {
        this.snapshot = snapshot
    }

    override suspend fun load(): ByteArray? = snapshot

    override suspend fun delete() {
        snapshot = null
    }
}
