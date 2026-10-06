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
package com.vitorpamplona.quartz.buzz.arArtifacts.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.utils.ensure

/**
 * The NIP-AR `root` tag — an optional conversation anchor (a `kind:9`, `40002`, `45001` or
 * `45003` event in the home channel) the artifact is attached to. When set on creation, or
 * changed from the previous revision, the relay requires the anchor to exist, undeleted, in
 * `h`; a delete must keep the current value. Ground truth: `accept_artifact` in
 * `buzz-db/src/store/artifact.rs`.
 */
object RootTag {
    const val TAG_NAME = "root"

    fun match(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME

    fun parse(tag: Tag): HexKey? {
        ensure(tag.has(1)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isNotEmpty()) { return null }
        return tag[1]
    }

    /** The anchor's event id, only when it is a well-formed 64-char hex id (for the hint providers). */
    fun parseId(tag: Tag): HexKey? {
        ensure(tag.has(1)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isValid()) { return null }
        return tag[1]
    }

    fun assemble(anchorEventId: HexKey) = arrayOf(TAG_NAME, anchorEventId)
}
