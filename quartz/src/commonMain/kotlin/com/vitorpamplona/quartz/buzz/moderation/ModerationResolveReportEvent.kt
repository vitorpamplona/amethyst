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
package com.vitorpamplona.quartz.buzz.moderation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ReasonTag
import com.vitorpamplona.quartz.buzz.moderation.tags.ActionTag
import com.vitorpamplona.quartz.buzz.moderation.tags.ReportTag
import com.vitorpamplona.quartz.buzz.moderation.tags.StatusTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz community-moderation resolve-report command (`kind:9044`): mod-signed, closes a
 * kind:1984 report. Carries a [ReportTag] (the report event id), a [StatusTag]
 * (`resolved`/`dismissed`), an [ActionTag] (`delete`/`kick`/`ban`/`timeout`/`dismiss`/
 * `escalate`), and an optional [ReasonTag] whose text is surfaced to the reporter. No `h`
 * tag (tenant bound by the connection host); `content` is empty. Validated + executed by the
 * relay, never stored. Ground truth:
 * `buzz-sdk/src/builders.rs::build_moderation_resolve_report`.
 */
@Immutable
class ModerationResolveReportEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The kind:1984 report event id being resolved — the `report` tag. */
    fun report() = tags.moderationReport()

    /** The resolution status: `resolved` or `dismissed`. */
    fun status() = tags.moderationStatus()

    /** The action taken: `delete`/`kick`/`ban`/`timeout`/`dismiss`/`escalate`. */
    fun action() = tags.moderationAction()

    /** The optional reason surfaced to the reporter. */
    fun reason() = tags.moderationReason()

    companion object {
        const val KIND = 9044

        fun build(
            reportEventId: HexKey,
            status: String,
            action: String,
            reason: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ModerationResolveReportEvent>.() -> Unit = {},
        ) = eventTemplate<ModerationResolveReportEvent>(KIND, "", createdAt) {
            addUnique(ReportTag.assemble(reportEventId))
            addUnique(StatusTag.assemble(status))
            addUnique(ActionTag.assemble(action))
            reason?.let { addUnique(ReasonTag.assemble(it)) }
            initializer()
        }
    }
}
