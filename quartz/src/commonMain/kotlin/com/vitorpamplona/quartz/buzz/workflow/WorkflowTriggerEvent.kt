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
 * A manual Buzz workflow trigger (`kind:46020`). The `d` tag references the workflow UUID to
 * run; the optional `content` is a JSON object of webhook fields merged into the run's trigger
 * context. Only the workflow owner may trigger. Ground truth: `handle_workflow_trigger` in
 * Buzz's `buzz-relay/src/handlers/command_executor.rs`.
 */
@Immutable
class WorkflowTriggerEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The workflow UUID this trigger targets - the `d` tag. */
    fun workflowId() = tags.workflowDTag()

    companion object {
        const val KIND = 46020

        fun build(
            workflowId: String,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WorkflowTriggerEvent>.() -> Unit = {},
        ) = eventTemplate<WorkflowTriggerEvent>(KIND, content, createdAt) {
            workflowDTag(workflowId)
            initializer()
        }
    }
}
