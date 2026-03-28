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

fun TagArrayBuilder<ContactCardEvent>.rank(rank: Int) = add(RankTag.assemble(rank))

fun TagArrayBuilder<ContactCardEvent>.followers(count: Int) = add(FollowerCountTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.firstCreatedAt(timestamp: Long) = add(FirstCreatedAtTag.assemble(timestamp))

fun TagArrayBuilder<ContactCardEvent>.postCount(count: Int) = add(PostCountTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.replyCount(count: Int) = add(ReplyCountTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.reactionsCount(count: Int) = add(ReactionsCountTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.zapAmountReceived(sats: Long) = add(ZapAmountReceivedTag.assemble(sats))

fun TagArrayBuilder<ContactCardEvent>.zapAmountSent(sats: Long) = add(ZapAmountSentTag.assemble(sats))

fun TagArrayBuilder<ContactCardEvent>.zapCountReceived(count: Int) = add(ZapCountReceivedTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.zapCountSent(count: Int) = add(ZapCountSentTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.zapAvgAmountDayReceived(sats: Long) = add(ZapAvgAmountDayReceivedTag.assemble(sats))

fun TagArrayBuilder<ContactCardEvent>.zapAvgAmountDaySent(sats: Long) = add(ZapAvgAmountDaySentTag.assemble(sats))

fun TagArrayBuilder<ContactCardEvent>.reportsCountReceived(count: Int) = add(ReportsCountReceivedTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.reportsCountSent(count: Int) = add(ReportsCountSentTag.assemble(count))

fun TagArrayBuilder<ContactCardEvent>.topic(topic: String) = add(TopicTag.assemble(topic))

fun TagArrayBuilder<ContactCardEvent>.activeHoursStart(hour: Int) = add(ActiveHoursStartTag.assemble(hour))

fun TagArrayBuilder<ContactCardEvent>.activeHoursEnd(hour: Int) = add(ActiveHoursEndTag.assemble(hour))

fun TagArrayBuilder<ContactCardEvent>.petName(name: String) = add(PetNameTag.assemble(name))

fun TagArrayBuilder<ContactCardEvent>.summary(summary: String) = add(SummaryTag.assemble(summary))
