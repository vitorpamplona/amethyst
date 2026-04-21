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
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.pr.tags.CurrentCommitTag
import com.vitorpamplona.quartz.nip34Git.pr.tags.MergeBaseTag
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.tags.CloneTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-34 kind 1619 — Pull Request Update.
 *
 * Updates the tip of a pending pull request without creating a new PR
 * event. Uses NIP-22 `E`/`P` tags to reference the parent PR.
 */
@Immutable
class GitPullRequestUpdateEvent(
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
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint) + tags.mapNotNull(RootAuthorTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey) + tags.mapNotNull { RootAuthorTag.parseKey(it) }

    override fun eventHints() = tags.mapNotNull(RootEventTag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(RootEventTag::parseKey)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    fun repository() = tags.firstNotNullOfOrNull(ATag::parse)

    fun repositoryAddress() = tags.firstNotNullOfOrNull(ATag::parseAddress)

    fun parentPullRequestId(): HexKey? = tags.firstNotNullOfOrNull(RootEventTag::parseKey)

    fun parentPullRequestAuthor(): HexKey? = tags.firstNotNullOfOrNull { RootAuthorTag.parseKey(it) }

    fun currentCommit(): String? = tags.firstNotNullOfOrNull(CurrentCommitTag::parse)

    fun cloneUrls(): List<String> = tags.mapNotNull(CloneTag::parse)

    fun earliestUniqueCommit(): String? = tags.firstOrNull { it.size > 1 && it[0] == "r" && it[1].isNotEmpty() }?.get(1)

    fun mergeBase(): String? = tags.firstNotNullOfOrNull(MergeBaseTag::parse)

    companion object {
        const val KIND = 1619
        const val ALT = "A Git Pull Request Update"

        /**
         * Build a NIP-34 kind-1619 pull-request update event.
         *
         * @param parentPullRequest the pull request being updated.
         * @param repository the target repository.
         * @param earliestUniqueCommit repository's earliest unique commit (`r` tag).
         * @param currentCommit new PR tip commit (`c` tag).
         * @param cloneUrls at least one clone URL where [currentCommit] can be fetched.
         * @param mergeBase optional updated merge-base.
         * @param notify optional additional recipient pubkeys.
         */
        fun build(
            parentPullRequest: EventHintBundle<GitPullRequestEvent>,
            repository: EventHintBundle<GitRepositoryEvent>,
            earliestUniqueCommit: String,
            currentCommit: String,
            cloneUrls: List<String>,
            mergeBase: String? = null,
            notify: List<PTag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitPullRequestUpdateEvent>.() -> Unit = {},
        ) = eventTemplate<GitPullRequestUpdateEvent>(KIND, "", createdAt) {
            alt(ALT)
            parentPullRequest(parentPullRequest)
            parentPullRequestAuthor(parentPullRequest)
            repository(repository)
            euc(earliestUniqueCommit)
            pTag(repository.event.pubKey, repository.authorHomeRelay)
            if (notify.isNotEmpty()) pTags(notify)
            currentCommit(currentCommit)
            cloneUrls.forEach { cloneUrl(it) }
            mergeBase?.let { mergeBase(it) }
            initializer()
        }
    }
}
