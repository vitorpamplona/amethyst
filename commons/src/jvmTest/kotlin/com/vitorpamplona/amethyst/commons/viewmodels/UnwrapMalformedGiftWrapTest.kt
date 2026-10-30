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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * A gift wrap or seal whose content does not decrypt to an event (an empty wrap, or a sender's
 * broken JSON) is a message that cannot be shown, not a crash. Opening one from the chat feed
 * (a DM replying to it) used to throw Kite's "expected '{' at offset 0" out of [launchSigner],
 * which killed the app (1.20.0).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnwrapMalformedGiftWrapTest {
    private val me = NostrSignerInternal(KeyPair())
    private val sender = NostrSignerInternal(KeyPair())

    private lateinit var viewModel: BaseAccountViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val account = mockk<Account>(relaxed = true)
        every { account.signer } returns me
        viewModel =
            BaseAccountViewModel(
                account = account,
                settings = mockk(relaxed = true),
                torSettings = mockk(relaxed = true),
                dataSources = mockk(relaxed = true),
                httpClientBuilder = mockk(relaxed = true),
                nip05ClientBuilder = { mockk(relaxed = true) },
                host = mockk(relaxed = true),
                notices = mockk(relaxed = true),
                noticeResolver = mockk(relaxed = true),
            )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun noteOf(event: Event) = Note(event.id).also { it.event = event }

    private fun wrap(content: String) =
        GiftWrapEvent(
            "1".repeat(64),
            sender.pubKey,
            1_790_900_000,
            arrayOf(arrayOf("p", me.pubKey)),
            content,
            "0".repeat(128),
        )

    private fun seal(content: String) =
        SealEvent(
            "2".repeat(64),
            sender.pubKey,
            1_790_900_000,
            emptyArray(),
            content,
            "0".repeat(128),
        )

    private fun assertUnwrapDoesNotFail(event: Event) =
        runBlocking {
            val job = viewModel.unwrapIfNeeded(noteOf(event))
            job.join()
            assertFalse(job.isCancelled, "unwrapping ${event.kind} failed the coroutine")
        }

    @Test
    fun emptyGiftWrap() = assertUnwrapDoesNotFail(wrap(""))

    @Test
    fun giftWrapThatDecryptsToText() = runBlocking { assertUnwrapDoesNotFail(wrap(sender.nip44Encrypt("hello", me.pubKey))) }

    @Test
    fun emptySeal() = assertUnwrapDoesNotFail(seal(""))

    @Test
    fun sealThatDecryptsToText() = runBlocking { assertUnwrapDoesNotFail(seal(sender.nip44Encrypt("hello", me.pubKey))) }
}
