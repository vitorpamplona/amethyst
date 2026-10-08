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
package com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip34Git.ci.ciBoundedExpiration
import com.vitorpamplona.quartz.nip34Git.ci.ciRunnerFamilies
import com.vitorpamplona.quartz.nip34Git.ci.ciRunnerSelectors
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiAdmissionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiBillingPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiExecutionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiRunnerSelector
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiSecretsKey
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiSoftware
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 19843 — Nostr CI Coordinator Advertisement (draft "CI Extension to NIP-34").
 *
 * A normal replaceable event (one per coordinator pubkey) advertising current availability and
 * capabilities: `software`, runner families (`W`) and `<family>:<selector>` runner selectors (`R`),
 * the admission (`M`), execution (`X`) and optional billing (`B`) policies, an optional
 * `secrets-key` recipient for Repository Secret Updates (29846), and an `expiration` at most 30
 * minutes after `created_at`. Content is empty and there is no `d`.
 *
 * Policies: an unknown `M`/`X`/`B` value reads as `UNKNOWN`, and a repeated tag (the spec requires
 * exactly one `M`/`X`, at most one `B`) also reads as `UNKNOWN` — never as open, automatic or free.
 *
 * No graph edges: the `secrets-key` is an encryption key, not a user, and runner labels are not
 * references. Not searchable (no human text).
 */
@Immutable
class CiCoordinatorAdvertisementEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    // Kind 19843 is replaceable: NIP-01 fixes its address to `kind:pubkey:`, so a stray `d`
    // tag must not split one coordinator's advertisement into several addresses.
    override fun dTag(): String = ""

    fun software() = tags.software()

    /** Supported runner families, case-folded and deduplicated. */
    fun runnerFamilies() = tags.ciRunnerFamilies()

    /**
     * Accepted runner selectors, case-folded and deduplicated, keeping only those whose family is
     * advertised in a `W` tag (the spec: "its family MUST appear in a `W` tag").
     */
    fun runnerSelectors() = tags.ciRunnerSelectors()

    fun admission() = tags.admission()

    fun execution() = tags.execution()

    fun billing() = tags.billing()

    /** The current secret-update recipient; null means the coordinator is not accepting secret updates. */
    fun secretsKey() = tags.secretsKey()

    fun acceptsSecretUpdates() = secretsKey() != null

    fun expiration() = tags.expiration()

    /** The expiration, when it is later than `created_at` and within the spec's 30-minute bound. */
    fun validExpiration() = tags.ciBoundedExpiration(createdAt, MAX_EXPIRATION_SECONDS)

    /** Whether the advertisement is live at [now]. A 19844 or 39844 is only actionable while one is. */
    fun isLive(now: Long = TimeUtils.now()) = validExpiration()?.let { it > now } ?: false

    companion object {
        const val KIND = 19843
        const val MAX_EXPIRATION_SECONDS = 30 * 60L

        fun build(
            software: CiSoftware,
            runnerFamilies: List<String>,
            runnerSelectors: List<CiRunnerSelector>,
            admission: CiAdmissionPolicy,
            execution: CiExecutionPolicy,
            billing: CiBillingPolicy? = null,
            secretsKey: CiSecretsKey? = null,
            createdAt: Long = TimeUtils.now(),
            expiresAt: Long = createdAt + MAX_EXPIRATION_SECONDS,
            initializer: TagArrayBuilder<CiCoordinatorAdvertisementEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            software(software)
            ciRunnerFamilies(runnerFamilies)
            ciRunnerSelectors(runnerSelectors)
            admission(admission)
            execution(execution)
            billing?.let { billing(it) }
            secretsKey?.let { secretsKey(it) }
            expiration(expiresAt)
            initializer()
        }
    }
}
