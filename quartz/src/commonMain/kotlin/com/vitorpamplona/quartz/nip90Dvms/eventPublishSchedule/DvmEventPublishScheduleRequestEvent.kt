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
package com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip90Dvms.tags.InputTag
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmParamValues
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmRequestLinks
import com.vitorpamplona.quartz.nip90Dvms.tags.inputText
import com.vitorpamplona.quartz.nip90Dvms.tags.inputs
import com.vitorpamplona.quartz.nip90Dvms.tags.param
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class DvmEventPublishScheduleRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
    override fun links(): List<Link<*>> = links { dvmRequestLinks(tags) }

    fun inputs(): List<InputTag> = tags.inputs()

    fun eventJsons(): List<String> = inputs().filter { it.type == "text" }.map { it.value }

    fun relays(): List<String> = tags.dvmParamValues("relays") ?: emptyList()

    companion object {
        const val KIND = 5905

        fun build(
            eventJson: String,
            relays: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DvmEventPublishScheduleRequestEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            inputText(eventJson)
            if (relays.isNotEmpty()) {
                param("relays", *relays.toTypedArray())
            }
            initializer()
        }
    }
}

@Deprecated(
    "Renamed to DvmEventPublishScheduleRequestEvent. NIP-90 Data Vending Machine events use the Dvm prefix.",
    ReplaceWith("DvmEventPublishScheduleRequestEvent", "com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.DvmEventPublishScheduleRequestEvent"),
)
typealias NIP90EventPublishScheduleRequestEvent = DvmEventPublishScheduleRequestEvent
