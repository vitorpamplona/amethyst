/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.actions

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class NewPostViewModelTest {
    @MockK
    lateinit var accountViewModel: AccountViewModel

    @MockK(relaxed = true)
    lateinit var replyingTo: Note

    private lateinit var newPostViewModelUnderTest: NewPostViewModel

    @Before
    fun setup() {
        mockkObject(LocalCache)
        every { LocalCache.getOrCreateUser(any<HexKey>()) } returns mockk<User>()
        newPostViewModelUnderTest = NewPostViewModel()
        MockKAnnotations.init(this)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test load with mentions`() =
        runTest {
            // Arrange: Setup with non empty mentions
            every { accountViewModel.account } returns mockk<Account>()

            val textNoteEvent = mockk<TextNoteEvent>(relaxed = true)
            every { textNoteEvent.mentions() } returns listOf(PTag("user1"), PTag("user2"))
            every { replyingTo.event } returns textNoteEvent

            every { accountViewModel.userProfile() } returns mockk<User>(relaxed = true)

            // Act: Call load with mentions
            newPostViewModelUnderTest.load(accountViewModel, replyingTo, quote = null, fork = null, version = null, draft = null)

            // Assert
            // Two mentions should call LocalCache.getOrCreateUser twice
            verify(exactly = 2) { LocalCache.getOrCreateUser(any()) }
        }

    @Test
    fun `test load with zero mentions`() =
        runTest {
            // Arrange: Setup Note with zero mentions
            every { accountViewModel.account } returns mockk<Account>()

            val textNoteEvent = mockk<TextNoteEvent>(relaxed = true)
            every { textNoteEvent.mentions() } returns emptyList()
            every { replyingTo.event } returns textNoteEvent

            every { accountViewModel.userProfile() } returns mockk<User>(relaxed = true)

            // Act: Call load with empty mentions
            newPostViewModelUnderTest.load(accountViewModel, replyingTo, quote = null, fork = null, version = null, draft = null)

            // Assert
            // With no mentions LocalCache.getOrCreateUser should not be called
            verify(exactly = 0) { LocalCache.getOrCreateUser(any()) }
        }

    @Test
    fun `test load with empty mentions`() =
        runTest {
            // Arrange: Setup empty mentions
            every { accountViewModel.account } returns mockk<Account>()

            val textNoteEvent = mockk<TextNoteEvent>(relaxed = true)
            every { textNoteEvent.mentions() } returns emptyList()
            every { replyingTo.event } returns textNoteEvent

            every { accountViewModel.userProfile() } returns mockk<User>(relaxed = true)

            // Act: Call load with empty mentions
            newPostViewModelUnderTest.load(accountViewModel, replyingTo, quote = null, fork = null, version = null, draft = null)

            // Assert
            // Verify LocalCache.getOrCreateUser(it) is not called with empty hex, it will crash the app
            verify(exactly = 0) { LocalCache.getOrCreateUser(any()) }
        }
}
