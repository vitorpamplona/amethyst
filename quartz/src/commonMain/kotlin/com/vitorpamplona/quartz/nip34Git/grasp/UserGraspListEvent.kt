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
package com.vitorpamplona.quartz.nip34Git.grasp

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.grasp.tags.GraspTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-34 kind 10317 — User Grasp Server List.
 *
 * Declares a user's preferred grasp (Git-over-Nostr hosting) servers in
 * preference order. Functions analogously to NIP-65's relay list: clients
 * use it to discover where to push PR tip branches under
 * `refs/nostr/<pr-event-id>`.
 */
@Immutable
class UserGraspListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** Grasp server URLs in preference order (first = most preferred). */
    fun grasps(): List<String> = tags.mapNotNull(GraspTag::parse)

    fun graspsNorm(): List<NormalizedRelayUrl> = tags.mapNotNull(GraspTag::parseNorm)

    companion object {
        const val KIND = 10317
        const val ALT = "Preferred grasp servers for Git over Nostr"

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun build(
            grasps: List<String>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<UserGraspListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT)
            grasps.forEach { add(GraspTag.assemble(it)) }
            initializer()
        }

        fun buildNorm(
            grasps: List<NormalizedRelayUrl>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<UserGraspListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT)
            grasps.forEach { add(GraspTag.assemble(it)) }
            initializer()
        }
    }
}
