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
package com.vitorpamplona.amethyst.commons.ui.feeds

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the "key unchanged" guard in
 * [FeedContentState.checkKeysInvalidateDataAndSendToTop].
 *
 * The feed key only carries the top-nav *selection*, which flips synchronously when the user
 * picks a list in the spinner. What that selection resolves to — the author set — lands later,
 * because a NIP-51 people list has to be decrypted first. The screen calls
 * [FeedContentState.checkKeysInvalidateDataAndSendToTop] when the resolved filter changes, so
 * that call must refresh even though the key it was given has not moved since the rebuild that
 * ran mid-resolution.
 */
class FeedContentStateRefreshTest {
    companion object {
        private const val TIMEOUT_MS = 5000L

        /** Comfortably past the 250ms [FeedContentState] bundles its refreshes with. */
        private const val BUNDLE_WINDOW_MS = 500L
    }

    private class TestFilter : IFeedFilter<Note> {
        var key: String = "all-follows"
        var notes: List<Note> = emptyList()

        override fun loadTop(): List<Note> = notes

        override fun feed(): List<Note> = notes

        override fun feedKey(): Any = key

        override fun limit(): Int = 100
    }

    private object NoCache : ICacheProvider {
        override val relayHints = HintIndexer()

        override fun getAnyChannel(note: Note): Channel? = null

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = null

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = null

        override fun getOrCreateAddressableNote(address: Address): AddressableNote = throw NotImplementedError()

        override fun getEventStream(): ICacheEventStream = throw NotImplementedError()

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }

    /**
     * [com.vitorpamplona.amethyst.commons.service.BasicBundledUpdate] holds a throttle window
     * open after each run and folds a call that lands inside it into the *previous* block, so
     * wait the window out before exercising the call under test.
     */
    private fun settleBundler() = runBlocking { delay(BUNDLE_WINDOW_MS) }

    private fun awaitFeed(
        state: FeedContentState,
        expected: List<Note>,
    ): List<Note> =
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) {
                while (state.visibleNotes() != expected) delay(10)
            }
            state.visibleNotes()
        }

    @Test
    fun `late resolution refreshes even though the feed key has not moved`() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val publicMember = Note("aa")
            val privateMember = Note("bb")

            val filter = TestFilter()
            val state = FeedContentState(filter, scope, NoCache)

            // The spinner flips to a people list: the key moves right away, but the list is
            // still being decrypted, so only its public members are in the resolved filter.
            // A note bundle arriving in that window rebuilds the feed and stamps the new key.
            filter.key = "people-list"
            filter.notes = listOf(publicMember)
            state.invalidateData()
            assertEquals(listOf(publicMember), awaitFeed(state, listOf(publicMember)))
            settleBundler()

            // Decryption lands: same selection, more authors. This is the only refresh that
            // carries the private members, so it must not be skipped as a no-op.
            filter.notes = listOf(publicMember, privateMember)
            state.checkKeysInvalidateDataAndSendToTop()
            assertEquals(
                listOf(publicMember, privateMember),
                awaitFeed(state, listOf(publicMember, privateMember)),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a changed selection still scrolls the reader back to the top`() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val note = Note("aa")
            val filter = TestFilter()
            val state = FeedContentState(filter, scope, NoCache)

            filter.notes = listOf(note)
            state.invalidateData()
            assertEquals(listOf(note), awaitFeed(state, listOf(note)))
            settleBundler()

            val scrollsBefore = state.scrollToTop.value
            filter.key = "people-list"
            state.checkKeysInvalidateDataAndSendToTop()

            runBlocking {
                withTimeoutOrNull(TIMEOUT_MS) {
                    while (state.scrollToTop.value == scrollsBefore) delay(10)
                }
            }
            assertEquals(scrollsBefore + 1, state.scrollToTop.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a refresh for the same selection leaves the scroll position alone`() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val note = Note("aa")
            val filter = TestFilter()
            val state = FeedContentState(filter, scope, NoCache)

            filter.notes = listOf(note)
            state.invalidateData()
            assertEquals(listOf(note), awaitFeed(state, listOf(note)))
            settleBundler()

            val scrollsBefore = state.scrollToTop.value
            filter.notes = listOf(note, Note("bb"))
            state.checkKeysInvalidateDataAndSendToTop()
            awaitFeed(state, filter.notes)

            assertEquals(scrollsBefore, state.scrollToTop.value)
        } finally {
            scope.cancel()
        }
    }
}
