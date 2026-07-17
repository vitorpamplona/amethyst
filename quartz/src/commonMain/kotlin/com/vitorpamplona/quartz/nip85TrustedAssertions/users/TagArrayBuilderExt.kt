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
package com.vitorpamplona.quartz.nip85TrustedAssertions.users

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ActiveHoursEndTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ActiveHoursStartTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.FirstCreatedAtTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.FollowerCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.HopsTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.PetNameTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.PostCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ReactionsCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ReplyCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ReportsCountReceivedTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ReportsCountSentTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.SummaryTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.TopicTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapAmountReceivedTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapAmountSentTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapAvgAmountDayReceivedTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapAvgAmountDaySentTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapCountReceivedTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.ZapCountSentTag

fun TagArrayBuilder<ContactCardEvent>.rank(rank: Int) = addUnique(RankTag.assemble(rank))

fun TagArrayBuilder<ContactCardEvent>.followers(count: Int) = addUnique(FollowerCountTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.hops(hops: Int) = addUnique(HopsTag.assemble(hops))

fun TagArrayBuilder<ContactCardEvent>.firstCreatedAt(timestamp: Long) = addUnique(FirstCreatedAtTag.assemble(timestamp))

fun TagArrayBuilder<ContactCardEvent>.postCount(count: Int) = addUnique(PostCountTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.replyCount(count: Int) = addUnique(ReplyCountTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.reactionsCount(count: Int) = addUnique(ReactionsCountTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.zapAmountReceived(sats: Long) = addUnique(ZapAmountReceivedTag.assemble(sats))

fun TagArrayBuilder<ContactCardEvent>.zapAmountSent(sats: Long) = addUnique(ZapAmountSentTag.assemble(sats))

fun TagArrayBuilder<ContactCardEvent>.zapCountReceived(count: Int) = addUnique(ZapCountReceivedTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.zapCountSent(count: Int) = addUnique(ZapCountSentTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.zapAvgAmountDayReceived(sats: Long) = addUnique(ZapAvgAmountDayReceivedTag.assemble(sats))

fun TagArrayBuilder<ContactCardEvent>.zapAvgAmountDaySent(sats: Long) = addUnique(ZapAvgAmountDaySentTag.assemble(sats))

fun TagArrayBuilder<ContactCardEvent>.reportsCountReceived(count: Int) = addUnique(ReportsCountReceivedTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.reportsCountSent(count: Int) = addUnique(ReportsCountSentTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.topic(topic: String) = add(TopicTag.assemble(topic))

fun TagArrayBuilder<ContactCardEvent>.activeHoursStart(hour: Int) = addUnique(ActiveHoursStartTag.assemble(hour))

fun TagArrayBuilder<ContactCardEvent>.activeHoursEnd(hour: Int) = addUnique(ActiveHoursEndTag.assemble(hour))

fun TagArrayBuilder<ContactCardEvent>.petName(name: String) = addUnique(PetNameTag.assemble(name))

fun TagArrayBuilder<ContactCardEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))
