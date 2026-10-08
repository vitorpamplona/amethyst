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
package com.vitorpamplona.quartz.nip34Git.ci.manualTrigger

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.ci.CiRunContext
import com.vitorpamplona.quartz.nip34Git.ci.ciCommits
import com.vitorpamplona.quartz.nip34Git.ci.ciGitRef
import com.vitorpamplona.quartz.nip34Git.ci.ciPullRequest
import com.vitorpamplona.quartz.nip34Git.ci.ciRepositories
import com.vitorpamplona.quartz.nip34Git.ci.ciRunAddressHints
import com.vitorpamplona.quartz.nip34Git.ci.ciRunContext
import com.vitorpamplona.quartz.nip34Git.ci.ciRunEventHints
import com.vitorpamplona.quartz.nip34Git.ci.ciRunLinkedAddressIds
import com.vitorpamplona.quartz.nip34Git.ci.ciRunLinkedEventIds
import com.vitorpamplona.quartz.nip34Git.ci.ciSingleOrNull
import com.vitorpamplona.quartz.nip34Git.ci.ciWorkflow
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 9840 — Nostr CI Manual Trigger (draft "CI Extension to NIP-34").
 *
 * A repository maintainer asks one coordinator (the only `p`) to replay the exact workflow named
 * by `w` at the commit in `c`. It carries the common context tags **except** `o` — a Manual
 * Trigger is always normalized as `manual` — and, for a pull-request run, the NIP-22 PR context
 * without the lowercase participant `p`. The resulting runs quote it with the `manual-trigger`
 * marker. Content is empty.
 *
 * Not searchable (no human text) and not a NIP-22 root scope (a one-shot request; discussion
 * belongs on the PR).
 */
@Immutable
class CiManualTriggerEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider {
    override fun eventHints() = tags.ciRunEventHints()

    /** `ROOT`: the PR (`E`); `PARENT`: the PR or PR Update that supplied the commit (`e`). */
    override fun linkedEventIds() = tags.ciRunLinkedEventIds()

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    /** `COORDINATOR`: the coordinator asked to run (`p`); `ROOT_AUTHOR`: the PR's author (`P`). */
    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey) + listOfNotNull(pullRequest()?.pullRequestAuthor)

    override fun addressHints() = tags.ciRunAddressHints()

    /** `REPOSITORY`: each maintainer's repository announcement (`a`). */
    override fun linkedAddressIds() = tags.ciRunLinkedAddressIds()

    /**
     * The addressed coordinator. Coordinators require exactly one `p` naming themselves, so a
     * second `p` makes the request ambiguous and this returns null (ngit's rule).
     */
    fun coordinator() = tags.ciSingleOrNull(PTag::parseKey)

    fun repositories() = tags.ciRepositories()

    /** The commit to run, first; then any annotated tag object ids that peel to it. */
    fun commits() = tags.ciCommits()

    fun commit() = commits().firstOrNull()

    fun workflow() = tags.ciWorkflow()

    fun gitRef() = tags.ciGitRef()

    fun pullRequest() = tags.ciPullRequest(withSourceAuthor = false)

    companion object {
        const val KIND = 9840

        fun build(
            coordinator: HexKey,
            context: CiRunContext,
            coordinatorRelay: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CiManualTriggerEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            add(PTag.assemble(coordinator, coordinatorRelay))
            ciRunContext(context, withSourceAuthor = false)
            initializer()
        }
    }
}
