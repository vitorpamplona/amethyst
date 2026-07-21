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
package com.vitorpamplona.quartz.buzz.forum

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/** The `h` channel tag (NIP-29) scoping a Buzz forum event to its channel UUID. */
fun <T : Event> TagArrayBuilder<T>.forumChannel(channelId: String) = addUnique(GroupIdTag.assemble(channelId))

/** A `p` mention per pubkey, deduplicated. Mirrors `mention_tags` in `builders.rs`. */
fun <T : Event> TagArrayBuilder<T>.forumMentions(mentions: List<HexKey>) = mentions.forEach { addUniqueValueIfNew(PTag.assemble(it, null)) }

/** The `e` tag naming a forum vote's target event. */
fun <T : Event> TagArrayBuilder<T>.forumVoteTarget(targetEventId: HexKey) = addUnique(ETag.assemble(targetEventId, null, null))

/**
 * NIP-10 thread e-tags for a forum comment, matching `thread_tags` in `builders.rs`:
 * - direct reply (`root == parent`): one `["e", root, "", "reply"]`
 * - nested reply (`root != parent`): `["e", root, "", "root"]` + `["e", parent, "", "reply"]`
 *
 * The empty relay slot is emitted verbatim so the marker stays at index 3, matching the
 * reference wire form.
 */
fun <T : Event> TagArrayBuilder<T>.forumThread(
    rootEventId: HexKey,
    parentEventId: HexKey,
) = if (rootEventId == parentEventId) {
    add(arrayOf(MarkedETag.TAG_NAME, rootEventId, "", MarkedETag.MARKER.REPLY.code))
} else {
    add(arrayOf(MarkedETag.TAG_NAME, rootEventId, "", MarkedETag.MARKER.ROOT.code))
    add(arrayOf(MarkedETag.TAG_NAME, parentEventId, "", MarkedETag.MARKER.REPLY.code))
}
