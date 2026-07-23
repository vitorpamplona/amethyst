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
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz workflow definition (`kind:30620`) - an addressable (NIP-33) event whose `d` tag is
 * the workflow UUID and whose `content` is the workflow's YAML source. An `h` tag scopes it to
 * a channel; an optional `name` tag labels it. The relay parses the YAML, upserts by the
 * `(owner, d)` address, and preserves any webhook secret across updates. Ground truth:
 * `handle_workflow_def` in Buzz's `buzz-relay/src/handlers/command_executor.rs`.
 */
@Immutable
class WorkflowDefEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The workflow UUID - the NIP-33 `d` tag. */
    fun workflowId() = dTag()

    /** The channel UUID (the `h` tag) this workflow is defined in. */
    fun channel() = tags.workflowChannel()

    /** The optional human-readable workflow name. */
    fun name() = tags.workflowName()

    /** The workflow YAML source - the event `content`. */
    fun yaml() = content

    companion object {
        const val KIND = 30620

        fun build(
            workflowId: String,
            channelId: String,
            yaml: String,
            name: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WorkflowDefEvent>.() -> Unit = {},
        ) = eventTemplate<WorkflowDefEvent>(KIND, yaml, createdAt) {
            dTag(workflowId)
            workflowChannel(channelId)
            name?.let { workflowName(it) }
            initializer()
        }
    }
}
