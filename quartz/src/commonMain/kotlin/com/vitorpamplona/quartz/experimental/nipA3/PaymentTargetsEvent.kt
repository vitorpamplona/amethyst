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
package com.vitorpamplona.quartz.experimental.nipA3

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

class PaymentTargetsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 10133

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        suspend fun updatePaymentTargets(
            earlierVersion: PaymentTargetsEvent,
            targets: List<PaymentTarget>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): PaymentTargetsEvent {
            val tags =
                earlierVersion.tags
                    .filter(PaymentTargetTag::notMatch)
                    .plus(targets.map { PaymentTargetTag.assemble(it) })
                    .toTypedArray()

            return signer.sign(createdAt, KIND, tags, earlierVersion.content)
        }

        fun createPaymentTargets(targets: List<PaymentTarget>) = targets.map { PaymentTargetTag.assemble(it) }.toTypedArray()

        suspend fun create(
            targets: List<PaymentTarget>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): PaymentTargetsEvent = signer.sign(createdAt, KIND, createPaymentTargets(targets), "")
    }
}
