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
package com.vitorpamplona.quartz.nip11RelayInfo

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation.Nip29Support
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation.RelayInformationFee
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation.RelayInformationFees
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation.RelayInformationLimitation
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation.RelayInformationRetentionData

/**
 * Scopes the NIP-11 DSL so an inner builder (e.g. `limitation { }`) can't
 * accidentally reach the outer builder's members.
 */
@DslMarker annotation class Nip11DslMarker

/**
 * Type-safe builder for a [Nip11RelayInformation] document, so a relay operator
 * never has to hand-write the JSON.
 *
 * Instead of the string that smells:
 * ```
 * private val NIP11 =
 *     """{"name":"sot","description":"...","supported_nips":[1,11,42,50],"software":"...","version":"0.1"}"""
 * ```
 * write:
 * ```
 * val info = relayInformation {
 *     name = "sot"
 *     description = "NIP-50 profile search ranked by Nostr web-of-trust"
 *     software = "https://github.com/vitorpamplona/sot"
 *     version = "0.1"
 *     supports(1, 11, 42, 50)
 * }
 * ```
 * and serve it with [Nip11RelayInformation.toJson] under
 * [Nip11RelayInformation.CONTENT_TYPE]. Null/empty fields are omitted, and
 * numeric NIP numbers serialize as the JSON integers the spec expects
 * (`[1,11,42,50]`, not `["1","11","42","50"]`).
 */
inline fun relayInformation(initializer: Nip11RelayInformationBuilder.() -> Unit): Nip11RelayInformation = Nip11RelayInformationBuilder().apply(initializer).build()

@Nip11DslMarker
class Nip11RelayInformationBuilder {
    var id: String? = null
    var name: String? = null
    var description: String? = null
    var banner: String? = null
    var icon: String? = null
    var pubkey: HexKey? = null
    var self: HexKey? = null
    var contact: String? = null
    var software: String? = null
    var version: String? = null
    var postingPolicy: String? = null
    var privacyPolicy: String? = null
    var termsOfService: String? = null
    var paymentsUrl: String? = null

    private val supportedNips = mutableListOf<String>()
    private val supportedNipExtensions = mutableListOf<String>()
    private val relayCountries = mutableListOf<String>()
    private val languageTags = mutableListOf<String>()
    private val tags = mutableListOf<String>()
    private val nip50Subfeatures = mutableListOf<String>()
    private val supportedGrasps = mutableListOf<String>()
    private val retention = mutableListOf<RelayInformationRetentionData>()

    private var limitation: RelayInformationLimitation? = null
    private var fees: RelayInformationFees? = null
    private var nip29: Nip29Support? = null

    /** Advertise supported NIP numbers, e.g. `supports(1, 11, 42, 50)`. Repeatable. */
    fun supports(vararg nips: Int) = apply { nips.forEach { supportedNips.add(it.toString()) } }

    /** Advertise supported NIPs by string id (for non-numeric extensions). Repeatable. */
    fun supports(vararg nips: String) = apply { supportedNips.addAll(nips) }

    /** Advertise `supported_nip_extensions` (draft/experimental features). Repeatable. */
    fun supportsExtensions(vararg extensions: String) = apply { supportedNipExtensions.addAll(extensions) }

    /** ISO-3166 country codes the relay serves under (`relay_countries`). Repeatable. */
    fun countries(vararg codes: String) = apply { relayCountries.addAll(codes) }

    /** IETF language tags of content on the relay (`language_tags`). Repeatable. */
    fun languages(vararg codes: String) = apply { languageTags.addAll(codes) }

    /** Free-form topic tags describing the relay's focus. Repeatable. */
    fun tags(vararg values: String) = apply { tags.addAll(values) }

    /** NIP-50 search sub-features the relay implements (the `nip50` field). Repeatable. */
    fun nip50Features(vararg features: String) = apply { nip50Subfeatures.addAll(features) }

    /** GRASP git-server capabilities the relay implements (`supported_grasps`). Repeatable. */
    fun grasps(vararg values: String) = apply { supportedGrasps.addAll(values) }

    /** Advertise NIP-29 subgroup support (`nip29: { "subgroups": true }`). */
    fun subgroups(supported: Boolean = true) = apply { nip29 = Nip29Support(subgroups = supported) }

    /** Declare the relay's `limitation` object via a nested DSL. */
    fun limitation(initializer: LimitationBuilder.() -> Unit) =
        apply {
            limitation = LimitationBuilder().apply(initializer).build()
        }

