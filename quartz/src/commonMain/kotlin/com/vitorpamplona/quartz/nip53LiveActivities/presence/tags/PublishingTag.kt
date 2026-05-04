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
package com.vitorpamplona.quartz.nip53LiveActivities.presence.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * `["publishing", "0|1"]` on a kind-10312 presence event.
 *
 * Indicates whether this peer is currently pushing audio to the
 * relay (i.e. holds an active speaker slot AND is not muted nor
 * paused). Distinct from [MutedTag], which can be true while still
 * publishing silence; `publishing=1, muted=1` is "I'm a speaker but
 * temporarily muted", and `publishing=0` means no audio packets at
 * all.
 *
 * The participant grid (Tier 2) uses this to render an "active
 * waveform" indicator only on peers actually broadcasting.
 */
class PublishingTag {
    companion object Companion {
        const val TAG_NAME = "publishing"

        fun parse(tag: Array<String>): Boolean? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1] == "1"
        }

        fun assemble(publishing: Boolean) = arrayOf(TAG_NAME, if (publishing) "1" else "0")
    }
}
