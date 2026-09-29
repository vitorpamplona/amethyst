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
package com.vitorpamplona.quartz.nip71Video

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.CreditProps
import com.vitorpamplona.quartz.nip01Core.links.props.ParticipantProps
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip71Video.textTrack.TextTrackEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip71VideoLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val relay = "wss://relay.example/"
    private val participant = "a".repeat(64)
    private val collaborator = "b".repeat(64)
    private val inspiration = "c".repeat(64)
    private val mentioned = "d".repeat(64)
    private val cited = "2".repeat(64)
    private val track = "3".repeat(64)

    @Test
    fun videoTellsParticipantsCreditsAndMentionsApart() {
        val audio = "34236:$inspiration:song"
        val subtitles = "39307:$author:subtitles:clip"
        val tags =
            arrayOf(
                arrayOf("imeta", "url https://video.example/clip.mp4"),
                arrayOf("p", participant),
                arrayOf("p", collaborator, "Collaborator"),
                arrayOf("p", inspiration, relay, "inspired-by"),
                arrayOf("p", mentioned, relay, "mention"),
                arrayOf("a", audio, relay, "audio"),
                arrayOf("e", cited, relay, "mention"),
                arrayOf("e", "4".repeat(64), "", "root"),
                arrayOf("text-track", subtitles, relay, "subtitles", "en"),
                arrayOf("text-track", "nostr:" + NEvent.create(track, null, null, null)),
                arrayOf("text-track", "https://blossom.example/clip.vtt"),
                arrayOf("t", "Fun"),
            )
        val expected =
            listOf(
                Link(Relation.PARTICIPANT, LinkTarget.User(participant), "p"),
                Link(Relation.PARTICIPANT, LinkTarget.User(collaborator), "p", ParticipantProps(listOf("Collaborator"))),
                Link(Relation.CREDITED, LinkTarget.User(inspiration), "p", CreditProps("inspired-by")),
                Link(Relation.MENTION, LinkTarget.User(mentioned), "p"),
                Link(Relation.CREDITED, LinkTarget.Address(audio), "a", CreditProps("audio")),
                Link(Relation.MENTION, LinkTarget.Event(cited), "e"),
                Link(Relation.TEXT_TRACK, LinkTarget.Address(subtitles), "text-track"),
                Link(Relation.TEXT_TRACK, LinkTarget.Event(track), "text-track"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "fun"), "t"),
            )
        assertEquals(expected, VideoNormalEvent(id, author, 1, tags, "", sig).links())
        assertEquals(expected, AddressableShortVideoEvent(id, author, 1, arrayOf(arrayOf("d", "clip")) + tags, "", sig).links())
    }

    @Test
    fun textTrackNamesItsVideoAndLanguage() {
        val video = "34236:$author:clip"
        val event = TextTrackEvent(id, author, 1, arrayOf(arrayOf("d", "subtitles:clip"), arrayOf("a", video), arrayOf("l", "en")), "WEBVTT", sig)
        assertEquals(
            listOf(
                Link(Relation.VIDEO, LinkTarget.Address(video), "a"),
                Link(Relation.TAG, LinkTarget.Tag("l", "en"), "l"),
            ),
            event.links(),
        )
    }
}
