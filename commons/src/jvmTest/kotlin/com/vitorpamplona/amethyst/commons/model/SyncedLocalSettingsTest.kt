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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.model.preferences.FollowListSlot
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncedLocalSettingsTest {
    private val keys = AccountSettings(KeyPair())

    @Test
    fun theGroupsSurviveTheBlobsJsonRoundTrip() {
        val settings = keys
        settings.updateAccentColor(AccentColorType.GREEN)
        settings.changeDefaultHomeFollowList(TopFilter.Global)
        settings.changeDefaultFileServer(ServerName("Mine", "https://files.example.com", ServerType.NIP96))
        settings.changeCallsEnabled(false)

        val json = JsonMapper.toJson(settings.toSyncedSettingsInternal())
        val back = JsonMapper.fromJson<AccountSyncedSettingsInternal>(json)

        assertEquals("GREEN", back.appearance?.accentColor)
        assertEquals(false, back.calls?.callsEnabled)
        assertEquals(ServerName("Mine", "https://files.example.com", ServerType.NIP96), decodeFileServer(back.uploads?.defaultFileServer))
        val home = readableFeedFilters(back.feeds!!.feedFilters)[FollowListSlot.HOME]
        assertEquals(TopFilter.Global, decodeFeedFilter(assertNotNull(home)))
        assertEquals(FollowListSlot.entries.size, back.feeds!!.feedFilters.size)
    }

    /** A blob an older version wrote has none of the groups: the device keeps its own values. */
    @Test
    fun anOlderBlobHasNoGroups() {
        val older = """{"reactions":{"reactionChoices":["+"]}}"""
        val decoded = JsonMapper.fromJson<AccountSyncedSettingsInternal>(older)

        assertNull(decoded.appearance)
        assertNull(decoded.messages)
        assertNull(decoded.feeds)
        assertNull(decoded.relayAuth)
        assertNull(decoded.uploads)
        assertNull(decoded.calls)
    }

    @Test
    fun anAccentThisVersionDoesNotKnowKeepsTheCurrentOne() {
        assertEquals(AccentColorType.RED, decodeAccentColor("TEAL_FROM_THE_FUTURE", AccentColorType.RED))
        assertEquals(AccentColorType.BLUE, decodeAccentColor("BLUE", AccentColorType.RED))
        // Null is a choice: follow the application color.
        assertNull(decodeAccentColor(null, AccentColorType.RED))
    }

    @Test
    fun unknownSlotsAndFiltersAreSkipped() {
        val readable =
            readableFeedFilters(
                mapOf(
                    "HOME" to JsonMapper.toJson<TopFilter>(TopFilter.Global),
                    "A_SCREEN_FROM_THE_FUTURE" to JsonMapper.toJson<TopFilter>(TopFilter.Global),
                    "PODCASTS" to """{"type":"com.vitorpamplona.amethyst.model.TopFilter.FromTheFuture"}""",
                ),
            )

        assertEquals(setOf(FollowListSlot.HOME), readable.keys)
    }

    @Test
    fun aServerTypeThisVersionDoesNotKnowIsSkipped() {
        assertNull(decodeFileServer(SyncedFileServer("X", "https://x.example.com", "NIP999")))
        assertNull(decodeFileServer(null))
    }

    @Test
    fun anEnumNameThisVersionDoesNotKnowKeepsTheCurrentOne() {
        assertEquals(ServerType.NIP95, enumByName("NOPE", ServerType.NIP95))
        assertEquals(ServerType.Blossom, enumByName("Blossom", ServerType.NIP95))
    }
}
