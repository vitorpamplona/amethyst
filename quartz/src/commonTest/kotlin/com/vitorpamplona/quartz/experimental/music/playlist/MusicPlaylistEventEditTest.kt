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
package com.vitorpamplona.quartz.experimental.music.playlist

import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MusicPlaylistEventEditTest {
    private val pubkey = "989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"

    private val trackA = Address(MusicTrackEvent.KIND, pubkey, "track-a")
    private val trackB = Address(MusicTrackEvent.KIND, pubkey, "track-b")
    private val trackC = Address(MusicTrackEvent.KIND, pubkey, "track-c")

    // An `a` tag that does NOT point at a music track (e.g. a curated article). Must survive edit.
    private val articleRef = Address(30023, pubkey, "some-article")

    private fun earlierVersion(): MusicPlaylistEvent =
        MusicPlaylistEvent(
            id = "00",
            pubKey = pubkey,
            createdAt = 0L,
            tags =
                arrayOf(
                    arrayOf("d", "pl1"),
                    arrayOf("title", "Old Title"),
                    arrayOf("t", "playlist"),
                    arrayOf("t", "custom-genre"),
                    arrayOf("image", "https://old.example/cover.jpg"),
                    arrayOf("description", "old desc"),
                    arrayOf("a", "${MusicTrackEvent.KIND}:$pubkey:track-a"),
                    arrayOf("a", "30023:$pubkey:some-article"),
                    arrayOf("a", "${MusicTrackEvent.KIND}:$pubkey:track-b"),
                    arrayOf("a", "${MusicTrackEvent.KIND}:$pubkey:track-c"),
                    arrayOf("public", "true"),
                ),
            content = "old notes",
            sig = "00",
        )

    private fun resultOf(
        tracks: List<Address>,
        isPrivate: Boolean = false,
        isCollaborative: Boolean = false,
    ): MusicPlaylistEvent {
        val template =
            MusicPlaylistEvent.edit(
                earlierVersion = earlierVersion(),
                title = "New Title",
                content = "new notes",
                image = "https://new.example/cover.jpg",
                description = "new desc",
                tracks = tracks,
                isPrivate = isPrivate,
                isCollaborative = isCollaborative,
            )
        return MusicPlaylistEvent("00", pubkey, 0L, template.tags, template.content, "00")
    }

    @Test
    fun reordersTracksToTheGivenOrder() {
        val result = resultOf(tracks = listOf(trackC, trackA, trackB))
        assertEquals(listOf(trackC, trackA, trackB), result.trackAddresses())
    }

    @Test
    fun removesDroppedTracks() {
        val result = resultOf(tracks = listOf(trackA, trackC))
        assertEquals(listOf(trackA, trackC), result.trackAddresses())
    }

    @Test
    fun updatesComposerOwnedMetadata() {
        val result = resultOf(tracks = listOf(trackA), isPrivate = true, isCollaborative = true)
        assertEquals("New Title", result.title())
        assertEquals("https://new.example/cover.jpg", result.image())
        assertEquals("new desc", result.description())
        assertEquals("new notes", result.content)
        assertTrue(result.isPrivate())
        assertTrue(result.isCollaborative())
    }

    @Test
    fun switchingToPrivateClearsThePublicFlag() {
        val result = resultOf(tracks = listOf(trackA), isPrivate = true)
        val tagsByName = result.tags.groupBy { it[0] }
        assertFalse(tagsByName.containsKey("public"), "stale public flag must be dropped")
        assertTrue(tagsByName.containsKey("private"))
        assertTrue(result.isPrivate())
    }

    @Test
    fun preservesDTagCustomHashtagsAndNonTrackReferences() {
        val result = resultOf(tracks = listOf(trackA, trackB, trackC))
        val tagsByName = result.tags.groupBy { it[0] }

        // Same addressable identity → same d tag.
        assertEquals("pl1", tagsByName["d"]!!.single()[1])

        // Custom hashtags untouched (both the category marker and the user's genre tag).
        val tValues = tagsByName["t"]!!.map { it[1] }
        assertTrue(tValues.contains("playlist"))
        assertTrue(tValues.contains("custom-genre"))

        // The non-track `a` reference survives the track reset.
        val aValues = tagsByName["a"]!!.map { it[1] }
        assertTrue(aValues.contains("30023:$pubkey:some-article"))
    }

    @Test
    fun clearingCoverRemovesTheImageTag() {
        val template =
            MusicPlaylistEvent.edit(
                earlierVersion = earlierVersion(),
                title = "New Title",
                content = "",
                image = null,
                description = null,
                tracks = listOf(trackA),
                isPrivate = false,
                isCollaborative = false,
            )
        val tagsByName = template.tags.groupBy { it[0] }
        assertFalse(tagsByName.containsKey("image"), "null cover must remove the image tag")
        assertFalse(tagsByName.containsKey("description"), "null description must remove the tag")
    }
}
