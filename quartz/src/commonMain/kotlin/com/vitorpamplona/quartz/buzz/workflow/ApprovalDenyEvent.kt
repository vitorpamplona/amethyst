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
package com.vitorpamplona.quartz.buzz.workflow

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Denies a pending Buzz workflow approval (`kind:46031`). The `d` tag carries the approval
 * token hash hex; the optional `content` is a free-text note. Only an authorized approver may
 * deny, and denying cancels the paused workflow run. Ground truth: `handle_approval_deny` in
 * Buzz's `buzz-relay/src/handlers/command_executor.rs`.
 */
@Immutable
class ApprovalDenyEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The approval token hash hex this denial acts on - the `d` tag. */
    fun tokenHash() = tags.workflowDTag()

    /** The optional approver note - the event `content`. */
    fun note() = content

    companion object {
        const val KIND = 46031

        fun build(
            tokenHash: String,
            note: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ApprovalDenyEvent>.() -> Unit = {},
        ) = eventTemplate<ApprovalDenyEvent>(KIND, note, createdAt) {
            workflowDTag(tokenHash)
            initializer()
        }
    }
}
