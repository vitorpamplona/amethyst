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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.amethyst.commons.connectedApps.nip46.InMemoryNip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopNip46ConsentTest {
    private val account = "a".repeat(64)
    private val app = "b".repeat(64)
    private val coordinate = Nip46PermissionAuthorizer.coordinateFor(account, app)

    private fun connect(name: String? = null) = BunkerRequestConnect(remoteKey = account, secret = "s", clientMetadata = name?.let { BunkerClientMetadata(name = it) })

    @Test
    fun aConnectWaitsForTheUserAndTakesTheTrustLevelPicked() =
        runTest {
            val consent = DesktopNip46Consent { InMemoryNip46ClientStore() }
            val answer = async { consent.requestConnect(coordinate, app, connect("Publisher")) }
            runCurrent()

            // Queued under the app's coordinate, so the window shows it and names the app.
            val question =
                consent.queues.connects.pending.value
                    .single()
            assertEquals(coordinate, question.app)
            assertEquals("Publisher", question.info.appletTitle)
            assertEquals("Publisher", consent.titleOf(coordinate))

            question.answer(AppConnectResult.Connected(AppSignerPolicy.FULL_TRUST))
            assertEquals(AppConnectResult.Connected(AppSignerPolicy.FULL_TRUST), answer.await())
            assertTrue(
                consent.queues.connects.pending.value
                    .isEmpty(),
            )
        }

    @Test
    fun aClosedWindowDeclinesTheConnect() =
        runTest {
            val consent = DesktopNip46Consent { InMemoryNip46ClientStore() }
            val answer = async { consent.requestConnect(coordinate, app, connect("Publisher")) }
            runCurrent()

            consent.queues.connects.pending.value
                .single()
                .dismiss()
            assertEquals(AppConnectResult.Cancelled, answer.await())
        }

    @Test
    fun anUnansweredConnectIsDeclinedAfterTheTimeout() =
        runTest {
            val consent = DesktopNip46Consent { InMemoryNip46ClientStore() }
            val answer = async { consent.requestConnect(coordinate, app, connect("Publisher")) }
            advanceTimeBy(DesktopNip46Consent.CONSENT_TIMEOUT_MS + 1)

            assertEquals(AppConnectResult.Cancelled, answer.await())
            // The question leaves the queue, which takes its window down.
            assertTrue(
                consent.queues.connects.pending.value
                    .isEmpty(),
            )
        }
}
