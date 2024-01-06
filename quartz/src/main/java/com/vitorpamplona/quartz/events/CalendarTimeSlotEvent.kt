/**
 * Copyright (c) 2023 Vitor Pamplona
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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class CalendarTimeSlotEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun location() = tags.firstOrNull { it.size > 1 && it[0] == "location" }?.get(1)

    fun start() = tags.firstOrNull { it.size > 1 && it[0] == "start" }?.get(1)?.toLongOrNull()

    fun end() = tags.firstOrNull { it.size > 1 && it[0] == "end" }?.get(1)?.toLongOrNull()

    fun startTmz() = tags.firstOrNull { it.size > 1 && it[0] == "start_tzid" }?.get(1)?.toLongOrNull()

    fun endTmz() = tags.firstOrNull { it.size > 1 && it[0] == "end_tzid" }?.get(1)?.toLongOrNull()

    //    ["start", "<Unix timestamp in seconds>"],
    //    ["end", "<Unix timestamp in seconds>"],
    //    ["start_tzid", "<IANA Time Zone Database identifier>"],
    //    ["end_tzid", "<IANA Time Zone Database identifier>"],

    companion object {
        const val KIND = 31923
        const val ALT = "Calendar time-slot event"

        fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CalendarTimeSlotEvent) -> Unit,
        ) {
            val tags = arrayOf(arrayOf("alt", ALT))
            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }
}
