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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/** The `h` channel tag (NIP-29) scoping a workflow event to its channel UUID. */
fun <T : Event> TagArrayBuilder<T>.workflowChannel(channelId: String) = addUnique(GroupIdTag.assemble(channelId))

/**
 * A `d` reference tag. On the addressable definition (`kind:30620`) it is the workflow UUID
 * (the NIP-33 identifier); on the trigger (`kind:46020`) it references that workflow UUID; on
 * approval grant/deny (`kind:46030`/`46031`) it carries the approval token hash hex. Ground
 * truth: `extract_d_tag` usage in Buzz's `buzz-relay/src/handlers/command_executor.rs`.
 */
fun <T : Event> TagArrayBuilder<T>.workflowDTag(value: String) = addUnique(DTag.assemble(value))

/** The optional human-readable workflow `name` tag on a definition. */
fun <T : Event> TagArrayBuilder<T>.workflowName(name: String) = addUnique(arrayOf("name", name))

/** The `p` tag naming the approver a `kind:46010` approval-requested event is addressed to. */
fun <T : Event> TagArrayBuilder<T>.workflowApprover(approverPubKey: HexKey) = addUnique(PTag.assemble(approverPubKey, null))
