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
package com.vitorpamplona.quartz.nip34Git.ci.requestReadiness

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.ci.ciBoundedExpiration
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 19844 — Nostr CI Request-Readiness List (draft "CI Extension to NIP-34").
 *
 * A coordinator's public NIP-51 standard list of the repositories for which it is ready to act on
 * an otherwise valid Service Request (9843). An `a` item covers exactly that repository; a `p`
 * item covers every repository rooted at that pubkey. Content is empty (private items are
 * unsupported), there is no `d`, and the single `expiration` is at most 24 hours after
 * `created_at`. The list is an optional, non-exhaustive discovery hint, actionable only while the
 * same pubkey has a live Coordinator Advertisement (19843).
 *
 * Items "MUST NOT have a fourth marker": a 4-element `a`/`p` is not an item and is skipped.
 * Not searchable (no human text).
 */
@Immutable
class CiRequestReadinessListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    PubKeyHintProvider {
    // Kind 19844 is replaceable: NIP-01 fixes its address to `kind:pubkey:`.
    override fun dTag(): String = ""

    override fun addressHints() = tags.readyRepositoryHints()

    /** `READY_FOR`: each repository the coordinator is ready to serve on request (`a`). */
    override fun linkedAddressIds() = tags.readyRepositoryAddressIds()

    override fun pubKeyHints() = tags.readyMaintainerHints()

    /** `READY_FOR`: each maintainer whose repositories the coordinator is ready to serve on request (`p`). */
    override fun linkedPubKeys() = tags.readyMaintainers().map { it.pubKey }

    fun repositories() = tags.readyRepositories()

    fun maintainers() = tags.readyMaintainers()

    fun expiration() = tags.expiration()

    /** The expiration, when it is later than `created_at` and within the spec's 24-hour bound. */
    fun validExpiration() = tags.ciBoundedExpiration(createdAt, MAX_EXPIRATION_SECONDS)

    fun isLive(now: Long = TimeUtils.now()) = validExpiration()?.let { it > now } ?: false

    companion object {
        const val KIND = 19844
        const val MAX_EXPIRATION_SECONDS = 24 * 60 * 60L

        fun build(
            repositories: List<ATag>,
            maintainers: List<PTag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            expiresAt: Long = createdAt + MAX_EXPIRATION_SECONDS,
            initializer: TagArrayBuilder<CiRequestReadinessListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            readyRepositories(repositories)
            readyMaintainers(maintainers)
            expiration(expiresAt)
            initializer()
        }
    }
}
