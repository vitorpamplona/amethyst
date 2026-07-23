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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip31Alts.AltTag

/** The `h` channel id this event is scoped to, or null when absent. */
fun TagArray.channel(): String? = firstNotNullOfOrNull(GroupIdTag::parse)

/** All `e`-tagged target message ids, in order. */
fun TagArray.targetMessages(): List<HexKey> = mapNotNull(ETag::parseId)

/** The first `e`-tagged target message id, or null. */
fun TagArray.targetMessage(): HexKey? = firstNotNullOfOrNull(ETag::parseId)

/** All `p`-tagged mention pubkeys, in order. */
fun TagArray.mentions(): List<HexKey> = mapNotNull(PTag::parseKey)

/** True when a `["broadcast", "1"]` tag is present. */
fun TagArray.isBroadcast(): Boolean = firstNotNullOfOrNull(BroadcastTag::parse) ?: false

/**
 * Reassembles the [DiffMeta] carried in the tags of a [StreamMessageDiffEvent], or
 * null when the required `repo` / `commit` tags are missing.
 */
fun TagArray.diffMeta(): DiffMeta? {
    val repo = firstNotNullOfOrNull(RepoTag::parse) ?: return null
    val commit = firstNotNullOfOrNull(CommitTag::parse) ?: return null
    return DiffMeta(
        repoUrl = repo,
        commitSha = commit,
        filePath = firstNotNullOfOrNull(FileTag::parse),
        parentCommit = firstNotNullOfOrNull(ParentCommitTag::parse),
        branch = firstNotNullOfOrNull(BranchTag::parse),
        prNumber = firstNotNullOfOrNull(PrTag::parse),
        language = firstNotNullOfOrNull(LanguageTag::parse),
        description = firstNotNullOfOrNull(DescriptionTag::parse),
        truncated = firstNotNullOfOrNull(TruncatedTag::parse) ?: false,
        altText = firstNotNullOfOrNull(AltTag::parse),
    )
}
