/**
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
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
class LnZapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    LnZapEventInterface {
    // This event is also kept in LocalCache (same object)
    @Transient val zapRequest: LnZapRequestEvent?

    // Keeps this as a field because it's a heavier function used everywhere.
    val amount by lazy {
        try {
            lnInvoice()?.let { LnInvoiceUtil.getAmountInSats(it) }
        } catch (e: Exception) {
            Log.e("LnZapEvent", "Failed to Parse LnInvoice ${lnInvoice()}", e)
            null
        }
    }

    override fun countMemory(): Long =
        super.countMemory() +
            pointerSizeInBytes + (zapRequest?.countMemory() ?: 0) + // rough calculation
            pointerSizeInBytes + 36 // bigdecimal size

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

    override fun zappedPost() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    override fun zappedAuthor() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }

    override fun zappedPollOption(): Int? =
        try {
            zapRequest
                ?.tags
                ?.firstOrNull { it.size > 1 && it[0] == POLL_OPTION }
                ?.get(1)
                ?.toInt()
        } catch (e: Exception) {
            Log.e("LnZapEvent", "ZappedPollOption failed to parse", e)
            null
        }

    override fun zappedRequestAuthor(): String? = zapRequest?.pubKey()

    override fun amount() = amount

    override fun content(): String = content

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
