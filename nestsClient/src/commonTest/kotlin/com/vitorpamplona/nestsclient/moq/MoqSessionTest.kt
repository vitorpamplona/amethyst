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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoqSessionTest {
    @Test
    fun client_and_server_complete_setup_handshake_over_fake_transport() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()

            val clientJob =
                async {
                    val session = MoqSession.client(clientSide)
                    session.setup(listOf(MoqVersion.DRAFT_17))
                    session.selectedVersion
                }

            val serverJob =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val session = MoqSession.server(serverSide, control)
                    session.setup(
                        supportedVersions = listOf(MoqVersion.DRAFT_17, MoqVersion.DRAFT_11),
                        clientParameters =
                            listOf(
                                SetupParameter(
                                    SetupParameter.KEY_MAX_SUBSCRIBE_ID,
                                    byteArrayOf(0x10),
                                ),
                            ),
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
                    val session = MoqSession.client(clientSide)
                    // Client prefers 17, falls back to 11.
                    session.setup(listOf(MoqVersion.DRAFT_17, MoqVersion.DRAFT_11))
                    session.selectedVersion
                }

            val serverJob =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val session = MoqSession.server(serverSide, control)
                    // Server only speaks 11 → overlap is 11.
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
                    val session = MoqSession.client(clientSide)
                    runCatching { session.setup(listOf(MoqVersion.DRAFT_17)) }
                }

            val serverJob =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val session = MoqSession.server(serverSide, control)
                    assertFailsWith<MoqProtocolException> {
                        session.setup(supportedVersions = listOf(MoqVersion.DRAFT_11))
                    }
                }

            serverJob.await()
            // Client's result: its control stream closes with no reply, which throws.
            // We just verify that some throwable propagated.
            val clientOutcome = clientJob.await()
            assert(clientOutcome.isFailure) { "client setup should have failed, got: $clientOutcome" }
        }
}
