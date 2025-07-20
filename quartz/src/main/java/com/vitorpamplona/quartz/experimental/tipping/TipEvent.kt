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
package com.vitorpamplona.quartz.experimental.tipping

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

class TipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    val amount get() = tags.firstOrNull { it.size > 1 && it[0] == "amount" }?.get(1)?.toBigDecimalOrNull() ?: 0.0.toBigDecimal()

    fun tippedPost() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    fun tippedAuthor() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }

    companion object {
        const val KIND = 1814
        const val ALT = "Tip"

        fun build(
            event: Event?,
            users: List<PTag>,
            message: String = "",
            amount: Double = 0.0,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TipEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            alt(ALT)
            users.forEach {
                pTag(it)
            }
            addUnique(arrayOf("amount", amount.toString()))

            event?.let {
                if (it is AddressableEvent) {
                    add(arrayOf("a", it.addressTag()))
                } else {
                    add(arrayOf("e", it.id))
                }
            }

            initializer()
        }
    }

    enum class TipType {
        ANONYMOUS,
        PUBLIC,
        NONTIP,
    }
}
