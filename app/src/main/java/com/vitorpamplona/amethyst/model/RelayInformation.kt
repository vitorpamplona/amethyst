package com.vitorpamplona.amethyst.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class RelayInformation(
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
    val fees: RelayInformationFees?,
) {
    companion object {
        val gson: Gson = GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(RelayInformation::class.java, RelayInformationSerializer())
            .registerTypeAdapter(RelayInformationLimitation::class.java, RelayInformationLimitationSerializer())
            .registerTypeAdapter(RelayInformationFees::class.java, RelayInformationFeesSerializer())
            .registerTypeAdapter(RelayInformationFee::class.java, RelayInformationFeeSerializer())
            .create()

        fun fromJson(json: String): RelayInformation = gson.fromJson(json, RelayInformation::class.java)
    }
}

class RelayInformationFee(
    val amount: Int?,
    val unit: String?,
    val period: Int?,
    val kinds: List<Int>?
) {
    companion object {
        val gson: Gson = GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(RelayInformationFee::class.java, RelayInformationFeeSerializer())
            .create()

        fun fromJson(json: String): RelayInformationFee = gson.fromJson(json, RelayInformationFee::class.java)
    }
}

private class RelayInformationFeeSerializer : JsonSerializer<RelayInformationFee> {
    override fun serialize(
        src: RelayInformationFee,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonObject().apply {
            addProperty("amount", src.amount)
            addProperty("unit", src.unit)
            addProperty("period", src.period)
            add(
                "kinds",
                JsonArray().also { kinds ->
                    src.kinds?.forEach { kind ->
                        kinds.add(
                            kind
                        )
                    }
                }
            )
        }
    }
}

class RelayInformationFees(
    val admission: List<RelayInformationFee>?,
    val subscription: List<RelayInformationFee>?,
    val publication: List<RelayInformationFee>?,
) {
    companion object {
        val gson: Gson = GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(RelayInformationFees::class.java, RelayInformationFeesSerializer())
            .create()

        fun fromJson(json: String): RelayInformationFees = gson.fromJson(json, RelayInformationFees::class.java)
    }
}

private class RelayInformationFeesSerializer : JsonSerializer<RelayInformationFees> {
    override fun serialize(
        src: RelayInformationFees,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonObject().apply {
            add(
                "admission",
                JsonArray().also { admissions ->
                    src.admission?.forEach { admission ->
                        admissions.add(
                            admission.toString()
                        )
                    }
                }
            )
            add(
                "publication",
                JsonArray().also { publications ->
                    src.publication?.forEach { publication ->
                        publications.add(
                            publication.toString()
                        )
                    }
                }
            )
            add(
                "subscription",
                JsonArray().also { subscriptions ->
                    src.subscription?.forEach { subscription ->
                        subscriptions.add(
                            subscription.toString()
                        )
                    }
                }
            )
        }
    }
}

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
) {
    companion object {
        val gson: Gson = GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(RelayInformationLimitation::class.java, RelayInformationLimitationSerializer())
            .create()

        fun fromJson(json: String): RelayInformationLimitation = gson.fromJson(json, RelayInformationLimitation::class.java)
    }
}

private class RelayInformationLimitationSerializer : JsonSerializer<RelayInformationLimitation> {
    override fun serialize(
        src: RelayInformationLimitation,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonObject().apply {
            addProperty("max_message_length", src.max_message_length)
            addProperty("max_subscriptions", src.max_subscriptions)
            addProperty("max_filters", src.max_filters)
            addProperty("max_limit", src.max_limit)
            addProperty("max_subid_length", src.max_subid_length)
            addProperty("min_prefix", src.min_prefix)
            addProperty("max_event_tags", src.max_event_tags)
            addProperty("max_content_length", src.max_content_length)
            addProperty("min_pow_difficulty", src.min_pow_difficulty)
            addProperty("auth_required", src.auth_required)
            addProperty("payment_required", src.payment_required)
        }
    }
}

private class RelayInformationSerializer : JsonSerializer<RelayInformation> {
    override fun serialize(
        src: RelayInformation,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonObject().apply {
            addProperty("name", src.name)
            addProperty("description", src.description)
            addProperty("pubkey", src.pubkey)
            addProperty("contact", src.contact)
            add(
                "supported_nip_extensions",
                JsonArray().also { supported_nip_extensions ->
                    src.supported_nip_extensions?.forEach { nip ->
                        supported_nip_extensions.add(
                            nip
                        )
                    }
                }
            )
            add(
                "supported_nips",
                JsonArray().also { supported_nips ->
                    src.supported_nips?.forEach { nip ->
                        supported_nips.add(
                            nip
                        )
                    }
                }
            )
            addProperty("software", src.software)
            addProperty("version", src.version)
            add(
                "relay_countries",
                JsonArray().also { relay_countries ->
                    src.relay_countries?.forEach { country ->
                        relay_countries.add(
                            country
                        )
                    }
                }
            )
            add(
                "language_tags",
                JsonArray().also { language_tags ->
                    src.language_tags?.forEach { language_tag ->
                        language_tags.add(
                            language_tag
                        )
                    }
                }
            )
            add(
                "tags",
                JsonArray().also { tags ->
                    src.tags?.forEach { tag ->
                        tags.add(
                            tag
                        )
                    }
                }
            )
            addProperty("posting_policy", src.posting_policy)
            addProperty("payments_url", src.payments_url)
        }
    }
}