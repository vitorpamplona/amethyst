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
package com.vitorpamplona.quartz.buzz.threading

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag

/**
 * Buzz's NIP-10 thread e-tags, shared by stream messages (40002) and forum comments
 * (45003) — both mirror `thread_tags` in `buzz-sdk/src/builders.rs`:
 * - direct reply (`root == parent`): one `["e", root, "", "reply"]`
 * - nested reply (`root != parent`): `["e", root, "", "root"]` + `["e", parent, "", "reply"]`
 *
 * The empty relay slot is emitted verbatim so the marker stays at index 3, matching the
 * reference wire form. ([MarkedETag.assemble] cannot be used here: its `arrayOfNotNull`
 * drops the null relay slot, sliding the marker into index 2.)
 */
fun <T : Event> TagArrayBuilder<T>.buzzThread(
    rootEventId: HexKey,
    parentEventId: HexKey,
) = if (rootEventId == parentEventId) {
    add(arrayOf(MarkedETag.TAG_NAME, rootEventId, "", MarkedETag.MARKER.REPLY.code))
} else {
    add(arrayOf(MarkedETag.TAG_NAME, rootEventId, "", MarkedETag.MARKER.ROOT.code))
    add(arrayOf(MarkedETag.TAG_NAME, parentEventId, "", MarkedETag.MARKER.REPLY.code))
}
