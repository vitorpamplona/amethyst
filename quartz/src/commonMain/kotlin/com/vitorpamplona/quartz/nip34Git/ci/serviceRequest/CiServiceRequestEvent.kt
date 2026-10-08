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
package com.vitorpamplona.quartz.nip34Git.ci.serviceRequest

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.ci.ciSingleOrNull
import com.vitorpamplona.quartz.nip34Git.ci.tags.RepositoryTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 9843 — Nostr CI Service Request (draft "CI Extension to NIP-34").
 *
 * A requester asks one coordinator (`p`) to provide standing service for one repository
 * perspective (`a`). Exactly one of each; no `d`, no `expiration`, empty content. It admits no
 * unknown repository and grants no authority: by default a coordinator only accepts one signed by
 * a current maintainer. Requests and Stops (9844) are totally ordered — a greater `created_at` is
 * later, and at equal timestamps the lexicographically lower event id is later.
 *
 * Not searchable (no human text) and not a NIP-22 root scope.
 */
@Immutable
class CiServiceRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    AddressHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    /** `COORDINATOR`: the coordinator asked to serve the repository (`p`). */
    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun addressHints() = tags.mapNotNull(RepositoryTag::parseAsHint)

    /** `REPOSITORY`: the repository perspective the request covers (`a`). */
    override fun linkedAddressIds() = tags.mapNotNull(RepositoryTag::parseAddressId)

    /** The single repository perspective; null when absent or repeated (the spec requires exactly one). */
    fun repository() = tags.ciSingleOrNull(RepositoryTag::parse)

    /** The single addressed coordinator; null when absent or repeated. */
    fun coordinator() = tags.ciSingleOrNull(PTag::parseKey)

    companion object {
        const val KIND = 9843

        fun build(
            repository: ATag,
            coordinator: HexKey,
            coordinatorRelay: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CiServiceRequestEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            addUnique(RepositoryTag.assemble(repository))
            addUnique(PTag.assemble(coordinator, coordinatorRelay))
            initializer()
        }
    }
}
