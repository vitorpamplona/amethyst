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
package com.vitorpamplona.quartz.buzz.stream

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz diff/patch message showing file changes in unified-diff format, `kind:40008`.
 *
 * Mirrors `build_diff_message` in Buzz's `buzz-sdk/src/builders.rs`: [content] is the
 * unified diff text and the [DiffMeta] rides in tags (`repo`, `commit`, `file`,
 * `parent-commit`, `branch`, `pr`, `l`, `description`, `truncated`, `alt`) under the
 * `h` channel scope.
 */
@Immutable
class StreamMessageDiffEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun channel() = tags.channel()

    fun diffMeta() = tags.diffMeta()

    companion object {
        const val KIND = 40008

        fun build(
            channelId: String,
            diff: String,
            meta: DiffMeta,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<StreamMessageDiffEvent>.() -> Unit = {},
        ) = eventTemplate<StreamMessageDiffEvent>(KIND, diff, createdAt) {
            channel(channelId)
            diffMeta(meta)
            initializer()
        }
    }
}
