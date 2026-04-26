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
import kotlin.test.assertIs

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
            val uni = serverSide.openUniStream()
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
            val uniB = serverSide.openUniStream()
            uniB.write(Varint.encode(MoqLiteDataType.Group.code))
            uniB.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId = handleB.id, sequence = 0L)))
            uniB.write(framePayload("b".encodeToByteArray()))
            uniB.finish()

            val uniA = serverSide.openUniStream()
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
    fun publisher_replies_to_announcePlease_with_active_announce() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey")

            // Relay (serverSide) opens an Announce bidi to us with
            // AnnouncePlease(prefix="").
            val relayBidi = serverSide.openBidiStream()
            relayBidi.write(Varint.encode(MoqLiteControlType.Announce.code))
            relayBidi.write(MoqLiteCodec.encodeAnnouncePlease(MoqLiteAnnouncePlease(prefix = "")))

            // We reply on the same bidi with Announce(active=true,
            // suffix="speakerPubkey").
            val resp = withTimeout(2_000) { relayBidi.incoming().first() }
            val announce = MoqLiteCodec.decodeAnnounce(MoqLiteFrameBuffer().apply { push(resp) }.readSizePrefixed()!!)
            assertEquals(MoqLiteAnnounceStatus.Active, announce.status)
            assertEquals("speakerPubkey", announce.suffix)

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_acks_subscribe_and_pushes_group_data_on_uni_stream() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey")

            // Step 1: relay opens Subscribe bidi.
            val subBidi = serverSide.openBidiStream()
            subBidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 7L,
                        broadcast = "speakerPubkey",
                        track = "audio/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )

            // Step 2: we reply SubscribeOk.
            val ackChunk = withTimeout(2_000) { subBidi.incoming().first() }
            val resp =
                MoqLiteCodec.decodeSubscribeResponse(
                    MoqLiteFrameBuffer().apply { push(ackChunk) }.readSizePrefixed()!!,
                )
            assertIs<MoqLiteCodec.SubscribeResponse.Ok>(resp)

            // Step 3: publisher pushes one frame, which opens a uni
            // stream with DataType=Group + group header + frame.
            assertEquals(true, publisher.send("opus-1".encodeToByteArray()))
            publisher.endGroup()

            // The uni stream surfaces on the relay side via incomingUniStreams.
            val relayUni = withTimeout(2_000) { serverSide.incomingUniStreams().first() }
            val uniChunks = relayUni.incoming().toList()
            // Concatenate all chunks then parse: type + group header +
            // first frame. The buffer reader handles arbitrary chunk
            // boundaries.
            val buf = MoqLiteFrameBuffer()
            uniChunks.forEach { buf.push(it) }
            assertEquals(MoqLiteDataType.Group.code, buf.readVarint(), "uni stream starts with Group type byte")
            val header =
                MoqLiteCodec.decodeGroupHeader(
                    buf.readSizePrefixed() ?: error("group header missing"),
                )
            assertEquals(7L, header.subscribeId)
            assertEquals(0L, header.sequence, "first group is sequence 0")
            val firstFrame =
                buf.readSizePrefixed()
                    ?: error("first frame missing")
            assertContentEquals("opus-1".encodeToByteArray(), firstFrame)

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_send_returns_false_when_no_inbound_subscriber() =
        runBlocking {
            val (clientSide, _) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey")
            // No relay-opened Subscribe bidi → no subscribers → send is
            // a silent no-op (returns false), matching the listener
            // semantics where the speaker keeps capturing audio even
            // when nobody is listening.
            assertEquals(false, publisher.send("ignored".encodeToByteArray()))

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_close_emits_ended_announce() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey")

            // Relay opens an announce bidi.
            val relayBidi = serverSide.openBidiStream()
            relayBidi.write(Varint.encode(MoqLiteControlType.Announce.code))
            relayBidi.write(MoqLiteCodec.encodeAnnouncePlease(MoqLiteAnnouncePlease(prefix = "")))
            // Drain the Active announce so the next .first() picks up Ended.
            withTimeout(2_000) { relayBidi.incoming().first() }

            publisher.close()

            val endedChunk = withTimeout(2_000) { relayBidi.incoming().first() }
            val ended =
                MoqLiteCodec.decodeAnnounce(
                    MoqLiteFrameBuffer().apply { push(endedChunk) }.readSizePrefixed()!!,
                )
            assertEquals(MoqLiteAnnounceStatus.Ended, ended.status)
            assertEquals("speakerPubkey", ended.suffix)

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
