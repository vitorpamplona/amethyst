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
package com.vitorpamplona.quartz.buzz.invite

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A parsed Buzz workspace invite link: `https://<host>/invite/<code>`, where `<code>` is a
 * relay-signed token in one of two shapes:
 *
 * - `<payloadB64url>.<sigB64url>` (base64url, JWT-style but not a JWT) — the payload names the
 *   community, the granted role, an expiry and a nonce.
 * - `v2.<opaqueB64url>` — a bare server-side handle: `v2.` plus the unpadded, canonical base64url
 *   encoding of a 32-byte secret (exactly what `validate_v2_code` in `buzz-core/src/invite.rs`
 *   accepts — see [BuzzInviteLink.isValidV2Code]). Nothing about the invite is readable here; the
 *   relay resolves it on claim.
 *
 * A Buzz invite is **not** a NIP-29 invite code (kind 9009) — it is redeemed over HTTP against
 * the relay's tenant host: `POST /api/invites/claim`, NIP-98-signed by the joining key, after
 * accepting any configured join policy. The relay verifies the token's MAC (its own key), so a
 * client only needs to read the payload, never validate the signature.
 *
 * Ground truth: `buzz-relay/src/invite_token.rs` (token shape + `verify_invite`),
 * `buzz-core/src/invite.rs` (`validate_v2_code`) and `buzz-relay/src/api/invites.rs`
 * (`claim_invite`, `accept_policy`).
 */
data class BuzzInvite(
    /** The tenant host the invite is scoped to, e.g. `team.communities.buzz.xyz`. */
    val host: String,
    /** The full opaque token (`payload.sig`) to hand back to the relay's claim endpoint. */
    val code: String,
    /**
     * The community (workspace/tenant) UUID the invite admits into — the payload's `c`. Empty for
     * a `v2.` token, whose code is opaque: the relay resolves the community on claim and returns
     * it, so nothing client-side needs it (the join hands off to the tenant host, not the id).
     */
    val communityId: String,
    /** The role granted on claim (e.g. `member`) — the payload's `r`, or [DEFAULT_ROLE] for `v2.`. */
    val role: String,
    /** Unix-seconds expiry, or null when the payload omits it (always for `v2.`) — the payload's `e`. */
    val expiresAt: Long?,
) {
    /** The tenant's relay websocket URL. */
    fun relayUrl(): String = "wss://$host"

    /** The tenant's HTTPS base for the invite/policy REST endpoints. */
    fun httpBase(): String = "https://$host"

    /** True when [nowSecs] is at or past the invite's expiry (client-side courtesy check). */
    fun isExpired(nowSecs: Long): Boolean = expiresAt != null && nowSecs >= expiresAt
}

object BuzzInviteLink {
    private const val MARKER = "/invite/"

    /** The payload segment of an opaque, server-resolved token. */
    private const val V2_PREFIX = "v2"

    /** The full prefix the relay routes on (`V2_PREFIX` in `buzz-core/src/invite.rs`). */
    const val V2_CODE_PREFIX = "v2."

    /** Random bytes behind a `v2.` code (`V2_SECRET_LEN` in `buzz-core/src/invite.rs`). */
    const val V2_SECRET_LEN = 32

    @OptIn(ExperimentalEncodingApi::class)
    private val B64_NO_PAD = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** What the relay grants when the token doesn't say — and it never says for `v2.`. */
    private const val DEFAULT_ROLE = "member"

    private val JSON = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Payload(
        val c: String? = null,
        val r: String? = null,
        val e: Long? = null,
        val n: String? = null,
    )

