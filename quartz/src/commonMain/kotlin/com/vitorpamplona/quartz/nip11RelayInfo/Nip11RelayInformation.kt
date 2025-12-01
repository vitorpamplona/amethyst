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
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.Serializable

@Serializable
class Nip11RelayInformation(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val pubkey: String? = null,
    val contact: String? = null,
    @Serializable(with = FlexibleIntListSerializer::class)
    val supported_nips: List<Int>? = null,
    val supported_nip_extensions: List<String>? = null,
    val software: String? = null,
    val version: String? = null,
    val limitation: RelayInformationLimitation? = null,
    val relay_countries: List<String>? = null,
    val language_tags: List<String>? = null,
    val tags: List<String>? = null,
    val posting_policy: String? = null,
    val payments_url: String? = null,
    val retention: List<RelayInformationRetentionData>? = null,
    val fees: RelayInformationFees? = null,
    val nip50: List<String>? = null,
) {
    companion object {
        fun fromJson(json: String): Nip11RelayInformation = JsonMapper.fromJson<Nip11RelayInformation>(json)
    }
}

@Stable
@Serializable
class RelayInformationFee(
    val amount: Int? = null,
    val unit: String? = null,
    val period: Int? = null,
    val kinds: List<Int>? = null,
)

@Serializable
class RelayInformationFees(
    val admission: List<RelayInformationFee>? = null,
    val subscription: List<RelayInformationFee>? = null,
    val publication: List<RelayInformationFee>? = null,
)

@Serializable
class RelayInformationLimitation(
    val max_message_length: Int? = null,
    val max_subscriptions: Int? = null,
    val max_filters: Int? = null,
    val max_limit: Int? = null,
    val max_subid_length: Int? = null,
    val min_prefix: Int? = null,
    val max_event_tags: Int? = null,
    val max_content_length: Int? = null,
    val min_pow_difficulty: Int? = null,
    val auth_required: Boolean? = null,
    val payment_required: Boolean? = null,
    val restricted_writes: Boolean? = null,
    val created_at_lower_limit: Int? = null,
    val created_at_upper_limit: Int? = null,
)

@Serializable
class RelayInformationRetentionData(
    val kinds: ArrayList<Int>? = null,
    val time: Int? = null,
    val count: Int? = null,
)
