/**
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
package com.vitorpamplona.quartz.experimental.zapPolls

import com.vitorpamplona.quartz.experimental.zapPolls.tags.ClosedAtTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.ConsensusThresholdTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.MaximumTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.MinimumTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.PollOptionTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun TagArrayBuilder<PollNoteEvent>.consensusThreshold(percentage: Double) = addUnique(ConsensusThresholdTag.assemble(percentage))

fun TagArrayBuilder<PollNoteEvent>.minAmount(value: Long) = addUnique(MinimumTag.assemble(value))

fun TagArrayBuilder<PollNoteEvent>.maxAmount(value: Long) = addUnique(MaximumTag.assemble(value))

fun TagArrayBuilder<PollNoteEvent>.closedAt(timestamp: Long) = addUnique(ClosedAtTag.assemble(timestamp))

fun TagArrayBuilder<PollNoteEvent>.pollOption(
    index: Int,
    description: String,
) = add(PollOptionTag.assemble(index, description))

fun TagArrayBuilder<PollNoteEvent>.pollOptions(options: Map<Int, String>) = addAll(options.map { PollOptionTag.assemble(it.key, it.value) })

fun TagArrayBuilder<PollNoteEvent>.pollOptions(options: List<PollOptionTag>) = addAll(options.map { it.toTagArray() })
