/*
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.experimental.moneroTips

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Immutable
class TipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider {
    var valueByUser: MutableMap<HexKey, ULong> = mutableMapOf()

    val totalValue: ULong
        get() = valueByUser.values.sum()

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    fun tipProof(): TipProof? =
        try {
            if (content.isNotEmpty()) {
                Json.decodeFromString<TipProof>(content)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("TipEvent", "Can't parse content as tip proof: $content", e)
            null
        }

    companion object {
        const val KIND = 1814

        fun build(
            users: Set<HexKey>,
            txId: String,
            proofs: Map<String, Array<String>>,
            eventId: String? = null,
            message: String = "",
        ): EventTemplate<TipEvent> {
            if (proofs.isEmpty()) {
                throw IllegalArgumentException("No proofs specified")
            }
            if (users.isEmpty()) {
                throw IllegalArgumentException("At least one user must be specified")
            }

            val proof = TipProof(txId, proofs, message.ifEmpty { null })
            val content = Json.encodeToString(TipProof.serializer(), proof)

            return eventTemplate(KIND, content) {
                users.forEach { user ->
                    tag(PTag.assemble(user, null))
                }
                eventId?.let { id ->
                    tag(ETag.assemble(id, null))
                }
            }
        }
    }

    enum class TipType {
        PRIVATE,
        ANONYMOUS,
        PUBLIC,
    }
}

@Serializable
data class TipProof(
    @SerialName("txid") val txId: String,
    val proofs: Map<String, List<String>>,
    val message: String? = null,
)

data class TipSplitSetup(
    val addressOrPubKeyHex: String,
    val relay: String?,
    val weight: Double?,
    val isAddress: Boolean,
)
