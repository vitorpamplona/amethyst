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
package com.vitorpamplona.quartz.nip34Git.ci.repositoryStatus

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip34Git.ci.ciBoundedExpiration
import com.vitorpamplona.quartz.nip34Git.ci.ciRepositories
import com.vitorpamplona.quartz.nip34Git.ci.ciRepositoryAddressIds
import com.vitorpamplona.quartz.nip34Git.ci.ciRepositoryHints
import com.vitorpamplona.quartz.nip34Git.ci.ciRunnerFamilies
import com.vitorpamplona.quartz.nip34Git.ci.ciRunnerSelectors
import com.vitorpamplona.quartz.nip34Git.ci.ciSingleOrNull
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiRepositorySecret
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiRunnerSelector
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiServiceState
import com.vitorpamplona.quartz.nip34Git.ci.tags.RepositoryTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.ServiceStateTag
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 39844 — Nostr CI Coordinator Repository Status (draft "CI Extension to NIP-34").
 *
 * The effective service a coordinator provides for one selected repository root. `d` is that
 * root's exact repository address; the first `a` repeats it and the remaining `a`s list the rest
 * of its current maintainer closure. `s` is `acting` while the coordinator accepts eligible
 * triggers; `W`, `workflow-path` and `R` are its effective capabilities; each `secret` discloses an
 * effective secret by name — never by value — with its provisioning maintainer and update time.
 * The `expiration` is at most 24 hours after `created_at`. Content is empty.
 *
 * Actionable only while the same pubkey has a live Coordinator Advertisement (19843). A coordinator
 * that stops acting deletes the coordinate with NIP-09. Not searchable (no human text).
 */
@Immutable
class CiRepositoryStatusEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    PubKeyHintProvider {
    override fun addressHints() = tags.ciRepositoryHints()

    /** `REPOSITORY`: the selected repository root (first `a`, equal to `d`) and the rest of its maintainer closure. */
    override fun linkedAddressIds() = tags.ciRepositoryAddressIds()

    override fun pubKeyHints() = emptyList<PubKeyHint>()

    /** `SECRET_ORIGIN`: the maintainers who provisioned an effective secret through a 29846 (`secret` slot 2). */
    override fun linkedPubKeys() = secrets().mapNotNull { it.origin }.distinct()

    /** The selected repository root, from `d`; null when `d` is not a 30617 repository address. */
    fun selectedRepository(): Address? = AddressSerializer.parse(dTag())?.takeIf { RepositoryTag.isRepository(it) }

    /** The selected root first, then its maintainer closure, as the tags list them. */
    fun repositories() = tags.ciRepositories()

    fun state() = tags.ciSingleOrNull(ServiceStateTag::parse)

    fun isActing() = state() == CiServiceState.ACTING

    fun runnerFamilies() = tags.ciRunnerFamilies()

    fun runnerSelectors() = tags.ciRunnerSelectors()

    fun workflowPaths() = tags.workflowPaths()

    /** Effective secrets by name. None means the coordinator has no effective secrets for this repository. */
    fun secrets() = tags.secrets()

    fun expiration() = tags.expiration()

    /** The expiration, when it is later than `created_at` and within the spec's 24-hour bound. */
    fun validExpiration() = tags.ciBoundedExpiration(createdAt, MAX_EXPIRATION_SECONDS)

    fun isLive(now: Long = TimeUtils.now()) = validExpiration()?.let { it > now } ?: false

    companion object {
        const val KIND = 39844
        const val MAX_EXPIRATION_SECONDS = 24 * 60 * 60L

        /**
         * @param selected the selected repository root; it becomes `d` and the first `a`.
         * @param maintainerRepositories the rest of the maintainer closure, written after it.
         */
        fun build(
            selected: ATag,
            maintainerRepositories: List<ATag>,
            state: CiServiceState,
            runnerFamilies: List<String>,
            workflowPaths: List<String>,
            runnerSelectors: List<CiRunnerSelector>,
            secrets: List<CiRepositorySecret> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            expiresAt: Long = createdAt + MAX_EXPIRATION_SECONDS,
            initializer: TagArrayBuilder<CiRepositoryStatusEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(selected.toTag())
            statusRepositories(selected, maintainerRepositories)
            serviceState(state)
            ciRunnerFamilies(runnerFamilies)
            workflowPaths(workflowPaths)
            ciRunnerSelectors(runnerSelectors)
            secrets(secrets)
            expiration(expiresAt)
            initializer()
        }
    }
}
