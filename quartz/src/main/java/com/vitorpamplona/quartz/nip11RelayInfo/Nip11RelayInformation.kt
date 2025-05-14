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
package com.vitorpamplona.quartz.nip11RelayInfo

import androidx.compose.runtime.Stable
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@Stable
class Nip11RelayInformation(
    val id: String?,
    val name: String?,
    val description: String?,
    val icon: String?,
    val pubkey: String?,
    val contact: String?,
    val supported_nips: List<Int>?,
    val supported_nip_extensions: List<String>?,
    val software: String?,
    val version: String?,
    val limitation: RelayInformationLimitation?,
    val relay_countries: List<String>?,
    val language_tags: List<String>?,
    val tags: List<String>?,
    val posting_policy: String?,
    val payments_url: String?,
    val retention: List<RelayInformationRetentionData>?,
    val fees: RelayInformationFees?,
    val nip50: List<String>?,
) {
    companion object {
        val mapper =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun fromJson(json: String): Nip11RelayInformation = mapper.readValue(json, Nip11RelayInformation::class.java)
    }
}

@Stable
class RelayInformationFee(
    val amount: Int?,
    val unit: String?,
    val period: Int?,
    val kinds: List<Int>?,
)

class RelayInformationFees(
    val admission: List<RelayInformationFee>?,
    val subscription: List<RelayInformationFee>?,
    val publication: List<RelayInformationFee>?,
)

class RelayInformationLimitation(
    val max_message_length: Int?,
    val max_subscriptions: Int?,
    val max_filters: Int?,
    val max_limit: Int?,
    val max_subid_length: Int?,
    val min_prefix: Int?,
    val max_event_tags: Int?,
    val max_content_length: Int?,
    val min_pow_difficulty: Int?,
    val auth_required: Boolean?,
    val payment_required: Boolean?,
    val restricted_writes: Boolean?,
    val created_at_lower_limit: Int?,
    val created_at_upper_limit: Int?,
)

class RelayInformationRetentionData(
    val kinds: ArrayList<Int>?,
    val tiem: Int?,
    val count: Int?,
)
