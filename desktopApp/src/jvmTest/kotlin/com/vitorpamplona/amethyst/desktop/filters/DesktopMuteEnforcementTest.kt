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
package com.vitorpamplona.amethyst.desktop.filters

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.feeds.DesktopFollowingFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopGlobalFeedFilter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.DualCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the "mute is a silent no-op on desktop" bug: the feed
 * filters must drop notes that `Note.isHiddenFor(...)` rejects. Exercises the
 * real [DesktopGlobalFeedFilter] / [DesktopFollowingFeedFilter] with a
 * hand-built [LiveHiddenUsers], the same value the account assembles at runtime.
 */
class DesktopMuteEnforcementTest {
    private val mutedAuthor = "0000000000000000000000000000000000000000000000000000000000000001"
    private val cleanAuthor = "0000000000000000000000000000000000000000000000000000000000000002"
    private val cache = DesktopLocalCache()

    private fun user(hex: String): User = User(hex) { addr -> Note(addr.toValue()) }

    private fun note(
        id: String,
        pubkey: String,
        content: String = "hi",
    ): Note {
        val event = TextNoteEvent(id, pubkey, 0L, emptyArray(), content, "")
        val n = Note(id)
        n.loadEvent(event, user(pubkey), emptyList())
        return n
    }

    private fun hiddenUsers(pubkeys: Set<String>) =
        LiveHiddenUsers(
            showSensitiveContent = null,
            hiddenWordsCase = emptyList(),
            hiddenUsersHashCodes = pubkeys.mapTo(HashSet()) { it.hashCode() },
            spammersHashCodes = emptySet(),
            hiddenUsers = pubkeys,
        )

    @Test
    fun globalFeed_hidesMutedAuthor() {
        val filter = DesktopGlobalFeedFilter(cache) { hiddenUsers(setOf(mutedAuthor)) }
        val muted = note("aa", mutedAuthor)
        val clean = note("bb", cleanAuthor)
        val result = filter.applyFilter(setOf(muted, clean))
        assertEquals("Muted author's note must be filtered out", setOf(clean), result)
    }

    @Test
    fun globalFeed_showsEverythingWhenNothingMuted() {
        val filter = DesktopGlobalFeedFilter(cache) { LiveHiddenUsers.EMPTY }
        val a = note("aa", mutedAuthor)
        val b = note("bb", cleanAuthor)
        val result = filter.applyFilter(setOf(a, b))
        assertEquals(setOf(a, b), result)
    }

    @Test
    fun followingFeed_hidesMutedAuthorEvenIfFollowed() {
        // Muting wins over following: a muted author you follow is still hidden.
        val filter =
            DesktopFollowingFeedFilter(cache, { hiddenUsers(setOf(mutedAuthor)) }) {
                setOf(mutedAuthor, cleanAuthor)
            }
        val muted = note("aa", mutedAuthor)
        val clean = note("bb", cleanAuthor)
        val result = filter.applyFilter(setOf(muted, clean))
        assertEquals(setOf(clean), result)
    }

    @Test
    fun globalFeed_hidesNoteContainingHiddenWord() {
        val choices =
            LiveHiddenUsers(
                showSensitiveContent = null,
                hiddenWordsCase = listOf(DualCase("spam", "SPAM")),
                hiddenUsersHashCodes = emptySet(),
                spammersHashCodes = emptySet(),
                hiddenWords = setOf("spam"),
            )
        val filter = DesktopGlobalFeedFilter(cache) { choices }
        val spammy = note("aa", cleanAuthor, content = "buy my SPAM now")
        val ok = note("bb", cleanAuthor, content = "good morning")
        val result = filter.applyFilter(setOf(spammy, ok))
        assertTrue("Hidden-word note must be filtered", spammy !in result)
        assertTrue("Clean note must remain", ok in result)
    }
}
