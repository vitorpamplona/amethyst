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
package com.vitorpamplona.nestsclient.audio

import com.vitorpamplona.nestsclient.moq.MoqObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AudioRoomPlayerTest {
    @Test
    fun every_object_payload_is_decoded_and_enqueued_in_order() =
        runTest {
            val decoder = FakeOpusDecoder { byteToShorts(it) }
            val player = FakeAudioPlayer()

            val objects =
                flowOf(
                    moqObject(byteArrayOf(0x01, 0x02)),
                    moqObject(byteArrayOf(0x03)),
                    moqObject(byteArrayOf(0x04, 0x05, 0x06)),
                )

            val sut = AudioRoomPlayer(decoder, player, this)
            sut.play(objects)
            testScheduler.advanceUntilIdle()

            assertTrue(player.started)
            assertContentEquals(
                expected =
                    listOf(
                        byteToShorts(byteArrayOf(0x01, 0x02)),
                        byteToShorts(byteArrayOf(0x03)),
                        byteToShorts(byteArrayOf(0x04, 0x05, 0x06)),
                    ).flatten(),
                actual = player.queued.flatten(),
            )
            sut.stop()
            assertTrue(player.stopped)
            assertTrue(decoder.released)
        }

    @Test
    fun decoder_failure_invokes_onError_but_loop_continues() =
        runTest {
            val errors = mutableListOf<AudioException>()
            val decoder =
                FakeOpusDecoder { bytes ->
                    if (bytes.contentEquals(byteArrayOf(0xFF.toByte()))) {
                        throw IllegalStateException("synthetic decoder error")
                    }
                    byteToShorts(bytes)
                }
            val player = FakeAudioPlayer()
            val objects =
                flowOf(
                    moqObject(byteArrayOf(0x01)),
                    moqObject(byteArrayOf(0xFF.toByte())),
                    moqObject(byteArrayOf(0x02)),
                )

            val sut = AudioRoomPlayer(decoder, player, this)
            sut.play(objects, onError = { errors.add(it) })
            testScheduler.advanceUntilIdle()

            assertEquals(1, errors.size)
            assertEquals(AudioException.Kind.DecoderError, errors.single().kind)
            // The good packets either side of the bad one still made it through.
            assertContentEquals(
                listOf(byteToShorts(byteArrayOf(0x01)), byteToShorts(byteArrayOf(0x02))).flatten(),
                player.queued.flatten(),
            )
            sut.stop()
        }

    @Test
    fun stop_is_idempotent_and_releases_decoder_only_once() =
        runTest {
            val decoder = FakeOpusDecoder { byteToShorts(it) }
            val player = FakeAudioPlayer()
            val sut = AudioRoomPlayer(decoder, player, this)
            sut.play(flowOf())
            testScheduler.advanceUntilIdle()
            sut.stop()
            sut.stop() // second call must not double-release
            assertEquals(1, decoder.releaseCount)
            assertEquals(1, player.stopCount)
        }

    @Test
    fun play_cannot_be_called_twice_on_the_same_instance() =
        runTest {
            val sut =
                AudioRoomPlayer(
                    FakeOpusDecoder { byteToShorts(it) },
                    FakeAudioPlayer(),
                    this,
                )
            sut.play(flowOf())
            assertFailsWith<IllegalStateException> { sut.play(flowOf()) }
            sut.stop()
        }

    @Test
    fun play_after_stop_is_rejected() =
        runTest {
            val sut =
                AudioRoomPlayer(
                    FakeOpusDecoder { byteToShorts(it) },
                    FakeAudioPlayer(),
                    this,
                )
            sut.stop()
            assertFailsWith<IllegalStateException> { sut.play(flowOf()) }
        }

    @Test
    fun decoder_emitting_empty_pcm_does_not_call_player_enqueue() =
        runTest {
            val decoder = FakeOpusDecoder { ShortArray(0) }
            val player = FakeAudioPlayer()
            val sut = AudioRoomPlayer(decoder, player, this)
            sut.play(flowOf(moqObject(byteArrayOf(0x01))))
            testScheduler.advanceUntilIdle()
            assertEquals(0, player.queued.size)
            sut.stop()
        }

    @Test
    fun objects_arriving_after_play_are_streamed_through_the_pipeline() =
        runTest {
            val channel = Channel<MoqObject>(capacity = 8)
            val decoder = FakeOpusDecoder { byteToShorts(it) }
            val player = FakeAudioPlayer()

            val sut = AudioRoomPlayer(decoder, player, this)
            sut.play(channel.receiveAsFlow())
            testScheduler.runCurrent()

            channel.send(moqObject(byteArrayOf(0x10)))
            channel.send(moqObject(byteArrayOf(0x20)))
            testScheduler.advanceUntilIdle()

            assertEquals(2, player.queued.size)
            assertContentEquals(byteToShorts(byteArrayOf(0x10)), player.queued[0])
            assertContentEquals(byteToShorts(byteArrayOf(0x20)), player.queued[1])

            sut.stop()
        }

    // -- helpers -----------------------------------------------------------

    private fun moqObject(payload: ByteArray): MoqObject =
        MoqObject(
            trackAlias = 1,
            groupId = 0,
            objectId = 0,
            publisherPriority = 0x80,
            payload = payload,
        )

    private fun byteToShorts(b: ByteArray): ShortArray = ShortArray(b.size) { b[it].toShort() }

    private class FakeOpusDecoder(
        private val transform: (ByteArray) -> ShortArray,
    ) : OpusDecoder {
        var releaseCount = 0
            private set
        val released: Boolean get() = releaseCount > 0

        override fun decode(opusPacket: ByteArray): ShortArray = transform(opusPacket)

        override fun release() {
            releaseCount++
        }
    }

    private class FakeAudioPlayer : AudioPlayer {
        var started = false
            private set
        var stopCount = 0
            private set
        val stopped: Boolean get() = stopCount > 0
        val queued = mutableListOf<ShortArray>()
        var muted: Boolean = false
            private set

        override fun start() {
            started = true
        }

        override suspend fun enqueue(pcm: ShortArray) {
            queued.add(pcm)
        }

        override fun setMuted(muted: Boolean) {
            this.muted = muted
        }

        override fun stop() {
            stopCount++
        }
    }
}

/** Small helper so test assertions can flatten lists of ShortArrays. */
private fun List<ShortArray>.flatten(): List<Short> = flatMap { sa -> sa.toList() }
