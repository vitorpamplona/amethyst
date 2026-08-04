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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The event a NIP-66 monitor publishes to measure a relay's WRITE path: publish
 * one of these (signed with the monitor key), time the `OK`, and read the
 * rejection prefix when refused (`auth-required:`/`restricted:`/`pow:` map to
 * the discovery record's `R` requirement tags; a timed acceptance is `rtt-write`).
 *
 * The kind is EPHEMERAL (20000–29999 per NIP-01), so a compliant relay serves it
 * to current subscribers and never stores it — the probe leaves nothing behind.
 * [KIND] 20166 is this library's convention (30166 discovery minus the
 * addressable range), not something NIP-66 standardizes; any ephemeral kind
 * works. Belt-and-braces, the template also carries a NIP-40 `expiration` tag
 * [EXPIRATION_SECONDS] out, so a relay that stores unknown ephemeral kinds
 * anyway purges it promptly.
 *
 * A rejection is still a MEASUREMENT: an `OK false` proves the write path works
 * and documents the relay's policy. Only silence is a failed write test.
 */
object RelayProbeWriteTest {
    const val KIND = 20166

    /** Storage-window ceiling for non-compliant relays that store ephemeral events. */
    const val EXPIRATION_SECONDS = 60L

    fun build(
        content: String = "NIP-66 write probe",
        createdAt: Long = TimeUtils.now(),
    ): EventTemplate<Event> =
        eventTemplate(KIND, content, createdAt) {
            add(ExpirationTag.assemble(createdAt + EXPIRATION_SECONDS))
        }
}