    /**
     * Advertise the exact limits the relay actually enforces, keeping what is
     * published in sync with what is applied. See [RelayLimits.toNip11Limitation].
     */
    fun limitation(limits: RelayLimits) = apply { limitation = limits.toNip11Limitation() }

    /** Declare the relay's `fees` object via a nested DSL. */
    fun fees(initializer: FeesBuilder.() -> Unit) =
        apply {
            fees = FeesBuilder().apply(initializer).build()
        }

    /** Add a data-retention policy entry (`retention`). Call multiple times for multiple entries. */
    fun retention(
        kinds: List<Int>? = null,
        time: Int? = null,
        count: Int? = null,
    ) = apply {
        retention.add(RelayInformationRetentionData(kinds?.let { ArrayList(it) }, time, count))
    }

    fun build(): Nip11RelayInformation =
        Nip11RelayInformation(
            id = id,
            name = name,
            description = description,
            banner = banner,
            icon = icon,
            pubkey = pubkey,
            self = self,
            contact = contact,
            supported_nips = supportedNips.ifEmpty { null }?.toList(),
            supported_nip_extensions = supportedNipExtensions.ifEmpty { null }?.toList(),
            software = software,
            version = version,
            limitation = limitation,
            relay_countries = relayCountries.ifEmpty { null }?.toList(),
            language_tags = languageTags.ifEmpty { null }?.toList(),
            tags = tags.ifEmpty { null }?.toList(),
            posting_policy = postingPolicy,
            privacy_policy = privacyPolicy,
            terms_of_service = termsOfService,
            payments_url = paymentsUrl,
            retention = retention.ifEmpty { null }?.toList(),
            fees = fees,
            nip50 = nip50Subfeatures.ifEmpty { null }?.toList(),
            supported_grasps = supportedGrasps.ifEmpty { null }?.toList(),
            nip29 = nip29,
        )

    @Nip11DslMarker
    class LimitationBuilder {
        var maxMessageLength: Int? = null
        var maxSubscriptions: Int? = null
        var maxFilters: Int? = null
        var maxLimit: Int? = null
        var defaultLimit: Int? = null
        var maxSubidLength: Int? = null
        var minPrefix: Int? = null
        var maxEventTags: Int? = null
        var maxContentLength: Int? = null
        var minPowDifficulty: Int? = null
        var authRequired: Boolean? = null
        var paymentRequired: Boolean? = null
        var restrictedWrites: Boolean? = null
        var createdAtLowerLimit: Int? = null
        var createdAtUpperLimit: Int? = null

        fun build(): RelayInformationLimitation =
            RelayInformationLimitation(
                max_message_length = maxMessageLength,
                max_subscriptions = maxSubscriptions,
                max_filters = maxFilters,
                max_limit = maxLimit,
                default_limit = defaultLimit,
                max_subid_length = maxSubidLength,
                min_prefix = minPrefix,
                max_event_tags = maxEventTags,
                max_content_length = maxContentLength,
                min_pow_difficulty = minPowDifficulty,
                auth_required = authRequired,
                payment_required = paymentRequired,
                restricted_writes = restrictedWrites,
                created_at_lower_limit = createdAtLowerLimit,
                created_at_upper_limit = createdAtUpperLimit,
            )
    }

    @Nip11DslMarker
    class FeesBuilder {
        private val admission = mutableListOf<RelayInformationFee>()
        private val subscription = mutableListOf<RelayInformationFee>()
        private val publication = mutableListOf<RelayInformationFee>()

        /** A one-time fee to be allowed to write to the relay. Repeatable. */
        fun admission(
            amount: Int? = null,
            unit: String? = null,
            period: Int? = null,
            kinds: List<Int>? = null,
        ) = apply { admission.add(RelayInformationFee(amount, unit, period, kinds)) }

        /** A recurring fee to keep writing to the relay. Repeatable. */
        fun subscription(
            amount: Int? = null,
            unit: String? = null,
            period: Int? = null,
            kinds: List<Int>? = null,
        ) = apply { subscription.add(RelayInformationFee(amount, unit, period, kinds)) }

        /** A per-event fee to publish. Repeatable (e.g. one entry per kind group). */
        fun publication(
            amount: Int? = null,
            unit: String? = null,
            period: Int? = null,
            kinds: List<Int>? = null,
        ) = apply { publication.add(RelayInformationFee(amount, unit, period, kinds)) }

        fun build(): RelayInformationFees =
            RelayInformationFees(
                admission = admission.ifEmpty { null }?.toList(),
                subscription = subscription.ifEmpty { null }?.toList(),
                publication = publication.ifEmpty { null }?.toList(),
            )
    }
}
