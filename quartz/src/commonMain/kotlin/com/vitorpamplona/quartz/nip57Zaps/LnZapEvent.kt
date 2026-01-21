/**
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
package com.vitorpamplona.quartz.nip57Zaps

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.zapPolls.tags.PollOptionTag
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.Log

@Immutable
class LnZapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    LnZapEventInterface,
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    // This event is also kept in LocalCache (same object)
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    val zapRequest: LnZapRequestEvent?

    // Keeps this as a field because it's a heavier function used everywhere.
    val amount by lazy {
        try {
            lnInvoice()?.let { LnInvoiceUtil.getAmountInSats(it) }
        } catch (e: Exception) {
            Log.e("LnZapEvent", "Failed to Parse LnInvoice ${lnInvoice()}", e)
            null
        }
    }

    override fun containedPost(): LnZapRequestEvent? =
        try {
            description()?.ifBlank { null }?.let { fromJson(it) } as? LnZapRequestEvent
        } catch (e: Exception) {
            Log.w("LnZapEvent", "Failed to Parse Contained Post ${description()} in event $id", e)
            null
        }

    init {
        zapRequest = containedPost()
    }

    override fun zappedPost() = tags.mapNotNull(ETag::parseId)

    override fun zappedAuthor() = tags.mapNotNull(PTag::parseKey)

    override fun zappedPollOption(): Int? =
        try {
            zapRequest
                ?.tags
                ?.firstOrNull { it.size > 1 && it[0] == PollOptionTag.TAG_NAME }
                ?.get(1)
                ?.toInt()
        } catch (e: Exception) {
            Log.e("LnZapEvent", "ZappedPollOption failed to parse", e)
            null
        }

    override fun zappedRequestAuthor(): String? = zapRequest?.pubKey

    override fun amount() = amount

    fun lnInvoice() = tags.firstOrNull { it.size > 1 && it[0] == "bolt11" }?.get(1)

    private fun description() = tags.firstOrNull { it.size > 1 && it[0] == "description" }?.get(1)

    companion object {
        const val KIND = 9735
        const val ALT = "Zap event"
    }

    enum class ZapType {
        PUBLIC,
        PRIVATE,
        ANONYMOUS,
        NONZAP,
    }
}
