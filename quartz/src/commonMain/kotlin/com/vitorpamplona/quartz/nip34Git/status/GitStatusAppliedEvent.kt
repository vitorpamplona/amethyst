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
package com.vitorpamplona.quartz.nip34Git.status

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip34Git.status.tags.AppliedAsCommitsTag
import com.vitorpamplona.quartz.nip34Git.status.tags.MergeCommitTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-34 kind 1631 — Status: Applied / Merged / Resolved.
 *
 * Reports that a patch/PR/issue has been applied, merged, or resolved. In
 * addition to the common status tags, kind 1631 optionally carries:
 *
 * - `q` tags pointing at the specific applied patch event(s);
 * - `merge-commit` with the merge commit hash;
 * - `applied-as-commits` listing the individual commit hashes produced by
 *   applying the patch series.
 */
@Immutable
class GitStatusAppliedEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : GitStatusEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun mergeCommit(): String? = tags.firstNotNullOfOrNull(MergeCommitTag::parse)

    fun appliedAsCommits(): List<String> = tags.mapNotNull(AppliedAsCommitsTag::parse).flatten()

    fun appliedPatchIds(): List<HexKey> = tags.mapNotNull(QTag::parseEventId)

    companion object {
        const val KIND = KIND_APPLIED
        const val ALT = "A Git Applied Status"

        fun <T : Event> build(
            content: String,
            target: EventHintBundle<T>,
            appliedPatches: List<EventHintBundle<com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent>> = emptyList(),
            mergeCommit: String? = null,
            appliedAsCommits: List<String> = emptyList(),
            notify: List<PTag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitStatusAppliedEvent>.() -> Unit = {},
        ) = GitStatusBuilders.buildStatus<GitStatusAppliedEvent, T>(
            kind = KIND,
            altDescriptor = ALT,
            content = content,
            target = target,
            notify = notify,
            createdAt = createdAt,
        ) {
            appliedPatches.forEach { patch ->
                add(
                    QEventTag.assemble(
                        patch.event.id,
                        patch.relay,
                        patch.event.pubKey,
                    ),
                )
            }
            mergeCommit?.let { add(MergeCommitTag.assemble(it)) }
            if (appliedAsCommits.isNotEmpty()) {
                add(AppliedAsCommitsTag.assemble(appliedAsCommits))
            }
            initializer()
        }
    }
}
