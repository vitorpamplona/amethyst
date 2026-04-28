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
package com.vitorpamplona.quartz.nip34Git.status

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Shared builder logic for NIP-34 status events. Every status event (kind
 * 1630/1631/1632/1633) roots itself in a target patch/PR/issue via an `e`
 * tag marked `"root"`, includes the target's author as a `p` tag, and
 * optionally CC's additional pubkeys.
 */
object GitStatusBuilders {
    fun <E : GitStatusEvent, T : Event> buildStatus(
        kind: Int,
        altDescriptor: String,
        content: String,
        target: EventHintBundle<T>,
        notify: List<PTag> = emptyList(),
        createdAt: Long = TimeUtils.now(),
        initializer: TagArrayBuilder<E>.() -> Unit = {},
    ) = eventTemplate<E>(kind, content, createdAt) {
        alt(altDescriptor)
        add(
            MarkedETag.assemble(
                target.event.id,
                target.relay,
                MarkedETag.MARKER.ROOT,
                target.event.pubKey,
            ),
        )
        pTag(target.event.pubKey, target.authorHomeRelay)
        if (notify.isNotEmpty()) pTags(notify)
        initializer()
    }
}
