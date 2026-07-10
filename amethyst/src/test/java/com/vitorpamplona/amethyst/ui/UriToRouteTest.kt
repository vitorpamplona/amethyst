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
package com.vitorpamplona.amethyst.ui

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UriToRouteTest {
    private val account = mockk<Account>()

    @Test
    fun fragmentHashtagRoutesToHashtagFeed() {
        assertEquals(Route.Hashtag("nostrmultiplayergames"), uriToRoute("#NostrMultiplayerGames", account))
    }

    @Test
    fun fragmentHashtagKeepsUnicodeTags() {
        assertEquals(Route.Hashtag("日本語"), uriToRoute("#日本語", account))
    }

    @Test
    fun invalidFragmentHashtagsAreNotRoutes() {
        assertNull(uriToRoute("#", account))
        assertNull(uriToRoute("# ", account))
        assertNull(uriToRoute("#two words", account))
    }

    @Test
    fun fullUrlsWithAnchorsAreNotHashtagRoutes() {
        assertNull(uriToRoute("https://example.com/page#anchor", account))
    }

    @Test
    fun hashtagQueryRoutesStillWork() {
        assertEquals(Route.Hashtag("foo"), uriToRoute("hashtag?id=foo", account))
        assertEquals(Route.Hashtag("foo"), uriToRoute("nostr:hashtag?id=foo", account))
    }

    @Test
    fun fragmentHashtagOrNullExtractsTheTag() {
        assertEquals("NostrMultiplayerGames", fragmentHashtagOrNull("#NostrMultiplayerGames"))
        assertNull(fragmentHashtagOrNull("#"))
        assertNull(fragmentHashtagOrNull("##double"))
        assertNull(fragmentHashtagOrNull("#tag!"))
        assertNull(fragmentHashtagOrNull("https://example.com/page#anchor"))
        assertNull(fragmentHashtagOrNull("nostr:hashtag?id=foo"))
    }
}
