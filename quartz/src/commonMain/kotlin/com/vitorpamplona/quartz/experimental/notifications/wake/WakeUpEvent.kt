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
package com.vitorpamplona.quartz.experimental.notifications.wake

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.attestations.proficiency.kinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kind
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.toPTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.json.JsonNull.content

@Immutable
class WakeUpEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider {
    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds(): List<HexKey> = tags.mapNotNull(ETag::parseId)

    fun events() = tags.mapNotNull(ETag::parse)

    fun eventIds() = tags.mapNotNull(ETag::parseId)

    fun authors() = tags.mapNotNull(PTag::parse)

    fun authorKeys() = tags.mapNotNull(PTag::parseKey)

    fun kinds() = tags.kinds()

    /**
     * A WakeUpEvent's `p` tags point to the authors of the referenced subject
     * events (see [authorKeys]), **not** to the account the wake-up should be
     * delivered to. Example: Bob reacts to Alice's note, a WakeUpEvent about
     * Bob's reaction p-tags Bob — but it's Alice's device that needs to wake
     * up to process the reaction.
     *
     * WakeUpEvents reach this device through transport-level routing (push,
     * relay subscription). By the time one lands in [LocalCache], it is
     * already "for us" — so every logged-in signing account is a valid
     * recipient to kick the relay wakeup on behalf of. Returning true here
     * means the dispatcher invokes [com.vitorpamplona.quartz.experimental.
     * notifications.wake.WakeUpEvent]-handling for each logged-in account,
     * which is fine because keeping relay connections alive is idempotent.
     */
    override fun notifies(userHex: HexKey): Boolean = true

    companion object {
        const val KIND = 23903
        const val ALT_DESCRIPTION = "WakeUp"

        // p-tags on a WakeUp identify the AUTHORS of the referenced events —
        // the people whose events are the subject of the wake-up and who should
        // come online to handle new activity on them. Callers can add extra
        // e/p tags via [initializer] when waking up about multiple events.
        fun build(
            about: EventHintBundle<Event>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WakeUpEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            alt(ALT_DESCRIPTION)
            about(about)
            notify(about.toPTag())
            kind(about.event.kind)
            initializer()
        }
    }
}
