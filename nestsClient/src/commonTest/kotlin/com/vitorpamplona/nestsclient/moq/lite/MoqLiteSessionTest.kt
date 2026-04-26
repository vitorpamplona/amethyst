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
package com.vitorpamplona.nestsclient.moq.lite

import com.vitorpamplona.nestsclient.transport.FakeBidiStream
import com.vitorpamplona.nestsclient.transport.FakeWebTransport
import com.vitorpamplona.quic.Varint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Drives a [MoqLiteSession] from the listener side against a fake peer
 * that plays the relay role. Validates:
 *
 *   - subscribe() writes the right ControlType + body, blocks until the
 *     peer's SubscribeOk arrives, then yields a SubscribeHandle whose
 *     frames flow surfaces every incoming group.
 *   - subscribe() throws MoqLiteSubscribeException on a Drop response.
 *   - announce() writes the AnnouncePlease and surfaces every Announce
 *     update the peer streams back.
 *   - Group uni streams are demuxed by subscribeId — frames for sub A
 *     never leak into sub B.
 *
 * No transport mock magic — these tests use the production codec to
 * encode/decode bytes, so a wire-shape regression on either side fails
 * here.
 */
class MoqLiteSessionTest {
    /** Real-time scope for session pumps. Cancelled in [tearDown] so each
     * test ends with a clean coroutine ledger.
     */
    private val supervisor = SupervisorJob()
    private val pumpScope = CoroutineScope(supervisor)

    @AfterTest
    fun tearDown() =
        runBlocking {
            supervisor.cancelAndJoin()
        }

    @Test
    fun subscribe_writes_request_and_returns_handle_on_ok() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val peerHandlesSubscribe =
                async {
                    val bidi = serverSide.peerOpenedBidiStreams().first()
                    val req = readSubscribeRequest(bidi)
                    assertEquals("speakerPubkey", req.broadcast)
                    assertEquals("audio/data", req.track)
                    assertEquals(MoqLiteSession.DEFAULT_PRIORITY, req.priority)
                    val ok =
                        MoqLiteSubscribeOk(
                            priority = req.priority,
                            ordered = req.ordered,
                            maxLatencyMillis = req.maxLatencyMillis,
                            startGroup = null,
                            endGroup = null,
                        )
                    bidi.write(MoqLiteCodec.encodeSubscribeOk(ok))
                    req
                }

            val handle = session.subscribe("speakerPubkey", "audio/data")
            val request = peerHandlesSubscribe.await()
            assertEquals(request.id, handle.id)
            assertEquals(0L, handle.id, "first subscribe id is 0")

