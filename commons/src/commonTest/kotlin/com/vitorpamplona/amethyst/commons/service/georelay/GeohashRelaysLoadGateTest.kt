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
package com.vitorpamplona.amethyst.commons.service.georelay

import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The georelays directory is a GitHub download: accounts that follow no geohash must never make it. */
class GeohashRelaysLoadGateTest {
    private var previousLoader: (suspend () -> List<GeoRelay>)? = null
    private var downloads = 0

    @BeforeTest
    fun setUp() {
        previousLoader = GeohashRelays.liveRelayLoader
        // Empty: the shared directory and the loaded flag stay untouched for other tests.
        GeohashRelays.liveRelayLoader = {
            downloads++
            emptyList()
        }
    }

    @AfterTest
    fun tearDown() {
        GeohashRelays.liveRelayLoader = previousLoader
        GeohashRelays.refreshed = false
        GeoRelayDirectory.shared.setRelays(GeoRelayDirectory.FALLBACK)
    }

    @Test
    fun downloadsOnlyOnceAGeohashIsFollowedWithGeohashChatsOn() =
        runTest {
            val cells = MutableStateFlow(emptySet<String>())
            val feeds = MutableStateFlow(ChatFeedType.ALL - ChatFeedType.GEOHASH)

            val load = async { GeohashRelays.ensureLoadedOnceNeeded(cells, feeds) }
            runCurrent()
            assertEquals(0, downloads, "follows nothing, geohash chats off")

            cells.value = setOf("u4pruy")
            runCurrent()
            assertEquals(0, downloads, "follows a geohash, but geohash chats are off")

            feeds.value = ChatFeedType.ALL
            runCurrent()
            assertEquals(1, downloads)
            assertFalse(load.await())
        }

    @Test
    fun noDownloadWhileTheAccountFollowsNoGeohash() =
        runTest {
            val load = async { GeohashRelays.ensureLoadedOnceNeeded(MutableStateFlow(emptySet()), MutableStateFlow(ChatFeedType.ALL)) }
            runCurrent()
            assertEquals(0, downloads)
            load.cancel()
        }

    @Test
    fun reroutesWhenAnOpenedChatLoadedTheDirectoryFirst() =
        runTest {
            // More relays than the fallback, so a load counts as the live directory.
            val live = GeoRelayDirectory.FALLBACK + GeoRelayDirectory.parseCsv("extra.example.com,1.0,2.0")
            GeohashRelays.liveRelayLoader = {
                downloads++
                live
            }
            val cells = MutableStateFlow(emptySet<String>())
            val load = async { GeohashRelays.ensureLoadedOnceNeeded(cells, MutableStateFlow(ChatFeedType.ALL)) }
            runCurrent()

            // An opened geohash chat downloads it while the chat list still waits.
            assertTrue(GeohashRelays.ensureLoaded())
            cells.value = setOf("u4pruy")

            assertTrue(load.await(), "the chat list must re-route onto the live directory")
            assertEquals(1, downloads)
        }
}
