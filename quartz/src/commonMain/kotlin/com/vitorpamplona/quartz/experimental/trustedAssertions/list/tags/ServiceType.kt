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
package com.vitorpamplona.quartz.experimental.trustedAssertions.list.tags

data class ServiceType(
    val kind: Int,
    val type: String,
) {
    fun toValue() = assemble(kind, type)

    companion object {
        fun assemble(
            kind: Int,
            type: String,
        ) = "$kind:$type"

        fun parse(serviceType: String): ServiceType? {
            val (kindStr, type) = serviceType.split(":", limit = 2)
            val kind = kindStr.toIntOrNull() ?: return null
            return ServiceType(kind, type)
        }

        fun isOfKind(
            serviceType: String,
            kind: String,
        ) = serviceType.startsWith(kind) && serviceType[kind.length] == ':'
    }
}

object ProviderTypes {
    val rank = ServiceType(30382, "rank")
    val followerCount = ServiceType(30382, "followers")

    val firstPost = ServiceType(30382, "first_created_at")
    val postCount = ServiceType(30382, "post_cnt")
    val replyCount = ServiceType(30382, "reply_cnt")
    val reactionsCount = ServiceType(30382, "reactions_cnt")
    val zapReceivedAmount = ServiceType(30382, "zap_amt_recd")
    val zapSentAmount = ServiceType(30382, "zap_amt_sent")
    val zapReceivedCount = ServiceType(30382, "zap_cnt_recd")
    val zapSentCount = ServiceType(30382, "zap_cnt_sent")
    val averageZapAmountReceivedPerDay = ServiceType(30382, "zap_avg_amt_day_recd")
    val averageZapAmountSentPerDay = ServiceType(30382, "zap_avg_amt_day_sent")
    val reportsReceivedCount = ServiceType(30382, "reports_cnt_recd")
    val reportsSentCount = ServiceType(30382, "reports_cnt_sent")
    val topics = ServiceType(30382, "t")
    val activeFrom = ServiceType(30382, "active_hours_start")
    val activeTo = ServiceType(30382, "active_hours_end")
}
