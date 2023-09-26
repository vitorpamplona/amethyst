package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@Stable
class RelayInformation(
    val id: String?,
    val name: String?,
    val description: String?,
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
    val fees: RelayInformationFees?
) {
    companion object {
        val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun fromJson(json: String): RelayInformation = mapper.readValue(json, RelayInformation::class.java)
    }
}

@Stable
class RelayInformationFee(
    val amount: Int?,
    val unit: String?,
    val period: Int?,
    val kinds: List<Int>?
)

class RelayInformationFees(
    val admission: List<RelayInformationFee>?,
    val subscription: List<RelayInformationFee>?,
    val publication: List<RelayInformationFee>?,
    val retention: List<RelayInformationFee>?
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
    val payment_required: Boolean?
)
