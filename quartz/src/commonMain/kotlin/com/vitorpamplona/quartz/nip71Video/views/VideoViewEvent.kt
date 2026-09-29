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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip71Video.AddressableVideoEvent
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewPhase
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewSource
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewedRange
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * "Someone watched this video": an ephemeral analytics event divine.video publishes for its
 * NIP-71 videos (kind 22236, not part of NIP-71). Relays pass it on without storing it, so only a
 * service listening live, such as Divine's relay, turns it into view and loop counts.
 *
 * A viewing session is reported in two phases: one [ViewPhase.START] when playback begins, which
 * counts the view, then one [ViewPhase.END] per interruption carrying the watch time since the
 * previous `end`. An event with no `phase` is the older single-shot report. The content is empty.
 *
 * Schema: divine-mobile `mobile/docs/NOSTR_VIDEO_EVENTS.md`.
 */
@Immutable
class VideoViewEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    EventHintProvider {
    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    /** The video that was watched. */
    fun video() = tags.video()

    /** The id of the exact version that was watched. */
    fun videoVersion() = tags.videoVersion()

    fun phase() = tags.phase()

    fun viewed() = tags.viewed()

    fun loops() = tags.loops()

    fun source() = tags.source()

    companion object {
        const val KIND = 22236

        /** Playback started. Carries no watch time: nothing has been watched yet. */
        fun <T : AddressableVideoEvent> buildStart(
            video: EventHintBundle<T>,
            source: ViewSource? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<VideoViewEvent>.() -> Unit = {},
        ) = build(video, ViewPhase.START, null, null, source, createdAt, initializer)

        /**
         * A segment ended after [watchedSeconds] of playback, [loops] of them complete or partial.
         * A [loops] that is not a positive finite number is left out, as divine-mobile does; a negative
         * [watchedSeconds] throws, since it could only be a bug in the caller's clock.
         */
        fun <T : AddressableVideoEvent> buildEnd(
            video: EventHintBundle<T>,
            watchedSeconds: Long,
            loops: Double? = null,
            source: ViewSource? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<VideoViewEvent>.() -> Unit = {},
        ): EventTemplate<VideoViewEvent> {
            val playthroughs = loops?.takeIf { it.isFinite() && it > 0.0 }
            return build(video, ViewPhase.END, ViewedRange(0, watchedSeconds), playthroughs, source, createdAt, initializer)
        }

        /**
         * Prefer [buildStart] / [buildEnd]: they keep watch time off `start` events, where it
         * would count engagement the viewer never gave.
         */
        fun <T : AddressableVideoEvent> build(
            video: EventHintBundle<T>,
            phase: ViewPhase?,
            viewed: ViewedRange?,
            loops: Double?,
            source: ViewSource?,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<VideoViewEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            video(video)
            phase?.let { phase(it) }
            viewed?.let { viewed(it) }
            loops?.let { loops(it) }
            source?.let { source(it) }
            initializer()
        }
    }
}
