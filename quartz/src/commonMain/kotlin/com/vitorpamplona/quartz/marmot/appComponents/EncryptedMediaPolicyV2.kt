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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter

/** One blob-store endpoint and the locator kind it serves. */
data class BlobStoreEndpointV2(
    val locatorKind: String,
    /** Normalized `http`/`https` base URL, 1..2048 bytes. */
    val baseUrl: String,
) {
    /**
     * `base_url` with trailing slashes removed — the `server_root` the fetch
     * and upload URL rules are written against.
     */
    val serverRoot: String get() = baseUrl.trimEnd('/')

    /** Blossom BUD-01 fetch URL for a ciphertext hash. No extension, query or fragment. */
    fun blossomFetchUrl(ciphertextSha256Hex: String) = "$serverRoot/$ciphertextSha256Hex"

    /** Blossom BUD-02 upload URL. */
    fun blossomUploadUrl() = "$serverRoot/upload"
}

/**
 * `marmot.group.encrypted-media.v2`, component `0x800b` — the group's media
 * policy (`app-components/group-encrypted-media-v2.md`).
 *
 * Supersedes the frozen v1 policy at `0x8008`, which MUST NOT be reinterpreted
 * as v2; a client may keep rendering legacy v1 references, but a
 * current-profile sender creates only v2 ones.
 *
 * ## Order is part of the value
 *
 * Unlike `relays` in nostr-routing or `admins` in admin-policy, neither list
 * here is sorted. `default_blob_endpoints` order IS the upload/fetch fallback
 * priority, so sorting it would silently change which server a group uploads
 * to. Two policies differing only in order are different canonical values, and
 * a decoder preserves what the producer wrote.
 */
