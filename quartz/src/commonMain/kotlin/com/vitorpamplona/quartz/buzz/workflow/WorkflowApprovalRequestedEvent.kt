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
 * A Buzz workflow step is waiting for human approval (`kind:46010`), emitted and signed by the
 * relay when a run pauses. It is a "needs action" feed item: an `h` tag scopes it to the
 * workflow's channel and a `p` tag names the approver whose action is required (the approver
 * later replies with a [ApprovalGrantEvent]/[ApprovalDenyEvent]). This kind is push-eligible
 * (NIP-PL urgent). The `content` carries relay-supplied approval detail as JSON; its exact
 * schema is not fixed by a builder in the Buzz sources yet. Ground truth: the `p`+`h` tagging in
 * Buzz's `buzz-db/src/feed.rs` (needs-action query) and `KIND_WORKFLOW_APPROVAL_REQUESTED`.
 */
@Immutable
class WorkflowApprovalRequestedEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The channel UUID (the `h` tag) this approval request belongs to. */
    fun channel() = tags.workflowChannel()

    /** The approver whose action is required - the `p` tag. */
    fun approver() = tags.workflowApprover()

    companion object {
        const val KIND = 46010

        fun build(
            channelId: String,
            approverPubKey: HexKey,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WorkflowApprovalRequestedEvent>.() -> Unit = {},
        ) = eventTemplate<WorkflowApprovalRequestedEvent>(KIND, content, createdAt) {
            workflowChannel(channelId)
            workflowApprover(approverPubKey)
            initializer()
        }
    }
}
