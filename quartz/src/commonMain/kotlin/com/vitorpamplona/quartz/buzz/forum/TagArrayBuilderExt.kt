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

import com.vitorpamplona.quartz.buzz.threading.buzzThread
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/** The `h` channel tag (NIP-29) scoping a Buzz forum event to its channel UUID. */
fun <T : Event> TagArrayBuilder<T>.forumChannel(channelId: String) = addUnique(GroupIdTag.assemble(channelId))

/** A `p` mention per pubkey, deduplicated. Mirrors `mention_tags` in `builders.rs`. */
fun <T : Event> TagArrayBuilder<T>.forumMentions(mentions: List<HexKey>) = mentions.forEach { addUniqueValueIfNew(PTag.assemble(it, null)) }

/** The `e` tag naming a forum vote's target event. */
fun <T : Event> TagArrayBuilder<T>.forumVoteTarget(targetEventId: HexKey) = addUnique(ETag.assemble(targetEventId, null, null))

/**
 * NIP-10 thread e-tags for a forum comment. Buzz threads streams and forum comments
 * identically (`thread_tags` in `builders.rs`), so this delegates to the shared
 * [buzzThread] verb.
 */
fun <T : Event> TagArrayBuilder<T>.forumThread(
    rootEventId: HexKey,
    parentEventId: HexKey,
) = buzzThread(rootEventId, parentEventId)
