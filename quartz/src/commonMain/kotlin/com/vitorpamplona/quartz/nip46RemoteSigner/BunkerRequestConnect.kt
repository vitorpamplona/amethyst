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
package com.vitorpamplona.quartz.nip46RemoteSigner

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Optional client metadata a client MAY attach to a NIP-46 `connect` request
 * (the 4th, optional `connect` parameter — https://github.com/nostr-protocol/nips/pull/2381).
 *
 * It mirrors the `name`/`url`/`image` fields already carried by `nostrconnect://`
 * URIs so a `bunker://`-paired signer can also show who is asking to connect.
 *
 * Display-only: in the `bunker://` pairing flow the client's pubkey is not
 * authenticated, so a signer MUST treat these fields as a UI hint, never as an
 * authorization mechanism.
 */
@Serializable
data class BunkerClientMetadata(
    val name: String? = null,
    val url: String? = null,
    val image: String? = null,
) {
    fun isEmpty() = name == null && url == null && image == null
}

@OptIn(ExperimentalUuidApi::class)
class BunkerRequestConnect(
    id: String = Uuid.random().toString(),
    val remoteKey: HexKey,
    val secret: HexKey? = null,
    val permissions: String? = null,
    val clientMetadata: BunkerClientMetadata? = null,
) : BunkerRequest(id, METHOD_NAME, buildParams(remoteKey, secret, permissions, clientMetadata)) {
    companion object {
        val METHOD_NAME = "connect"

        /**
         * The connect params are positional:
         * `[remote-signer-pubkey, optional_secret, optional_requested_perms, optional_client_metadata]`.
         *
         * Client metadata MUST occupy the 4th position, so when it is present we
         * back-fill the optional secret/permissions slots with empty strings
         * (per NIP-46). When it is absent we keep the array as short as possible
         * for backward compatibility.
         */
        private fun buildParams(
            remoteKey: HexKey,
            secret: HexKey?,
            permissions: String?,
            clientMetadata: BunkerClientMetadata?,
        ): Array<String> {
            val metadataJson = clientMetadata?.takeUnless { it.isEmpty() }?.let { JsonMapper.toJson(it) }
            return if (metadataJson != null) {
                arrayOf(remoteKey, secret ?: "", permissions ?: "", metadataJson)
            } else {
                listOfNotNull(remoteKey, secret, permissions).toTypedArray()
            }
        }

        fun parse(
            id: String,
            params: Array<String>,
        ): BunkerRequestConnect =
            BunkerRequestConnect(
                id = id,
                remoteKey = params[0],
                secret = params.getOrNull(1)?.takeIf { it.isNotEmpty() },
                permissions = params.getOrNull(2)?.takeIf { it.isNotEmpty() },
                clientMetadata = params.getOrNull(3)?.let(::parseMetadata),
            )

        // The metadata rides in as a JSON-stringified object. A hostile or
        // malformed value must never abort parsing of an otherwise valid
        // connect request, so failures degrade to "no metadata".
        private fun parseMetadata(json: String): BunkerClientMetadata? =
            try {
                JsonMapper.fromJson<BunkerClientMetadata>(json).takeUnless { it.isEmpty() }
            } catch (_: Exception) {
                null
            }
    }
}

public fun <T> Array<T>.getOrNull(index: Int): T? = if (index in 0..<size) get(index) else null
