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
package com.vitorpamplona.quartz.buzz.aoObserver.tags

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The Buzz `frame` tag — the cleartext direction of an agent observer frame
 * (NIP-AO `kind:24200`). It is the only routing hint a relay reads without
 * touching the NIP-44 ciphertext:
 * - [TELEMETRY] — agent-to-owner telemetry (author is the agent, `p` is the owner).
 * - [CONTROL] — owner-to-agent control commands (author is the owner, `p` is the agent).
 *
 * Ground truth: `buzz-core/src/observer.rs` (`OBSERVER_FRAME_TAG`,
 * `OBSERVER_FRAME_TELEMETRY`, `OBSERVER_FRAME_CONTROL`).
 */
object FrameTag {
    const val TAG_NAME = "frame"

    /** Agent-to-owner telemetry frame value. */
    const val TELEMETRY = "telemetry"

    /** Owner-to-agent control frame value. */
    const val CONTROL = "control"

    fun match(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME

    fun parse(tag: Tag): String? {
        ensure(tag.has(1)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isNotEmpty()) { return null }
        return tag[1]
    }

    fun assemble(frame: String) = arrayOf(TAG_NAME, frame)
}
