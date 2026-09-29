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
package com.vitorpamplona.quartz.graph.props

/**
 * What a NIP-85 assertion states about its subject, keyed in [toMap] by the NIP-85 tag names: user metrics (30382) and content metrics (30383–30385). Absent metrics are null.
 *
 * Props of `SUBJECT`.
 */
data class SubjectProps(
    val rank: Int? = null,
    val followers: Int? = null,
    val hops: Int? = null,
    val firstCreatedAt: Long? = null,
    val postCount: Int? = null,
    val replyCount: Int? = null,
    val reactionsCount: Int? = null,
    val zapAmountReceived: Long? = null,
    val zapAmountSent: Long? = null,
    val zapCountReceived: Int? = null,
    val zapCountSent: Int? = null,
    val zapAvgAmountDayReceived: Long? = null,
    val zapAvgAmountDaySent: Long? = null,
    val reportsCountReceived: Int? = null,
    val reportsCountSent: Int? = null,
    val activeHoursStart: Int? = null,
    val activeHoursEnd: Int? = null,
    val commentCount: Int? = null,
    val quoteCount: Int? = null,
    val repostCount: Int? = null,
    val reactionCount: Int? = null,
    val zapCount: Int? = null,
    val zapAmount: Long? = null,
) : LinkProps {
    override fun toMap(): Map<String, Any> =
        propsOf(
            "rank" to rank,
            "followers" to followers,
            "hops" to hops,
            "first_created_at" to firstCreatedAt,
            "post_cnt" to postCount,
            "reply_cnt" to replyCount,
            "reactions_cnt" to reactionsCount,
            "zap_amt_recd" to zapAmountReceived,
            "zap_amt_sent" to zapAmountSent,
            "zap_cnt_recd" to zapCountReceived,
            "zap_cnt_sent" to zapCountSent,
            "zap_avg_amt_day_recd" to zapAvgAmountDayReceived,
            "zap_avg_amt_day_sent" to zapAvgAmountDaySent,
            "reports_cnt_recd" to reportsCountReceived,
            "reports_cnt_sent" to reportsCountSent,
            "active_hours_start" to activeHoursStart,
            "active_hours_end" to activeHoursEnd,
            "comment_cnt" to commentCount,
            "quote_cnt" to quoteCount,
            "repost_cnt" to repostCount,
            "reaction_cnt" to reactionCount,
            "zap_cnt" to zapCount,
            "zap_amount" to zapAmount,
        )
}