            // Now push one group with two frames from the server side.
            val uni = serverSide.openPeerUniStream()
            uni.write(Varint.encode(MoqLiteDataType.Group.code))
            uni.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId = handle.id, sequence = 7L)))
            uni.write(framePayload(byteArrayOf(0x10, 0x11)))
            uni.write(framePayload(byteArrayOf(0x20, 0x21)))
            uni.finish()

            val frames =
                withTimeout(2_000) {
                    handle.frames.take(2).toList()
                }
            assertEquals(2, frames.size)
            assertEquals(7L, frames[0].groupSequence)
            assertEquals(7L, frames[1].groupSequence)
            assertContentEquals(byteArrayOf(0x10, 0x11), frames[0].payload)
            assertContentEquals(byteArrayOf(0x20, 0x21), frames[1].payload)

            session.close()
        }

    @Test
    fun subscribe_throws_on_drop_response() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val peer =
                async {
                    val bidi = serverSide.peerOpenedBidiStreams().first()
                    readSubscribeRequest(bidi)
                    bidi.write(
                        MoqLiteCodec.encodeSubscribeDrop(
                            MoqLiteSubscribeDrop(errorCode = 4L, reasonPhrase = "no such broadcast"),
                        ),
                    )
                }

            assertFailsWith<MoqLiteSubscribeException> {
                session.subscribe("nope", "audio/data")
            }
            peer.await()
            session.close()
        }

    @Test
    fun announce_streams_relay_updates() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val peer =
                async {
                    val bidi = serverSide.peerOpenedBidiStreams().first()
                    val chunks = bidi.incoming().take(2).toList()
                    val controlByte = MoqLiteFrameBuffer().apply { push(chunks[0]) }.readVarint()
                    assertEquals(MoqLiteControlType.Announce.code, controlByte)
                    val plea =
                        MoqLiteFrameBuffer().apply { push(chunks[1]) }.readSizePrefixed()
                            ?: error("AnnouncePlease chunk did not contain a complete size-prefixed payload")
                    assertEquals("nests/30312:abc:room", MoqLiteCodec.decodeAnnouncePlease(plea).prefix)

                    // Send two Announce updates back.
                    bidi.write(
                        MoqLiteCodec.encodeAnnounce(
                            MoqLiteAnnounce(
                                MoqLiteAnnounceStatus.Active,
                                "speakerOne",
                                hops = 1L,
                            ),
                        ),
                    )
                    bidi.write(
                        MoqLiteCodec.encodeAnnounce(
                            MoqLiteAnnounce(
                                MoqLiteAnnounceStatus.Active,
                                "speakerTwo",
                                hops = 1L,
                            ),
                        ),
                    )
                }

            val announces = session.announce("nests/30312:abc:room")
            val updates = withTimeout(2_000) { announces.updates.take(2).toList() }
            peer.await()

            assertEquals(2, updates.size)
            assertEquals("speakerOne", updates[0].suffix)
            assertEquals(MoqLiteAnnounceStatus.Active, updates[0].status)
            assertEquals("speakerTwo", updates[1].suffix)

            announces.close()
            session.close()
        }

    @Test
    fun groups_are_demuxed_by_subscribeId() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            // Set up two parallel subscriptions — peer accepts both, replies
            // with Ok, then pushes one group per subscription out of order.
            val subAck =
                async {
                    val bidiA = serverSide.peerOpenedBidiStreams().first()
                    readSubscribeRequest(bidiA)
                    bidiA.write(MoqLiteCodec.encodeSubscribeOk(okFor(0L)))
                    bidiA
                }
            val handleA = session.subscribe("speakerA", "audio/data")
            subAck.await()

            val subAck2 =
                async {
                    val bidiB = serverSide.peerOpenedBidiStreams().first()
                    readSubscribeRequest(bidiB)
                    bidiB.write(MoqLiteCodec.encodeSubscribeOk(okFor(1L)))
                    bidiB
                }
            val handleB = session.subscribe("speakerB", "audio/data")
            subAck2.await()

            assertEquals(0L, handleA.id)
            assertEquals(1L, handleB.id)

            // Push one group for A with payload "a", one for B with payload "b".
            val uniB = serverSide.openPeerUniStream()
            uniB.write(Varint.encode(MoqLiteDataType.Group.code))
            uniB.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId = handleB.id, sequence = 0L)))
            uniB.write(framePayload("b".encodeToByteArray()))
            uniB.finish()

            val uniA = serverSide.openPeerUniStream()
            uniA.write(Varint.encode(MoqLiteDataType.Group.code))
            uniA.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId = handleA.id, sequence = 0L)))
            uniA.write(framePayload("a".encodeToByteArray()))
            uniA.finish()

            val fromA = withTimeout(2_000) { handleA.frames.first() }
            val fromB = withTimeout(2_000) { handleB.frames.first() }
            assertContentEquals("a".encodeToByteArray(), fromA.payload)
            assertContentEquals("b".encodeToByteArray(), fromB.payload)

            handleA.unsubscribe()
            handleB.unsubscribe()
            session.close()
        }

    @Test
    fun unsubscribe_FINs_the_subscribe_bidi() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            var peerBidi: FakeBidiStream? = null
            val peer =
                async {
                    val bidi = serverSide.peerOpenedBidiStreams().first()
                    readSubscribeRequest(bidi)
                    bidi.write(MoqLiteCodec.encodeSubscribeOk(okFor(0L)))
                    peerBidi = bidi
                    // Drain whatever the listener writes after Ok — moq-lite
                    // unsubscribe is a FIN, which surfaces here as the
                    // incoming flow completing.
                    bidi.incoming().toList()
                }

            val handle = session.subscribe("speakerX", "audio/data")
            handle.unsubscribe()
            withTimeout(2_000) { peer.await() }
            assertEquals(true, peerBidi != null, "peer received the bidi")

            session.close()
        }

    // ---------- helpers ----------

    /**
     * Read a Subscribe request from the peer side of the bidi. Each
     * `bidi.write` on the session side becomes one channel send (so
     * one chunk on this end), and the session writes ControlType then
     * the encoded body — exactly two chunks. Pull both with a single
     * [take]+[toList] and parse them, no streaming buffer needed.
     */
    private suspend fun readSubscribeRequest(bidi: FakeBidiStream): MoqLiteSubscribe {
        val chunks = bidi.incoming().take(2).toList()
        check(chunks.size == 2) { "expected 2 chunks (control byte + body), got ${chunks.size}" }
        val controlByte = MoqLiteFrameBuffer().apply { push(chunks[0]) }.readVarint()
        assertEquals(MoqLiteControlType.Subscribe.code, controlByte)
        val payload =
            MoqLiteFrameBuffer().apply { push(chunks[1]) }.readSizePrefixed()
                ?: error("subscribe body chunk did not contain a complete size-prefixed payload")
        return MoqLiteCodec.decodeSubscribe(payload)
    }

    private fun framePayload(bytes: ByteArray): ByteArray = Varint.encode(bytes.size.toLong()) + bytes

    private fun okFor(
        @Suppress("UNUSED_PARAMETER") id: Long,
    ) = MoqLiteSubscribeOk(
        priority = MoqLiteSession.DEFAULT_PRIORITY,
        ordered = true,
        maxLatencyMillis = 0L,
        startGroup = null,
        endGroup = null,
    )
}
