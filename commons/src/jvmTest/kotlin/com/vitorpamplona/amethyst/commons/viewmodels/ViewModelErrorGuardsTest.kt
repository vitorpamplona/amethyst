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
import com.vitorpamplona.amethyst.commons.notices.ActionFailed
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.quartz.nip01Core.core.Event
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * [BaseAccountViewModel.launchSigner] and [BaseAccountViewModel.runOnIO] run on viewModelScope,
 * which has no exception handler: anything that escapes them kills the app. Bad input from a
 * relay or another app (Kite's parse errors, a NIP-55 signer's malformed reply) throws
 * [IllegalArgumentException], so both must contain it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelErrorGuardsTest {
    private val notices = Collections.synchronizedList(ArrayList<UserNotice>())

    private val scope = mockAccountViewModelScope()

    private lateinit var viewModel: BaseAccountViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        viewModel =
            BaseAccountViewModel(
                account = mockk<Account>(relaxed = true),
                settings = mockk(relaxed = true),
                torSettings = mockk(relaxed = true),
                dataSources = mockk(relaxed = true),
                httpClientBuilder = mockk(relaxed = true),
                nip05ClientBuilder = { mockk(relaxed = true) },
                host = mockk(relaxed = true),
                notices = { notices.add(it) },
                noticeResolver = mockk(relaxed = true),
                scope = scope,
            )
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun launchSignerReportsUnreadableData() =
        runBlocking<Unit> {
            val job = viewModel.launchSigner { Event.fromJson("not json") }
            job.join()
            assertFalse(job.isCancelled, "the parse error escaped launchSigner")
            assertIs<ActionFailed>(notices.single())
        }

    @Test
    fun runOnIOContainsUnreadableData() =
        runBlocking<Unit> {
            val job = viewModel.runOnIO { Event.fromJson("[\"results\"") }
            job.join()
            assertFalse(job.isCancelled, "the parse error escaped runOnIO")
        }
}
