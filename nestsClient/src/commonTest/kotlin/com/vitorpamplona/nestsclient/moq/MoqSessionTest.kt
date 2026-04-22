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
package com.vitorpamplona.nestsclient.moq

import com.vitorpamplona.nestsclient.transport.FakeWebTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoqSessionTest {
    @Test
    fun client_and_server_complete_setup_handshake_over_fake_transport() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()

            val clientJob =
                async {
                    val session = MoqSession.client(clientSide, backgroundScope)
                    session.setup(listOf(MoqVersion.DRAFT_17))
                    session.selectedVersion
                }

            val serverJob =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val session = MoqSession.server(serverSide, control, backgroundScope)
                    session.setup(
                        supportedVersions = listOf(MoqVersion.DRAFT_17, MoqVersion.DRAFT_11),
                        clientParameters =
                            listOf(SetupParameter(SetupParameter.KEY_MAX_SUBSCRIBE_ID, byteArrayOf(0x10))),
                    )
                    session.selectedVersion
                }

            assertEquals(MoqVersion.DRAFT_17, clientJob.await())
            assertEquals(MoqVersion.DRAFT_17, serverJob.await())
        }

    @Test
    fun server_picks_first_mutually_supported_version_from_its_own_list() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()

            val clientJob =
                async {
                    val session = MoqSession.client(clientSide, backgroundScope)
                    session.setup(listOf(MoqVersion.DRAFT_17, MoqVersion.DRAFT_11))
                    session.selectedVersion
                }

            val serverJob =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val session = MoqSession.server(serverSide, control, backgroundScope)
                    session.setup(supportedVersions = listOf(MoqVersion.DRAFT_11))
                    session.selectedVersion
                }

            assertEquals(MoqVersion.DRAFT_11, clientJob.await())
            assertEquals(MoqVersion.DRAFT_11, serverJob.await())
        }

    @Test
    fun server_rejects_when_no_version_overlap() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()

            val clientJob =
                async {
                    val session = MoqSession.client(clientSide, backgroundScope)
                    runCatching { session.setup(listOf(MoqVersion.DRAFT_17)) }
                }

            val serverJob =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val session = MoqSession.server(serverSide, control, backgroundScope)
                    assertFailsWith<MoqProtocolException> {
                        session.setup(supportedVersions = listOf(MoqVersion.DRAFT_11))
                    }
                }

            serverJob.await()
            val clientOutcome = clientJob.await()
            assert(clientOutcome.isFailure) { "client setup should have failed, got: $clientOutcome" }
        }

    /**
     * End-to-end: client subscribes; a *raw* server peer (no MoqSession on
     * the server side, since that would compete with the test's manual reads
     * for control-stream items) responds with SUBSCRIBE_OK and three
     * OBJECT_DATAGRAMs. The client's flow should yield them in order.
     */
    @Test
    fun subscribe_completes_on_subscribe_ok_then_delivers_datagrams_in_order() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val clientSession = MoqSession.client(clientSide, backgroundScope)

            // Server-side raw peer: handshake + serve one subscription manually.
            val serverWork =
                async {
                    val serverControl = serverSide.peerOpenedBidiStreams().first()
                    // Handshake: read CLIENT_SETUP, write SERVER_SETUP.
                    val clientSetupFrame = serverControl.incoming().first()
                    val clientSetup = MoqCodec.decode(clientSetupFrame)!!.message as ClientSetup
                    val version = clientSetup.supportedVersions.first()
                    serverControl.write(MoqCodec.encode(ServerSetup(selectedVersion = version)))

                    // Wait for SUBSCRIBE, reply with SUBSCRIBE_OK + three datagrams.
                    val sub = MoqCodec.decode(serverControl.incoming().first())!!.message as Subscribe
                    serverControl.write(
                        MoqCodec.encode(
                            SubscribeOk(
                                subscribeId = sub.subscribeId,
                                expiresMs = 60_000,
                                groupOrder = 0,
                                contentExists = false,
                            ),
                        ),
                    )
                    repeat(3) { i ->
                        val obj =
                            MoqObject(
                                trackAlias = sub.trackAlias,
                                groupId = 0,
                                objectId = i.toLong(),
                                publisherPriority = 0x80,
                                payload = byteArrayOf(i.toByte()),
                            )
                        serverSide.sendDatagram(MoqObjectDatagram.encode(obj))
                    }
                    sub
                }

            clientSession.setup(listOf(MoqVersion.DRAFT_17))

            val handle =
                clientSession.subscribe(
                    namespace = TrackNamespace.of("nests", "test-room"),
                    trackName = "speaker-1".encodeToByteArray(),
                )
            val sub = serverWork.await()

            assertEquals(sub.subscribeId, handle.subscribeId)
            assertEquals(sub.trackAlias, handle.trackAlias)
            assertEquals(60_000L, handle.ok.expiresMs)

            val received = handle.objects.take(3).toList()
            assertEquals(listOf(0L, 1L, 2L), received.map { it.objectId })
            assertContentEquals(byteArrayOf(0), received[0].payload)
            assertContentEquals(byteArrayOf(1), received[1].payload)
            assertContentEquals(byteArrayOf(2), received[2].payload)

            clientSession.close()
        }

    @Test
    fun subscribe_throws_MoqProtocolException_when_publisher_replies_with_subscribe_error() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val clientSession = MoqSession.client(clientSide, backgroundScope)

            val rejector =
                async {
                    val serverControl = serverSide.peerOpenedBidiStreams().first()
                    val clientSetup =
                        MoqCodec.decode(serverControl.incoming().first())!!.message as ClientSetup
                    serverControl.write(MoqCodec.encode(ServerSetup(clientSetup.supportedVersions.first())))

                    val sub =
                        MoqCodec.decode(serverControl.incoming().first())!!.message as Subscribe
                    serverControl.write(
                        MoqCodec.encode(
                            SubscribeError(
                                subscribeId = sub.subscribeId,
                                errorCode = 404,
                                reasonPhrase = "track not found",
                                trackAlias = sub.trackAlias,
                            ),
                        ),
                    )
                }

            clientSession.setup(listOf(MoqVersion.DRAFT_17))

            val ex =
                assertFailsWith<MoqProtocolException> {
                    clientSession.subscribe(
                        namespace = TrackNamespace.of("nests", "test-room"),
                        trackName = "missing".encodeToByteArray(),
                    )
                }
            rejector.await()
            assert("404" in ex.message!!) { "error message should mention the code: ${ex.message}" }

            clientSession.close()
        }

    @Test
    fun datagrams_for_unknown_track_alias_are_dropped_silently() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val clientSession = MoqSession.client(clientSide, backgroundScope)

            val handshake =
                async {
                    val serverControl = serverSide.peerOpenedBidiStreams().first()
                    val cs = MoqCodec.decode(serverControl.incoming().first())!!.message as ClientSetup
                    serverControl.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                }
            clientSession.setup(listOf(MoqVersion.DRAFT_17))
            handshake.await()

            // Send a datagram for a track no one subscribed to. The pump should
            // silently drop it; the session stays usable + closes cleanly.
            serverSide.sendDatagram(
                MoqObjectDatagram.encode(
                    MoqObject(
                        trackAlias = 9_999,
                        groupId = 0,
                        objectId = 0,
                        publisherPriority = 0x80,
                        payload = byteArrayOf(0xAA.toByte()),
                    ),
                ),
            )
            testScheduler.runCurrent()
            clientSession.close()
        }

    /**
     * Existing FakeWebTransport bidi-stream test still passes after the
     * receiveAsFlow refactor: write + finish + collect should still surface
     * the chunk and complete cleanly.
     */
    @Test
    fun fake_bidi_stream_finish_completes_the_consumer_flow() =
        runTest {
            val (a, b) = FakeWebTransport.pair()
            val clientStream = a.openBidiStream()
            clientStream.write(byteArrayOf(0xAA.toByte()))
            clientStream.finish()

            val serverStream = b.peerOpenedBidiStreams().first()
            val received = serverStream.incoming().toList()
            assertEquals(1, received.size)
            assertContentEquals(byteArrayOf(0xAA.toByte()), received.single())
        }
}
