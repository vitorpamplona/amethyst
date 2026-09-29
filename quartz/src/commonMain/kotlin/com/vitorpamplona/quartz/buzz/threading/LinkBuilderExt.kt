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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag

/**
 * ROOT and PARENT from Buzz's thread e-tags ([buzzThread]). A direct reply carries only a
 * `reply` marker, because its root IS its parent, so a lone `reply` is both the ROOT and the
 * PARENT; a nested reply carries both markers.
 */
fun LinkBuilder.buzzThreadLinks(
    root: HexKey?,
    reply: HexKey?,
) {
    event(Relation.ROOT, root ?: reply, ETag.TAG_NAME)
    event(Relation.PARENT, reply, ETag.TAG_NAME)
}

/** [buzzThreadLinks] from the marked `e` tags of a stream message (40002) or a forum comment (45003). */
fun LinkBuilder.buzzThreadLinks(tags: TagArray) = buzzThreadLinks(tags.buzzThreadRoot(), tags.buzzThreadReply())
