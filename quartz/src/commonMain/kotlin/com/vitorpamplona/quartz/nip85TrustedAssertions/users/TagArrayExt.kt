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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
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

fun TagArray.rank() = fastFirstNotNullOfOrNull(RankTag::parse)

fun TagArray.followerCount() = fastFirstNotNullOfOrNull(FollowerCountTag::parse)

fun TagArray.hops() = fastFirstNotNullOfOrNull(HopsTag::parse)

fun TagArray.firstCreatedAt() = fastFirstNotNullOfOrNull(FirstCreatedAtTag::parse)

fun TagArray.postCount() = fastFirstNotNullOfOrNull(PostCountTag::parse)

fun TagArray.replyCount() = fastFirstNotNullOfOrNull(ReplyCountTag::parse)

fun TagArray.reactionsCount() = fastFirstNotNullOfOrNull(ReactionsCountTag::parse)

fun TagArray.zapAmountReceived() = fastFirstNotNullOfOrNull(ZapAmountReceivedTag::parse)

fun TagArray.zapAmountSent() = fastFirstNotNullOfOrNull(ZapAmountSentTag::parse)

fun TagArray.zapCountReceived() = fastFirstNotNullOfOrNull(ZapCountReceivedTag::parse)

fun TagArray.zapCountSent() = fastFirstNotNullOfOrNull(ZapCountSentTag::parse)

fun TagArray.zapAvgAmountDayReceived() = fastFirstNotNullOfOrNull(ZapAvgAmountDayReceivedTag::parse)

fun TagArray.zapAvgAmountDaySent() = fastFirstNotNullOfOrNull(ZapAvgAmountDaySentTag::parse)

fun TagArray.reportsCountReceived() = fastFirstNotNullOfOrNull(ReportsCountReceivedTag::parse)

fun TagArray.reportsCountSent() = fastFirstNotNullOfOrNull(ReportsCountSentTag::parse)

fun TagArray.topics() = mapNotNull(TopicTag::parse)

fun TagArray.activeHoursStart() = fastFirstNotNullOfOrNull(ActiveHoursStartTag::parse)

fun TagArray.activeHoursEnd() = fastFirstNotNullOfOrNull(ActiveHoursEndTag::parse)

fun TagArray.petName() = fastFirstNotNullOfOrNull(PetNameTag::parse)

fun TagArray.summary() = fastFirstNotNullOfOrNull(SummaryTag::parse)