data class EncryptedMediaPolicyV2(
    /** Ordered, unique, 1..16. The initial kind is `blossom-v1`. */
    val allowedLocatorKinds: List<String>,
    /** Ordered by fallback priority, unique, 1..16. */
    val defaultBlobEndpoints: List<BlobStoreEndpointV2>,
    /** Fixed constant. Not version negotiation — another format needs another component id. */
    val mediaFormat: String = MEDIA_FORMAT,
) {
    init {
        require(mediaFormat == MEDIA_FORMAT) {
            "media_format must be exactly '$MEDIA_FORMAT', was '$mediaFormat'"
        }
        require(allowedLocatorKinds.isNotEmpty()) { "allowed_locator_kinds must not be empty" }
        require(allowedLocatorKinds.size <= MAX_ENTRIES) {
            "allowed_locator_kinds exceeds $MAX_ENTRIES entries"
        }
        allowedLocatorKinds.forEach { requireValidLocatorKind(it) }
        require(allowedLocatorKinds.toSet().size == allowedLocatorKinds.size) {
            "allowed_locator_kinds contains a duplicate"
        }

        require(defaultBlobEndpoints.isNotEmpty()) { "default_blob_endpoints must not be empty" }
        require(defaultBlobEndpoints.size <= MAX_ENTRIES) {
            "default_blob_endpoints exceeds $MAX_ENTRIES entries"
        }
        require(defaultBlobEndpoints.toSet().size == defaultBlobEndpoints.size) {
            "default_blob_endpoints contains a duplicate"
        }
        defaultBlobEndpoints.forEach { endpoint ->
            requireValidLocatorKind(endpoint.locatorKind)
            require(endpoint.locatorKind in allowedLocatorKinds) {
                "endpoint locator kind '${endpoint.locatorKind}' is not in allowed_locator_kinds"
            }
            requireNormalizedBaseUrl(endpoint.baseUrl)
        }
    }

    /** Endpoints serving [locatorKind], in fallback priority order. */
    fun endpointsFor(locatorKind: String) = defaultBlobEndpoints.filter { it.locatorKind == locatorKind }

    fun encode(): ByteArray {
        val writer = TlsWriter()
        writer.putOpaqueVarInt(mediaFormat.encodeToByteArray())

        val kinds = TlsWriter()
        allowedLocatorKinds.forEach { kinds.putOpaqueVarInt(it.encodeToByteArray()) }
        writer.putOpaqueVarInt(kinds.toByteArray())

        val endpoints = TlsWriter()
        defaultBlobEndpoints.forEach {
            endpoints.putOpaqueVarInt(it.locatorKind.encodeToByteArray())
            endpoints.putOpaqueVarInt(it.baseUrl.encodeToByteArray())
        }
        writer.putOpaqueVarInt(endpoints.toByteArray())
        return writer.toByteArray()
    }

    companion object {
        const val COMPONENT_ID = 0x800b
        const val MEDIA_FORMAT = "encrypted-media-v2"
        const val INITIAL_LOCATOR_KIND = "blossom-v1"
        const val MAX_ENTRIES = 16
        const val MAX_BASE_URL_BYTES = 2048
        const val MAX_LOCATOR_KIND_BYTES = 64

        /** The spec's reference policy — the default a new group starts from. */
        val REFERENCE =
            EncryptedMediaPolicyV2(
                allowedLocatorKinds = listOf(INITIAL_LOCATOR_KIND),
                defaultBlobEndpoints =
                    listOf(BlobStoreEndpointV2(INITIAL_LOCATOR_KIND, "https://blossom.primal.net/")),
            )

        /**
         * Decode, rejecting anything non-canonical.
         *
         * These bytes sit in signed group state, so a lenient decoder is worse
         * than a strict one: if two peers each "repair" the same bytes
         * differently they hold different canonical values and disagree about
         * what the group's policy is. Nothing is trimmed, case-folded,
         * normalized or deduplicated on the way in — a duplicate or a
         * non-normalized URL is refused rather than fixed, and trailing bytes
         * are refused rather than ignored.
         */
        fun decode(bytes: ByteArray): EncryptedMediaPolicyV2 {
            val reader = TlsReader(bytes)
            val format = reader.readOpaqueVarInt().decodeToString()

            val kindsBlock = reader.readOpaqueVarInt()
            val kinds = mutableListOf<String>()
            val kindReader = TlsReader(kindsBlock)
            while (kindReader.remaining > 0) {
                kinds.add(kindReader.readOpaqueVarInt().decodeToString())
            }

            val endpointBlock = reader.readOpaqueVarInt()
            val endpoints = mutableListOf<BlobStoreEndpointV2>()
            val endpointReader = TlsReader(endpointBlock)
            while (endpointReader.remaining > 0) {
                val kind = endpointReader.readOpaqueVarInt().decodeToString()
                val url = endpointReader.readOpaqueVarInt().decodeToString()
                endpoints.add(BlobStoreEndpointV2(kind, url))
            }

            require(reader.remaining == 0) {
                "encrypted-media policy has ${reader.remaining} trailing byte(s)"
            }

            val decoded = EncryptedMediaPolicyV2(kinds, endpoints, format)
            require(decoded.encode().contentEquals(bytes)) {
                "encrypted-media policy bytes are not canonical"
            }
            return decoded
        }

        fun decodeOrNull(bytes: ByteArray): EncryptedMediaPolicyV2? =
            try {
                decode(bytes)
            } catch (_: Exception) {
                null
            }

        /** Lowercase ASCII letters, digits and `-`, 1..64 bytes. */
        fun requireValidLocatorKind(kind: String) {
            val bytes = kind.encodeToByteArray()
            require(bytes.isNotEmpty() && bytes.size <= MAX_LOCATOR_KIND_BYTES) {
                "locator kind must be 1..$MAX_LOCATOR_KIND_BYTES bytes, was ${bytes.size}"
            }
            require(kind.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) {
                "locator kind '$kind' has a character outside [a-z0-9-]"
            }
        }

        /**
         * A base URL is normalized when it is byte-equal to its own
         * parse-and-serialize output.
         *
         * The checks below are the structural subset that decides validity for
         * every member identically: scheme, no userinfo, a present host, and no
         * query or fragment. Reachability and whether this client is willing to
         * contact the host are LOCAL policy and must not influence whether the
         * component bytes — or the Commit carrying them — are valid; otherwise
         * one member's blocklist would fork the group.
         */
        fun requireNormalizedBaseUrl(url: String) {
            val bytes = url.encodeToByteArray()
            require(bytes.isNotEmpty() && bytes.size <= MAX_BASE_URL_BYTES) {
                "base_url must be 1..$MAX_BASE_URL_BYTES bytes, was ${bytes.size}"
            }
            val scheme =
                when {
                    url.startsWith("https://") -> "https://"
                    url.startsWith("http://") -> "http://"
                    else -> throw IllegalArgumentException("base_url must be http or https: '$url'")
                }
            require('#' !in url) { "base_url must not carry a fragment: '$url'" }
            require('?' !in url) { "base_url must not carry a query: '$url'" }

            val afterScheme = url.substring(scheme.length)
            val authority = afterScheme.substringBefore('/')
            require(authority.isNotEmpty()) { "base_url has no host: '$url'" }
            require('@' !in authority) { "base_url must not carry userinfo: '$url'" }
            require(authority == authority.lowercase()) {
                "base_url host is not normalized (lowercase): '$url'"
            }
            require("//" !in afterScheme) { "base_url path is not normalized: '$url'" }
            require(".." !in afterScheme) { "base_url path is not normalized: '$url'" }
        }
    }
}