    /**
     * Parses a Buzz invite URL. Accepts the full `https://<host>/invite/<code>` link (any
     * scheme) and tolerates a trailing `#fragment` or `?query`. Returns null when the URL is
     * not an invite link or the token payload can't be read — including the Concord invite
     * shape (`/invite/<naddr>#<fragment>`), which carries no `.`-separated base64 payload, so
     * the two link families never collide.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun parse(url: String): BuzzInvite? {
        val trimmed = url.trim()
        val marker = trimmed.indexOf(MARKER)
        if (marker < 0) return null

        val host = extractHost(trimmed, marker) ?: return null

        val afterMarker = trimmed.substring(marker + MARKER.length)
        val code =
            afterMarker
                .substringBefore('#')
                .substringBefore('?')
                .trim()
        if (code.isEmpty()) return null

        // Token = <payloadB64url>.<sigB64url>. No dot → not a Buzz invite (e.g. a Concord naddr).
        val payloadB64 = code.substringBefore('.')
        if (payloadB64 == code || payloadB64.isEmpty()) return null

        // `v2.<opaque>` carries no client-readable payload — the community, role and expiry live
        // only on the relay, which resolves the code on claim and returns them. The join flow
        // needs the host (for the relay url and the REST base) and the code, both of which the url
        // itself carries, and the claim response supplies the rest. The relay routes on this exact
        // prefix and never falls back to the v1 verifier, so a malformed `v2.` code is rejected
        // here too rather than handed to a claim that can only answer `invite_invalid`.
        if (payloadB64 == V2_PREFIX) {
            if (!isValidV2Code(code)) return null
            return BuzzInvite(host = host, code = code, communityId = "", role = DEFAULT_ROLE, expiresAt = null)
        }

        val payload =
            try {
                val bytes = Base64.UrlSafe.decode(padBase64(payloadB64))
                JSON.decodeFromString<Payload>(bytes.decodeToString())
            } catch (_: Exception) {
                return null
            }

        val community = payload.c?.takeIf { it.isNotBlank() } ?: return null
        return BuzzInvite(
            host = host,
            code = code,
            communityId = community,
            role = payload.r?.takeIf { it.isNotBlank() } ?: DEFAULT_ROLE,
            expiresAt = payload.e,
        )
    }

    /**
     * True when [code] is exactly `v2.` followed by the unpadded base64url encoding of a 32-byte
     * secret, in its canonical form — mirroring `validate_v2_code` in `buzz-core/src/invite.rs`,
     * whose decode/re-encode comparison rejects padded or otherwise aliased encodings.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun isValidV2Code(code: String): Boolean {
        if (!code.startsWith(V2_CODE_PREFIX)) return false
        val encoded = code.substring(V2_CODE_PREFIX.length)
        val secret =
            try {
                B64_NO_PAD.decode(encoded)
            } catch (_: IllegalArgumentException) {
                return false
            }
        return secret.size == V2_SECRET_LEN && B64_NO_PAD.encode(secret) == encoded
    }

    /** The host between the scheme's `//` and the `/invite/` marker, or null when absent. */
    private fun extractHost(
        url: String,
        marker: Int,
    ): String? {
        val schemeEnd = url.indexOf("//")
        val start = if (schemeEnd in 0 until marker) schemeEnd + 2 else 0
        val host = url.substring(start, marker).trim()
        return host.takeIf { it.isNotEmpty() && '/' !in it }
    }

    /** Right-pads a base64url string to a multiple of 4 so the padded decoder accepts it. */
    private fun padBase64(s: String): String {
        val remainder = s.length % 4
        return if (remainder == 0) s else s + "=".repeat(4 - remainder)
    }
}

/**
 * The outcome vocabulary of Buzz's invite claim endpoint (`POST /api/invites/claim` in
 * `buzz-relay/src/api/invites.rs`). A success answers 200 with a [STATUS_JOINED] or
 * [STATUS_ALREADY_MEMBER] `status`; a refusal answers 403 with `{"error": <slug>}`.
 */
object BuzzInviteClaim {
    /** 200: the claimer was added to the workspace. */
    const val STATUS_JOINED = "joined"

    /** 200: the claimer was already a member — nothing changed, and no use was consumed. */
    const val STATUS_ALREADY_MEMBER = "already_member"

    /** 403: the invite's lifetime has passed. */
    const val ERROR_EXPIRED = "invite_expired"

    /** 403: a `v2.` invite minted with `max_uses` has no uses left. */
    const val ERROR_EXHAUSTED = "invite_exhausted"

    /** 403: unknown, revoked, malformed, or minted for another workspace. */
    const val ERROR_INVALID = "invite_invalid"

    /** 403: the workspace has a join policy and no valid acceptance receipt was sent. */
    const val ERROR_JOIN_POLICY_REQUIRED = "join_policy_required"

    private val JSON = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Body(
        val status: String? = null,
        val error: String? = null,
    )

    /** The `status` of a claim response body, or null when absent/unparseable. */
    fun statusOf(body: String): String? = parse(body)?.status

    /** The `error` slug of a claim response body, or null when absent/unparseable. */
    fun errorOf(body: String): String? = parse(body)?.error

    private fun parse(body: String): Body? =
        try {
            JSON.decodeFromString<Body>(body)
        } catch (_: Exception) {
            null
        }
}
