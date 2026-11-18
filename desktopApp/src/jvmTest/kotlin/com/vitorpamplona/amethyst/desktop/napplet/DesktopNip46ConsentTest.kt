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

import com.vitorpamplona.amethyst.commons.connectedApps.consent.ConsentQueue
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.InMemoryNip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopNip46ConsentTest {
    private val accountSigner = NostrSignerInternal(KeyPair())
    private val account = accountSigner.pubKey
    private val appSigner = NostrSignerInternal(KeyPair())
    private val app = appSigner.pubKey
    private val coordinate = Nip46PermissionAuthorizer.coordinateFor(account, app)
    private val store = InMemoryNip46ClientStore()

    private fun consent() = DesktopNip46Consent(clientStore = { store })

    private fun connect(name: String? = "Publisher") = BunkerRequestConnect(remoteKey = account, secret = "s", clientMetadata = name?.let { BunkerClientMetadata(name = it) })

    private fun connectQuestion(consent: DesktopNip46Consent) =
        consent.queues.connects.pending.value
            .single()

    /**
     * Waits, in real time, until [queue] holds a question: the prompt's words come from compose
     * resources, read on a thread the test's virtual clock does not drive. The requests these tests
     * wait on run on [Dispatchers.Default] too, so the virtual clock can't skip past their time limits
     * while the test waits.
     */
    private suspend fun <I, A> arrived(queue: ConsentQueue<I, A>): ConsentQueue.Request<I, A> =
        withContext(Dispatchers.Default) {
            withTimeout(10_000) { queue.pending.first { it.isNotEmpty() }.single() }
        }

    @Test
    fun aConnectWaitsForTheUserAndTakesTheTrustLevelPicked() =
        runTest {
            val consent = consent()
            val answer = async { consent.requestConnect(coordinate, app, connect()) }
            runCurrent()

            // Queued under the app's coordinate, so its window shows it, named after the app.
            val question = connectQuestion(consent)
            assertEquals(coordinate, question.app)
            assertEquals("Publisher", question.info.appletTitle)
            assertEquals(account, question.info.accountPubKey)

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
            val consent = consent()
            val answer = async { consent.requestConnect(coordinate, app, connect()) }
            runCurrent()

            connectQuestion(consent).dismiss()
            assertEquals(AppConnectResult.Cancelled, answer.await())
        }

    @Test
    fun aQuestionThatIsNeverShownGivesUpAtTheCeilingNotAtTheOnScreenLimit() =
        runTest {
            val consent = consent()
            val answer = async { consent.requestConnect(coordinate, app, connect()) }

            // The on-screen limit is the window's to apply from when it shows; a question still
            // waiting its turn keeps waiting past it.
            advanceTimeBy(DesktopNip46Consent.ANSWER_WITHIN_MS + 1)
            assertTrue(answer.isActive)

            advanceTimeBy(DesktopNip46Consent.UNSHOWN_LIMIT_MS)
            assertEquals(AppConnectResult.Cancelled, answer.await())
            assertTrue(
                consent.queues.connects.pending.value
                    .isEmpty(),
            )
        }

    @Test
    fun aNostrConnectOfferListsWhatItWouldBeGranted() =
        runTest {
            val consent = consent()
            val answer =
                async(Dispatchers.Default) {
                    consent.requestNostrConnectConsent(coordinate, "Publisher", "https://publisher.example", null, listOf(NostrSignerOp.SignKind(1)))
                }
            val question = arrived(consent.queues.connects)
            assertEquals("https://publisher.example", question.info.domain)
            assertEquals(1, question.info.requestedPermissions.size)

            question.answer(AppConnectResult.Blocked)
            assertEquals(AppConnectResult.Blocked, answer.await())
        }

    @Test
    fun aSignatureAsksUnderTheAppsStoredNameAndReturnsTheGrant() =
        runTest {
            store.store(coordinate, Nip46ClientInfo(name = "Stored name"))
            val consent = consent()
            val sign = BunkerRequestSign(event = EventTemplate<Event>(1L, 3071, emptyArray(), "release"))
            val answer = async(Dispatchers.Default) { consent.requestOp(coordinate, app, NostrSignerOp.SignKind(3071), sign, accountSigner) }
            val question = arrived(consent.queues.signerOps)
            assertEquals(coordinate, question.app)
            assertEquals("Stored name", question.info.appletTitle)
            assertEquals(account, question.info.accountPubKey)

            question.answer(SignerOpGrant.AllowOnce)
            assertEquals(SignerOpGrant.AllowOnce, answer.await())
        }

    @Test
    fun aClosedWindowDeniesTheOperation() =
        runTest {
            val consent = consent()
            val sign = BunkerRequestSign(event = EventTemplate<Event>(1L, 1, emptyArray(), "hi"))
            val answer = async(Dispatchers.Default) { consent.requestOp(coordinate, app, NostrSignerOp.SignKind(1), sign, accountSigner) }
            arrived(consent.queues.signerOps).dismiss()
            assertEquals(SignerOpGrant.DenyOnce, answer.await())
        }

    @Test
    fun aDecryptShowsWhatTheAppWouldRead() =
        runTest {
            val ciphertext = appSigner.nip44Encrypt("the secret plan", account)
            val consent = consent()
            val decrypt = BunkerRequestNip44Decrypt(pubKey = app, ciphertext = ciphertext)
            val answer = async(Dispatchers.Default) { consent.requestOp(coordinate, app, NostrSignerOp.Decrypt, decrypt, accountSigner) }
            val question = arrived(consent.queues.signerOps)
            assertEquals("the secret plan", question.info.contentPreview)
            assertEquals(app, question.info.counterpartyPubKey)

            question.dismiss()
            answer.await()
        }

    @Test
    fun theWindowShowsTheOldestQuestionOfTheAppsItShows() {
        val connects = ConsentQueue<SignerConnectInfo, AppConnectResult>(AppConnectResult.Cancelled)
        val ops = ConsentQueue<SignerConsentInfo, SignerOpGrant>(SignerOpGrant.DenyOnce)
        runTest {
            val first = async { ops.ask(opInfo("A"), "nip46:x:a") }
            runCurrent()
            val second = async { connects.ask(connectInfo("B"), "nip46:x:b") }
            runCurrent()

            assertEquals("nip46:x:a", oldestQuestion({ true }, connects.pending.value, ops.pending.value)?.app)
            assertEquals("nip46:x:b", oldestQuestion({ it == "nip46:x:b" }, connects.pending.value, ops.pending.value)?.app)
            assertNull(oldestQuestion({ false }, connects.pending.value, ops.pending.value))

            first.cancel()
            second.cancel()
        }
    }

    @Test
    fun aQuestionsWindowIsNamedAfterTheAppItCarries() {
        assertEquals("B", consentTitle(connectInfo("B")))
        assertEquals("A", consentTitle(opInfo("A")))
        assertNull(consentTitle(connectInfo(" ")))
        assertNull(consentTitle("anything else"))
    }

    private fun connectInfo(title: String) = SignerConnectInfo(appletTitle = title, coordinate = "c", domain = "d")

    private fun opInfo(title: String) = SignerConsentInfo(appletTitle = title, coordinate = "c", op = NostrSignerOp.SignKind(1), operationSummary = "s", contentPreview = "p")
}
