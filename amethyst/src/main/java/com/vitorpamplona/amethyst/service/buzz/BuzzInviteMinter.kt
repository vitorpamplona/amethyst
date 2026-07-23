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
package com.vitorpamplona.amethyst.service.buzz

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Mints a `block/buzz` workspace invite link by POSTing to the relay's **Buzz-specific**
 * `/api/invites` HTTP endpoint (NIP-98 signed; owner/admin only). Not a Nostr event and not a NIP —
 * only the NIP-98 auth is standard — so this is Buzz-only and lives outside quartz.
 *
 * The endpoint host is the relay's own host (the invite link is `https://<host>/invite/<code>`), so
 * we never need to own a domain — the relay does, and it checks the signer is an owner/admin. See
 * `crates/buzz-relay/src/api/invites.rs`.
 */
object BuzzInviteMinter {
    private val json = ObjectMapper()

    /** A freshly minted invite: the opaque [code], the shareable [url], and its [expiresAt] (secs). */
    data class MintedInvite(
        val code: String,
        val url: String,
        val expiresAt: Long,
    )

    /**
     * POST `/api/invites` on [relay]'s host with an optional [ttlSecs] (relay clamps to [60, 30d];
     * default 72 h). [httpAuth] signs the NIP-98 event over the exact URL + body; [okHttpClient]
     * supplies the transport (use a trusted-relay-posture client so a Cloudflare-fronted relay is
     * reached over clearnet). Throws [IllegalStateException] with the relay's error slug on failure.
     */
    suspend fun mint(
        relay: NormalizedRelayUrl,
        ttlSecs: Long?,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: suspend (url: String, method: String, body: ByteArray?) -> HTTPAuthorizationEvent,
    ): MintedInvite =
        withContext(Dispatchers.IO) {
            // wss://host[/..] -> https://host ; ws://host -> http://host. The endpoint is host-root.
            val wsUrl = relay.url
            val scheme = if (wsUrl.startsWith("wss", ignoreCase = true)) "https" else "http"
            val host = wsUrl.substringAfter("://").substringBefore("/")
            val url = "$scheme://$host/api/invites"

            // Exact bytes the NIP-98 payload hash is computed over — must equal what we send.
            val bodyStr = ttlSecs?.let { "{\"ttl_secs\":$it}" } ?: "{}"
            val bodyBytes = bodyStr.toByteArray(Charsets.UTF_8)

            val auth = httpAuth(url, "POST", bodyBytes)

            val request =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("Authorization", auth.toAuthToken())
                    .post(bodyBytes.toRequestBody("application/json".toMediaType()))
                    .build()

            okHttpClient(url).newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                val tree = runCatching { json.readTree(payload) }.getOrNull()

                if (!response.isSuccessful) {
                    val slug = tree?.get("error")?.asText() ?: "HTTP ${response.code}"
                    throw IllegalStateException(slug)
                }

                MintedInvite(
                    code = tree?.get("code")?.asText().orEmpty(),
                    url = tree?.get("url")?.asText().orEmpty(),
                    expiresAt = tree?.get("expires_at")?.asLong() ?: 0L,
                )
            }
        }
}
