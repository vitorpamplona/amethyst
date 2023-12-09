package com.vitorpamplona.amethyst.service

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.Request

object Nip96MediaServers {
    val DEFAULT =
        listOf(
            ServerName("Nostr.Build", "https://nostr.build"),
            ServerName("NostrCheck.me", "https://nostrcheck.me"),
            ServerName("Nostrage", "https://nostrage.com"),
            ServerName("Sove", "https://sove.rent"),
            ServerName("Sovbit", "https://files.sovbit.host"),
            ServerName("Void.cat", "https://void.cat")
        )

    data class ServerName(val name: String, val baseUrl: String)

    val cache: MutableMap<String, Nip96Retriever.ServerInfo> = mutableMapOf()

    suspend fun load(url: String): Nip96Retriever.ServerInfo {
        val cached = cache[url]
        if (cached != null) return cached

        val fetched = Nip96Retriever().loadInfo(url)
        cache[url] = fetched
        return fetched
    }
}

class Nip96Retriever {
    data class ServerInfo(
        @JsonProperty("api_url") val apiUrl: String,
        @JsonProperty("download_url") val downloadUrl: String? = null,
        @JsonProperty("delegated_to_url") val delegatedToUrl: String? = null,
        @JsonProperty("supported_nips") val supportedNips: ArrayList<Int> = arrayListOf(),
        @JsonProperty("tos_url") val tosUrl: String? = null,
        @JsonProperty("content_types") val contentTypes: ArrayList<MimeType> = arrayListOf(),
        @JsonProperty("plans") val plans: Map<PlanName, Plan> = mapOf()
    )

    data class Plan(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("is_nip98_required") val isNip98Required: Boolean? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("max_byte_size") val maxByteSize: Long? = null,
        @JsonProperty("file_expiration") val fileExpiration: ArrayList<Int> = arrayListOf(),
        @JsonProperty("media_transformations") val mediaTransformations: Map<MimeType, Array<String>> = emptyMap()
    )

    fun parse(body: String): ServerInfo {
        val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return mapper.readValue(body, ServerInfo::class.java)
    }

    suspend fun loadInfo(
        baseUrl: String
    ): ServerInfo {
        checkNotInMainThread()

        val request: Request = Request
            .Builder()
            .header("Accept", "application/nostr+json")
            .url(baseUrl.removeSuffix("/") + "/.well-known/nostr/nip96.json")
            .build()

        HttpClient.getHttpClient()
            .newCall(request)
            .execute().use { response ->
                checkNotInMainThread()
                response.use {
                    val body = it.body.string()
                    try {
                        if (it.isSuccessful) {
                            return parse(body)
                        } else {
                            throw RuntimeException("Resulting Message from $baseUrl is an error: ${response.code} ${response.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("RelayInfoFail", "Resulting Message from $baseUrl in not parseable: $body", e)
                        throw e
                    }
                }
            }
    }
}

typealias PlanName = String
typealias MimeType = String
