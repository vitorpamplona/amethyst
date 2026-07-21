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

import com.vitorpamplona.quartz.buzz.stream.tags.BranchTag
import com.vitorpamplona.quartz.buzz.stream.tags.BroadcastTag
import com.vitorpamplona.quartz.buzz.stream.tags.CommitTag
import com.vitorpamplona.quartz.buzz.stream.tags.DescriptionTag
import com.vitorpamplona.quartz.buzz.stream.tags.FileTag
import com.vitorpamplona.quartz.buzz.stream.tags.LanguageTag
import com.vitorpamplona.quartz.buzz.stream.tags.ParentCommitTag
import com.vitorpamplona.quartz.buzz.stream.tags.PrTag
import com.vitorpamplona.quartz.buzz.stream.tags.RepoTag
import com.vitorpamplona.quartz.buzz.stream.tags.TruncatedTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip31Alts.AltTag

/** Adds the `h` channel-scope tag shared by every Buzz stream event. */
fun <T : Event> TagArrayBuilder<T>.channel(channelId: String) = addUnique(GroupIdTag.assemble(channelId))

/** Adds a plain `e` tag pointing at a target message. */
fun <T : Event> TagArrayBuilder<T>.targetMessage(eventId: HexKey) = add(ETag.assemble(eventId, null, null))

/** Adds a single `p` mention tag. */
fun <T : Event> TagArrayBuilder<T>.mention(pubkey: HexKey) = addUnique(PTag.assemble(pubkey, null))

/**
 * Adds one `p` mention tag per pubkey, deduplicated — matching `mention_tags` in
 * buzz-sdk builders.rs (which lowercases + dedupes). Duplicate `p` mentions would
 * otherwise be wire noise a relay cap could trip on.
 */
fun <T : Event> TagArrayBuilder<T>.mentions(pubkeys: List<HexKey>) = pubkeys.forEach { addUniqueValueIfNew(PTag.assemble(it, null)) }

/** Marks the message as a channel-wide broadcast (`["broadcast", "1"]`). */
fun <T : Event> TagArrayBuilder<T>.broadcast() = addUnique(BroadcastTag.assemble())

/** Adds all tags of a [DiffMeta] in the order `build_diff_message` emits them. */
fun TagArrayBuilder<StreamMessageDiffEvent>.diffMeta(meta: DiffMeta): TagArrayBuilder<StreamMessageDiffEvent> {
    addUnique(RepoTag.assemble(meta.repoUrl))
    addUnique(CommitTag.assemble(meta.commitSha))
    meta.filePath?.let { addUnique(FileTag.assemble(it)) }
    meta.parentCommit?.let { addUnique(ParentCommitTag.assemble(it)) }
    meta.branch?.let { addUnique(BranchTag.assemble(it)) }
    meta.prNumber?.let { addUnique(PrTag.assemble(it)) }
    meta.language?.let { addUnique(LanguageTag.assemble(it)) }
    meta.description?.let { addUnique(DescriptionTag.assemble(it)) }
    if (meta.truncated) addUnique(TruncatedTag.assemble())
    meta.altText?.let { addUnique(AltTag.assemble(it)) }
    return this
}
