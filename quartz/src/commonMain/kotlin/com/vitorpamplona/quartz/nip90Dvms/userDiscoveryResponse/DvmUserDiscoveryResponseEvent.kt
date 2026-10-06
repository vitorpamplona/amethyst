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
package com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip90Dvms.DvmResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.tags.parseDvmResultTags
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class DvmUserDiscoveryResponseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : DvmResponseEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    var people: List<HexKey>? = null

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var resultTagsCache: TagArray? = null

    /** The result list: `content` is a JSON tag array of `p` references. Parsed once. */
    fun resultTags(): TagArray = resultTagsCache ?: parseDvmResultTags(content, "DvmUserDiscoveryResponseEvent").also { resultTagsCache = it }

    fun innerTags(): List<HexKey> {
        people?.let { return it }

        return resultTags()
            .mapNotNull {
                if (it.size > 1 && it[0] == "p") {
                    it[1]
                } else {
                    null
                }
            }.also { people = it }
    }

    // The results are public references, just carried in content instead of tags, each with its
    // own relay slot: they feed the hint index here, but stay out of linkedPubKeys(). That runs on
    // every relay copy, and would record the response's arrival relay (the customer's) as a hint
    // for each of the 50-200 results. The linked set stays the base's tag references (customer `p`).
    override fun pubKeyHints(): List<PubKeyHint> = super.pubKeyHints() + resultTags().mapNotNull(PTag::parseAsHint)

    companion object {
        const val KIND = 6301

        fun build(
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DvmUserDiscoveryResponseEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            initializer()
        }
    }
}

@Deprecated(
    "Renamed to DvmUserDiscoveryResponseEvent. NIP-90 Data Vending Machine events use the Dvm prefix.",
    ReplaceWith("DvmUserDiscoveryResponseEvent", "com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse.DvmUserDiscoveryResponseEvent"),
)
typealias NIP90UserDiscoveryResponseEvent = DvmUserDiscoveryResponseEvent
