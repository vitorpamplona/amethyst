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
package com.vitorpamplona.quartz.nip34Git.patch

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.patch.tags.CommitPgpSigTag
import com.vitorpamplona.quartz.nip34Git.patch.tags.CommitTag
import com.vitorpamplona.quartz.nip34Git.patch.tags.Committer
import com.vitorpamplona.quartz.nip34Git.patch.tags.CommitterTag
import com.vitorpamplona.quartz.nip34Git.patch.tags.ParentCommitTag
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GitPatchEvent(
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

    override fun eventHints() = tags.mapNotNull(MarkedETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(MarkedETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    private fun innerRepository() =
        tags.firstOrNull { it.size > 3 && it[0] == "a" && it[3] == "root" }
            ?: tags.firstOrNull { it.size > 1 && it[0] == "a" }

    fun repositoryAddress() =
        innerRepository()?.let {
            if (it.size > 1) {
                Address.parse(it[1])
            } else {
                null
            }
        }

    fun repository() =
        innerRepository()?.let {
            if (it.size > 1) {
                val aTagValue = it[1]
                val relay = it.getOrNull(2)

                ATag.parse(aTagValue, relay)
            } else {
                null
            }
        }

    fun commit() = tags.firstNotNullOfOrNull(CommitTag::parse)

    fun parentCommit() = tags.firstNotNullOfOrNull(ParentCommitTag::parse)

    fun commitPGPSig() = tags.firstNotNullOfOrNull(CommitPgpSigTag::parse)

    fun committer() = tags.mapNotNull(CommitterTag::parse)

    /** Earliest unique commit of the target repository, encoded as `["r", <commit>]`. */
    fun earliestUniqueCommit(): String? = tags.firstOrNull { it.size > 1 && it[0] == "r" && it[1].isNotEmpty() }?.get(1)

    /** `true` if this event is tagged `["t", "root"]` (root of a patch series). */
    fun isRoot(): Boolean = tags.any { HashtagTag.isTagged(it, ROOT) }

    /** `true` if this event is tagged `["t", "root-revision"]` (root of a revision series). */
    fun isRootRevision(): Boolean = tags.any { HashtagTag.isTagged(it, ROOT_REVISION) }

    companion object {
        const val KIND = 1617
        const val ALT = "A Git Patch"
        const val ROOT = "root"
        const val ROOT_REVISION = "root-revision"

        /**
         * Build a NIP-34 kind-1617 patch event with all required tags.
         *
         * @param patch the raw `git format-patch` output for the content field.
         * @param repository an [EventHintBundle] pointing at the target repository announcement.
         * @param earliestUniqueCommit the repository's earliest unique commit ID, used as the `r` tag.
         * @param commit optional current commit hash (`commit` tag).
         * @param parentCommit optional parent commit hash (`parent-commit` tag).
         * @param commitPgpSig optional PGP signature or empty string for unsigned (`commit-pgp-sig` tag).
         * @param committer optional committer metadata (`committer` tag).
         * @param notify additional recipient pubkeys, beyond the repo owner who is always included.
         * @param root set to `true` to tag the patch as the root of a new patch series (`["t", "root"]`).
         * @param rootRevision set to `true` to tag the patch as the root of a revision series (`["t", "root-revision"]`).
         */
        fun build(
            patch: String,
            repository: EventHintBundle<GitRepositoryEvent>,
            earliestUniqueCommit: String,
            commit: String? = null,
            parentCommit: String? = null,
            commitPgpSig: String? = null,
            committer: Committer? = null,
            notify: List<PTag> = emptyList(),
            root: Boolean = false,
            rootRevision: Boolean = false,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitPatchEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, patch, createdAt) {
            alt(ALT)
            repository(repository)
            euc(earliestUniqueCommit)
            pTag(repository.event.pubKey, repository.authorHomeRelay)
            if (notify.isNotEmpty()) pTags(notify)
            commit?.let { commit(it) }
            parentCommit?.let { parentCommit(it) }
            commitPgpSig?.let { commitPgpSig(it) }
            committer?.let {
                committer(it.name, it.email, it.timestamp, it.timezoneInMinutes)
            }
            if (root) hashtag(ROOT)
            if (rootRevision) hashtag(ROOT_REVISION)
            initializer()
        }

        /**
         * Build a patch that replies to an earlier patch in the same series,
         * adding NIP-10 `e` reply tags that point at the previous patch.
         */
        fun reply(
            patch: String,
            repository: EventHintBundle<GitRepositoryEvent>,
            earliestUniqueCommit: String,
            replyingTo: EventHintBundle<GitPatchEvent>,
            commit: String? = null,
            parentCommit: String? = null,
            commitPgpSig: String? = null,
            committer: Committer? = null,
            notify: List<PTag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitPatchEvent>.() -> Unit = {},
        ) = build(
            patch = patch,
            repository = repository,
            earliestUniqueCommit = earliestUniqueCommit,
            commit = commit,
            parentCommit = parentCommit,
            commitPgpSig = commitPgpSig,
            committer = committer,
            notify = notify,
            createdAt = createdAt,
        ) {
            add(
                MarkedETag.assemble(
                    replyingTo.event.id,
                    replyingTo.relay,
                    MarkedETag.MARKER.REPLY,
                    replyingTo.event.pubKey,
                ),
            )
            initializer()
        }
    }
}
