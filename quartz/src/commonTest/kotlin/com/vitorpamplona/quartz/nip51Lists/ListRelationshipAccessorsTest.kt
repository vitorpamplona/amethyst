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
package com.vitorpamplona.quartz.nip51Lists

import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The id-shaped accessors of the NIP-51 lists, and the `linked*()` overrides that now read through them. */
class ListRelationshipAccessorsTest {
    private val a = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val b = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val note1 = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val note2 = "11".repeat(32)
    private val relay = "wss://relay.damus.io/"
    private val article = "30023:$b:article"
    private val app = "32267:$b:com.example.app"

    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    // An `e` and an `a` entry of each well-formed kind, plus one malformed of each.
    private val mixedItems =
        arrayOf(
            arrayOf("d", "set"),
            arrayOf("e", note1, relay),
            arrayOf("a", article, relay),
            arrayOf("e", "short"),
            arrayOf("a", "not:an:address"),
            arrayOf("e", note2),
            arrayOf("t", "nostr"),
        )

    @Test
    fun pinListPinnedEventIds() {
        val event = PinListEvent(id, a, 1700000000, arrayOf(arrayOf("e", note1, relay), arrayOf("e", "short"), arrayOf("e", note2)), "", sig)
        assertEquals(listOf(note1, note2), event.pinnedEventIds())
        assertEquals(event.pinnedEventIds(), event.linkedEventIds())
        assertTrue(PinListEvent(id, a, 1700000000, emptyArray(), "", sig).pinnedEventIds().isEmpty())
    }

    @Test
    fun appCurationSetAppAddressIds() {
        val event = AppCurationSetEvent(id, a, 1700000000, arrayOf(arrayOf("d", "apps"), arrayOf("a", app, relay), arrayOf("a", "garbage"), arrayOf("e", note1)), "", sig)
        assertEquals(listOf(app), event.appAddressIds())
        assertEquals(event.appAddressIds(), event.linkedAddressIds())
    }

    @Test
    fun curationSetsSplitTheirItemsIntoIdsAndAddressIds() {
        val article = ArticleCurationSetEvent(id, a, 1700000000, mixedItems, "", sig)
        val pictures = PictureCurationSetEvent(id, a, 1700000000, mixedItems, "", sig)
        val videos = VideoCurationSetEvent(id, a, 1700000000, mixedItems, "", sig)

        assertEquals(listOf(note1, note2), article.publicItemEventIds())
        assertEquals(listOf(this.article), article.publicItemAddressIds())
        assertEquals(listOf(note1, note2), pictures.publicItemEventIds())
        assertEquals(listOf(this.article), pictures.publicItemAddressIds())
        assertEquals(listOf(note1, note2), videos.publicItemEventIds())
        assertEquals(listOf(this.article), videos.publicItemAddressIds())

        listOf(article, pictures, videos).forEach {
            assertEquals(listOf(note1, note2), it.linkedEventIds())
            assertEquals(listOf(this.article), it.linkedAddressIds())
        }
    }

    @Test
    fun bookmarkListsSplitTheirBookmarksIntoIdsAndAddressIds() {
        val list = BookmarkListEvent(id, a, 1700000000, mixedItems, "", sig)
        val old = OldBookmarkListEvent(id, a, 1700000000, mixedItems, "", sig)
        val set = BookmarkSetEvent(id, a, 1700000000, mixedItems, "", sig)

        assertEquals(listOf(note1, note2), list.publicBookmarkedEventIds())
        assertEquals(listOf(article), list.publicBookmarkedAddressIds())
        assertEquals(listOf(note1, note2), old.publicBookmarkedEventIds())
        assertEquals(listOf(article), old.publicBookmarkedAddressIds())
        assertEquals(listOf(note1, note2), set.publicBookmarkedEventIds())
        assertEquals(listOf(article), set.publicBookmarkedAddressIds())

        assertEquals(list.publicBookmarkedEventIds(), list.linkedEventIds())
        assertEquals(list.publicBookmarkedAddressIds(), list.linkedAddressIds())
        assertEquals(old.publicBookmarkedAddressIds(), old.linkedAddressIds())
        assertEquals(set.publicBookmarkedEventIds(), set.linkedEventIds())
    }

    @Test
    fun releaseArtifactSetItemIds() {
        val event = ReleaseArtifactSetEvent(id, a, 1700000000, mixedItems, "", sig)
        assertEquals(listOf(note1, note2), event.itemEventIds())
        assertEquals(listOf(article), event.itemAddressIds())
        assertEquals(event.itemEventIds(), event.linkedEventIds())
        assertEquals(event.itemAddressIds(), event.linkedAddressIds())
    }

    @Test
    fun muteListMutedUsersAndThreads() {
        val event =
            MuteListEvent(
                id,
                a,
                1700000000,
                arrayOf(
                    arrayOf("p", b, relay),
                    arrayOf("p", "short"),
                    arrayOf("e", note1),
                    arrayOf("e", "zz".repeat(32)),
                    arrayOf("word", "spam"),
                    arrayOf("t", "politics"),
                ),
                "",
                sig,
            )
        assertEquals(listOf(b), event.publicMutedUserIds())
        assertEquals(listOf(note1), event.publicMutedThreadIds())
        assertEquals(event.publicMutedUserIds(), event.linkedPubKeys())
        assertEquals(event.publicMutedThreadIds(), event.linkedEventIds())
    }
}
