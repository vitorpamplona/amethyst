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
package com.vitorpamplona.quartz.nip75ZapGoals

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip75ZapGoals.tags.AmountTag
import com.vitorpamplona.quartz.nip75ZapGoals.tags.ClosedAtTag
import com.vitorpamplona.quartz.nip75ZapGoals.tags.RelayListTag

fun TagArrayBuilder<GoalEvent>.amount(amountInMillisats: Long) = addUnique(AmountTag.assemble(amountInMillisats))

fun TagArrayBuilder<GoalEvent>.relays(urls: List<String>) = add(RelayListTag.assemble(urls))

fun TagArrayBuilder<GoalEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<GoalEvent>.image(imageUrl: String) = addUnique(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<GoalEvent>.closedAt(closedAt: Long) = addUnique(ClosedAtTag.assemble(closedAt))

fun TagArrayBuilder<GoalEvent>.linked(tag: ETag) = addUnique(tag.toTagArray())

fun TagArrayBuilder<GoalEvent>.linked(tag: ATag) = addUnique(tag.toATagArray())
