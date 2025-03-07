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
package com.vitorpamplona.quartz.nip90Dvms

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class NIP90StatusEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    class StatusCode(
        val code: String,
        val description: String,
    )

    class AmountInvoice(
        val amount: Long?,
        val lnInvoice: String?,
    )

    fun status(): StatusCode? =
        tags.firstOrNull { it.size > 1 && it[0] == "status" }?.let {
            if (it.size > 2 && content == "") {
                StatusCode(it[1], it[2])
            } else {
                StatusCode(it[1], content)
            }
        }

    fun firstAmount(): AmountInvoice? =
        tags.firstOrNull { it.size > 1 && it[0] == "amount" }?.let {
            val amount = it[1].toLongOrNull()
            if (it.size > 2) {
                if (it[2].isNotBlank()) {
                    AmountInvoice(amount, it[2])
                } else {
                    null
                }
            } else {
                if (amount != null) {
                    AmountInvoice(amount, null)
                } else {
                    null
                }
            }
        }

    companion object {
        const val KIND = 7000
        const val ALT = "NIP90 Status update"

        fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AppRecommendationEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    AltTag.assemble(ALT),
                )
            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }
}
