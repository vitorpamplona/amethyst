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
package com.vitorpamplona.quartz.experimental.postingStreak

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 13473, Posting Streak (Ditto's `NIP.md`; the number keypad-spells 1-FIRE): the author's
 * self-reported run of creative posts with no gap over 36 hours, as `start`/`end` unix-second
 * tags and empty content. Replaceable, one per user. It is trust-based: the author's client
 * maintains it from the author's own events, and a forged streak only changes how the forger's
 * own profile looks.
 *
 * Read it through [streak] / [daysAt], which apply the spec's rejection rules and liveness window
 * rather than trusting the raw tags. Live events also carry `published_at` (the streak's first
 * publication) and Ditto's `client` tag; neither changes the reading.
 *
 * References nothing (no graph edges, no hints) and is not searchable: two timestamps.
 *
 * The spec requires an `alt`; [build] does not write one (Quartz does not add boilerplate alt
 * tags). Live events carry `["alt", "Posting streak"]`; pass it through the initializer if needed.
 */
@Immutable
class PostingStreakEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The raw `start` tag, when it is a non-negative integer. */
    fun start() = tags.streakStart()

    /** The raw `end` tag, when it is a non-negative integer. */
    fun end() = tags.streakEnd()

    /**
     * The streak, or null when the event is one clients SHOULD reject at [now]: a missing or
     * non-integer bound, `start > end`, or an `end` more than a few minutes in the future.
     * It may be broken; check [PostingStreak.isLive].
     */
    fun streak(now: Long = TimeUtils.now()) = PostingStreak.validOrNull(start(), end(), now)

    /** The days to display at [now]: 0 for a broken or rejected streak. */
    fun daysAt(now: Long = TimeUtils.now()) = streak(now)?.daysAt(now) ?: 0L

    companion object {
        const val KIND = 13473

        fun build(
            start: Long,
            end: Long,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PostingStreakEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            streakStart(start)
            streakEnd(end)
            initializer()
        }

        fun build(
            streak: PostingStreak,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PostingStreakEvent>.() -> Unit = {},
        ) = build(streak.start, streak.end, createdAt, initializer)
    }
}
