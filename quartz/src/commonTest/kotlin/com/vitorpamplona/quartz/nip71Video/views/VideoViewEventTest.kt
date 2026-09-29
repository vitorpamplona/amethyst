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
package com.vitorpamplona.quartz.nip71Video.views

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.views.tags.LoopsTag
import com.vitorpamplona.quartz.nip71Video.views.tags.PhaseTag
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewPhase
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewSource
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewedRange
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewedTag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Kind 22236 against the shape divine-mobile publishes (`mobile/lib/services/view_event_publisher.dart`
 * and the example in `mobile/docs/NOSTR_VIDEO_EVENTS.md`).
 */
class VideoViewEventTest {
    private val videoAuthor = "4d7dccc0a5116daa057348ef79c573873cddd9eff066fc6a5f3d37e8264afbeb"
    private val videoD = "c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862"
    private val videoId = "fa5a793b24edfe109f8d02ad6aa6cde77b0a923566f891df403049721b46e8d9"

    private val video =
        """{"id":"$videoId","pubkey":"$videoAuthor","created_at":1789661586,"kind":34236,"tags":[["d","$videoD"],["imeta","url https://media.divine.video/$videoD","m video/mp4"],["title","Xmas Already??"]],"content":"","sig":""}"""

    // An `end` segment exactly as divine-mobile writes it, client tag included.
    private val endView =
        """{"id":"2b1b0b53d6c0c2f1f5a8a2d5f0e7e1f8c9a4b3d2e1f0a9b8c7d6e5f4a3b2c1d0","pubkey":"34257350449d357c37e93eb8aef387ff1fee8879d794da664462346a4b540aa8","created_at":1789666900,"kind":22236,"tags":[["a","34236:$videoAuthor:$videoD","wss://relay.divine.video"],["e","$videoId","wss://relay.divine.video"],["phase","end"],["viewed","0","12"],["source","discovery:foryou"],["loops","2.0"],["version","1.0.23"],["client","Divine","31990:d95aa8fc0eff8e488952495b8064991d27fb96ed8652f12cdedc5a4e8b5ae540:divine-mobile","wss://relay.divine.video"]],"content":"","sig":""}"""

    // The pre-phase single-shot shape: no `phase`, and `viewed` carries the whole session.
    private val legacyView =
        """{"id":"3c2c1c64e7d1d3f2f6b9b3e6f1f8f2f9dab5c4e3f2f1bac9d8e7f6f5b4c3d2e1","pubkey":"34257350449d357c37e93eb8aef387ff1fee8879d794da664462346a4b540aa8","created_at":1789666900,"kind":22236,"tags":[["a","34236:$videoAuthor:$videoD","wss://relay.divine.video"],["e","$videoId","wss://relay.divine.video"],["viewed","0","5"],["loops","0.75"],["source","search","cats"]],"content":"","sig":""}"""

    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.divine.video")!!

    @Test
    fun parsesAnEndSegment() {
        val event = assertIs<VideoViewEvent>(Event.fromJson(endView))

        assertEquals("34236:$videoAuthor:$videoD", event.video()?.toValue())
        assertEquals(videoId, event.videoVersion())
        assertEquals(ViewPhase.END, event.phase())
        assertEquals(ViewedRange(0, 12), event.viewed())
        assertEquals(12, event.viewed()?.seconds)
        assertEquals(2.0, event.loops())
        // The tab rides inside the type; category is how a reader groups every discovery tab.
        assertEquals(ViewSource("discovery:foryou"), event.source())
        assertEquals(ViewSource.DISCOVERY, event.source()?.category)
        assertEquals(listOf("34236:$videoAuthor:$videoD"), event.linkedAddressIds())
        assertEquals(listOf(videoId), event.linkedEventIds())
    }

    @Test
    fun parsesALegacySingleShot() {
        val event = assertIs<VideoViewEvent>(Event.fromJson(legacyView))

        assertNull(event.phase())
        assertEquals(ViewedRange(0, 5), event.viewed())
        assertEquals(0.75, event.loops())
        assertEquals(ViewSource(ViewSource.SEARCH, "cats"), event.source())
        assertEquals(ViewSource.SEARCH, event.source()?.category)
    }

    @Test
    fun rejectsMalformedValues() {
        assertNull(PhaseTag.parse(arrayOf("phase", "middle")))
        assertNull(ViewedTag.parse(arrayOf("viewed", "10", "5")))
        assertNull(ViewedTag.parse(arrayOf("viewed", "0")))
        assertNull(LoopsTag.parse(arrayOf("loops", "-1")))
        assertNull(LoopsTag.parse(arrayOf("loops", "NaN")))
    }

    @Test
    fun endRefusesWhatTheParserWouldDrop() {
        val bundle = EventHintBundle(Event.fromJson(video) as AddressableShortVideoEvent, relay)

        assertFailsWith<IllegalArgumentException> { VideoViewEvent.buildEnd(bundle, watchedSeconds = -1) }

        // Not a playthrough count: left out, as divine-mobile does, instead of signed and ignored.
        for (loops in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val template = VideoViewEvent.buildEnd(bundle, watchedSeconds = 3, loops = loops)
            assertNull(template.tags.firstOrNull { it[0] == LoopsTag.TAG_NAME }, "loops=$loops")
        }
    }

    @Test
    fun startCarriesNoWatchTime() {
        val bundle = EventHintBundle(Event.fromJson(video) as AddressableShortVideoEvent, relay)
        val template = VideoViewEvent.buildStart(bundle, ViewSource(ViewSource.HOME))

        assertEquals(VideoViewEvent.KIND, template.kind)
        assertEquals("", template.content)
        assertEquals(
            listOf(
                listOf("a", "34236:$videoAuthor:$videoD", "wss://relay.divine.video/"),
                listOf("e", videoId, "wss://relay.divine.video/", videoAuthor),
                listOf("phase", "start"),
                listOf("source", "home"),
            ),
            template.tags.map { it.toList() },
        )
    }

    @Test
    fun endCarriesTheSegment() {
        val bundle = EventHintBundle(Event.fromJson(video) as AddressableShortVideoEvent, relay)
        val template = VideoViewEvent.buildEnd(bundle, watchedSeconds = 12, loops = 2.0, source = ViewSource(ViewSource.PROFILE))

        assertContentEquals(arrayOf("phase", "end"), template.tags[2])
        assertContentEquals(arrayOf("viewed", "0", "12"), template.tags[3])
        assertContentEquals(arrayOf("loops", "2.0"), template.tags[4])
        assertContentEquals(arrayOf("source", "profile"), template.tags[5])
    }
}
