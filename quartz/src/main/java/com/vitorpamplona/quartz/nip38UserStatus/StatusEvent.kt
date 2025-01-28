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
package com.vitorpamplona.quartz.nip38UserStatus

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class StatusEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun firstTaggedUrl() = tags.firstTagValue("r")

    companion object {
        const val KIND = 30315

        fun create(
            msg: String,
            type: String,
            expiration: Long?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (StatusEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()

            tags.add(arrayOf("d", type))
            expiration?.let { tags.add(arrayOf("expiration", it.toString())) }

            signer.sign(createdAt, KIND, tags.toTypedArray(), msg, onReady)
        }

        fun update(
            event: StatusEvent,
            newStatus: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (StatusEvent) -> Unit,
        ) {
            val tags = event.tags
            signer.sign(createdAt, KIND, tags, newStatus, onReady)
        }

        fun clear(
            event: StatusEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (StatusEvent) -> Unit,
        ) {
            val msg = ""
            val tags = event.tags.filter { it.size > 1 && it[0] == "d" }
            signer.sign(createdAt, KIND, tags.toTypedArray(), msg, onReady)
        }
    }
}
