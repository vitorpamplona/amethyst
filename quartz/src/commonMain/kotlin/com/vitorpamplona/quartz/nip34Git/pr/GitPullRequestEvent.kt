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
package com.vitorpamplona.quartz.nip34Git.pr

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip14Subject.SubjectTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.pr.tags.BranchNameTag
import com.vitorpamplona.quartz.nip34Git.pr.tags.CurrentCommitTag
import com.vitorpamplona.quartz.nip34Git.pr.tags.MergeBaseTag
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.tags.CloneTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-34 kind 1618 — Pull Request.
 *
 * Proposes merging a branch described by a `clone` URL and a current commit
 * tip (`c`) without inlining a patch. The markdown content describes the
 * change. If this is a revision of a previous patch, an `e` tag points at
 * the root patch.
 */
@Immutable
class GitPullRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    EventHintProvider,
    AddressHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    fun repository() = tags.firstNotNullOfOrNull(ATag::parse)

    fun repositoryAddress() = tags.firstNotNullOfOrNull(ATag::parseAddress)

    fun earliestUniqueCommit(): String? = tags.firstOrNull { it.size > 1 && it[0] == "r" && it[1].isNotEmpty() }?.get(1)

    fun currentCommit(): String? = tags.firstNotNullOfOrNull(CurrentCommitTag::parse)

    fun cloneUrls(): List<String> = tags.mapNotNull(CloneTag::parse)

    fun subject(): String? = tags.firstNotNullOfOrNull(SubjectTag::parse)

    fun labels(): List<String> = tags.hashtags()

    fun branchName(): String? = tags.firstNotNullOfOrNull(BranchNameTag::parse)

    fun mergeBase(): String? = tags.firstNotNullOfOrNull(MergeBaseTag::parse)

    /** Root patch event ID if this PR is a revision of a prior patch. */
    fun rootPatchId(): HexKey? = tags.firstNotNullOfOrNull(ETag::parseId)

    companion object {
        const val KIND = 1618
        const val ALT = "A Git Pull Request"

        /**
         * Build a NIP-34 kind-1618 pull request event.
         *
         * @param description markdown description of the pull request.
         * @param repository event-hint bundle for the target repository.
         * @param earliestUniqueCommit repository's earliest unique commit (`r` tag).
         * @param currentCommit tip commit of the PR branch (`c` tag).
         * @param cloneUrls at least one clone URL where [currentCommit] can be fetched.
         * @param subject optional PR subject.
         * @param labels optional PR labels (encoded as `t` tags).
         * @param branchName optional recommended local checkout branch name.
         * @param mergeBase optional most recent common ancestor with the target branch.
         * @param rootPatch optional pointer to a prior patch this PR supersedes.
         * @param notify additional recipient pubkeys.
         */
        fun build(
            description: String,
            repository: EventHintBundle<GitRepositoryEvent>,
            earliestUniqueCommit: String,
            currentCommit: String,
            cloneUrls: List<String>,
            subject: String? = null,
            labels: List<String> = emptyList(),
            branchName: String? = null,
            mergeBase: String? = null,
            rootPatch: EventHintBundle<com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent>? = null,
            notify: List<PTag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitPullRequestEvent>.() -> Unit = {},
        ) = eventTemplate<GitPullRequestEvent>(KIND, description, createdAt) {
            alt(ALT)
            repository(repository)
            euc(earliestUniqueCommit)
            pTag(repository.event.pubKey, repository.authorHomeRelay)
            if (notify.isNotEmpty()) pTags(notify)
            currentCommit(currentCommit)
            cloneUrls.forEach { cloneUrl(it) }
            subject?.let { subject(it) }
            if (labels.isNotEmpty()) hashtags(labels)
            branchName?.let { branchName(it) }
            mergeBase?.let { mergeBase(it) }
            rootPatch?.let { rootPatch(it) }
            initializer()
        }
    }
}
