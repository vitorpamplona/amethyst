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
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip57Zaps.tags.ZapSenderTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log

@Immutable
class ZapReceiptEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    ZapReceiptEventInterface,
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    // The zap comment lives in the embedded request, which init{} already parses
    // into [zapRequest] — so indexing it adds no extra parse cost.
    override fun indexableContent() = zapRequest?.content.orEmpty()

    // The read path: the same fields indexableContent() joins, without the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        // Null rather than the empty string the joined form yields: the visitor
        // drops nulls, so both sides still produce the same text.
        visitor.visit(zapRequest?.content)
    }

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    // The zapped `p` recipients, plus the sender of a public zap. The sender of an anonymous
    // or private zap (an `anon` tag in the embedded request) is a throwaway or derived key:
    // linking it would create a User, fetch metadata that never exists and record a relay hint
    // for it on every receipt. The sender is read from the embedded request, not from the
    // receipt's `P` tag, because only the request says whether the zap was anonymous; `P` is a
    // copy of that request's author anyway (NIP-57). Only `p` has a relay slot, so
    // [pubKeyHints] stays `p`-only.
    override fun linkedPubKeys(): List<HexKey> {
        val recipients = zappedAuthor()
        val request = zapRequest ?: return recipients
        if (request.hasAnonTag()) return recipients
        val sender = zappedRequestAuthor() ?: return recipients
        return recipients + sender
    }

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = zappedPost()

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = zappedAddresses()

    // This event is also kept in LocalCache (same object)
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    val zapRequest: ZapRequestEvent?

    // Keeps this as a field because it's a heavier function used everywhere.
    val amount by lazy {
        try {
            lnInvoice()?.let { LnInvoiceUtil.getAmountInSats(it) }
        } catch (e: Exception) {
            Log.e("ZapReceiptEvent", "Failed to Parse LnInvoice ${lnInvoice()}", e)
            null
        }
    }

    override fun containedPost(): ZapRequestEvent? =
        try {
            description()?.ifBlank { null }?.let { fromJson(it) } as? ZapRequestEvent
        } catch (e: Exception) {
            Log.w("ZapReceiptEvent", "Failed to Parse Contained Post ${description()} in event $id", e)
            null
        }

    init {
        zapRequest = containedPost()
    }

    override fun zappedPost() = tags.mapNotNull(ETag::parseId)

    override fun zappedAuthor() = tags.mapNotNull(PTag::parseKey)

    override fun zappedAddresses(): List<String> = tags.mapNotNull(ATag::parseValidAddress)

    /** NIP-57's uppercase `P`: the zap sender, as the zap service copied it from the request. */
    fun zapSender(): HexKey? = tags.firstNotNullOfOrNull(ZapSenderTag::parseKey)

    override fun zappedPollOption(): Int? =
        try {
            zapRequest
                ?.tags
                ?.firstOrNull { it.size > 1 && it[0] == PollOptionTag.TAG_NAME }
                ?.get(1)
                ?.toInt()
        } catch (e: Exception) {
            Log.e("ZapReceiptEvent", "ZappedPollOption failed to parse", e)
            null
        }

    /** The embedded request's author: the sender, or the throwaway key of an anonymous/private zap. Null when not a valid key. */
    override fun zappedRequestAuthor(): String? = zapRequest?.pubKey?.takeIf { it.length == 64 && Hex.isHex64(it) }

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

@Deprecated(
    "Renamed to ZapReceiptEvent. NIP-57 names kind 9735 the zap receipt.",
    ReplaceWith("ZapReceiptEvent", "com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent"),
)
typealias LnZapEvent = ZapReceiptEvent
