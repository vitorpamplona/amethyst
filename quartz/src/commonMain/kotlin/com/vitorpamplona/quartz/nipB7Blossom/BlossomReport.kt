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
package com.vitorpamplona.quartz.nipB7Blossom

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip56Reports.hash
import com.vitorpamplona.quartz.nip56Reports.user
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * BUD-09 blob report: a NIP-56 (kind 1984) report event scoped to a blob by its
 * sha256 (`x` tag) rather than to a nostr event. The signed event is PUT to a
 * server's `/report` endpoint so the operator can review problematic content.
 *
 * Reuses the shared NIP-56 tag builders ([hash], [user]) so a blob report is a
 * regular [ReportEvent] — clients that already parse kind 1984 pick up the
 * reported hash via `HashSha256Tag`.
 */
object BlossomReport {
    fun build(
        blobHash: HexKey,
        type: ReportType,
        uploader: HexKey? = null,
        comment: String = "",
        createdAt: Long = TimeUtils.now(),
    ) = eventTemplate(ReportEvent.KIND, comment, createdAt) {
        hash(blobHash, type)
        uploader?.let { user(it, type) }
    }
}
