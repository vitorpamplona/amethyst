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
package com.vitorpamplona.quartz.buzz.teamCatalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The JSON body of a Buzz team-catalog event ([TeamCatalogEvent], `kind:30178`): a team plus
 * every member's safe, portable definition embedded in full, so a recipient can rebuild the
 * team without reading the owner's personas.
 *
 * Mirrors `TeamCatalogContent` in Buzz's `desktop/src-tauri/src/managed_agents/team_catalog.rs`
 * field for field **and in declaration order**: serde emits in that order, the content bytes
 * are the canonical projection (they fix the event id, and the owner's freshness reconcile
 * compares them), so a reorder here would read as a changed team. Optional fields are omitted
 * when absent ([JSON] has `encodeDefaults = false`, matching serde's `skip_serializing_if`),
 * while [v] and [members] are always written and are required on read — a body without `v`
 * must not masquerade as v1.
 *
 * [validate] ports the v1 size/shape contract (`validate_team_catalog_content`); see its KDoc
 * for the parts it leaves out.
 */
@Serializable
data class TeamCatalogContent(
    /** Schema version; readers must refuse anything but [SCHEMA_VERSION]. */
    val v: Int,
    val name: String,
    val description: String? = null,
    val instructions: String? = null,
    /** Members in the team's own order — part of the canonical bytes. */
    val members: List<TeamCatalogMember>,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    /**
     * Returns the first reason upstream's reader would refuse this body, or null when it
     * passes: the schema version, the team name/description/instructions bounds, the member
     * cap, every member's field contract, unique member keys, and the total encoded size.
     *
     * Not ported: the concealment rule on visible text (`validate_visible_text` —
     * invisible/bidi control rejection) and the recomputation of `projection_hash` from the
     * member's own fields; only the hint pair's completeness and hash shape are checked. The
     * avatar URL check approximates upstream's WHATWG parse (see [isSafeCatalogAvatarUrl]).
     */
    fun validate(): String? {
        if (v != SCHEMA_VERSION) return "unsupported team catalog schema version $v (expected $SCHEMA_VERSION)"
        if (name.isBlank()) return "invalid team projection: the team name is empty"
        if (name.utf8Size() > MAX_NAME_BYTES) return "team too large to share: the team name exceeds $MAX_NAME_BYTES bytes"
        if (description != null && description.utf8Size() > MAX_TEXT_BYTES) return "team too large to share: the team description exceeds $MAX_TEXT_BYTES bytes"
        if (instructions != null && instructions.utf8Size() > MAX_INSTRUCTIONS_BYTES) return "team too large to share: the team instructions exceed $MAX_INSTRUCTIONS_BYTES bytes"
        if (members.size > MAX_MEMBERS) return "team too large to share: ${members.size} members (limit $MAX_MEMBERS)"
        val seen = HashSet<String>()
        for (member in members) {
            member.validate()?.let { return it }
            if (!seen.add(member.memberKey)) return "invalid team projection: '${member.displayName}' repeats the member key '${member.memberKey}' of an earlier member"
        }
        val encoded = encodeToJson().utf8Size()
        if (encoded > MAX_TOTAL_BYTES) return "team too large to share: the projection is $encoded bytes (limit $MAX_TOTAL_BYTES)"
        return null
    }

    fun isValid() = validate() == null

    companion object {
        const val SCHEMA_VERSION = 1

        const val MAX_MEMBERS = 64
        const val MAX_NAME_BYTES = 256
        const val MAX_TEXT_BYTES = 4 * 1024
        const val MAX_INSTRUCTIONS_BYTES = 16 * 1024
        const val MAX_SYSTEM_PROMPT_BYTES = 16 * 1024
        const val MAX_AVATAR_URL_BYTES = 32 * 1024
        const val MAX_NAME_POOL_ENTRIES = 64
        const val MAX_TOTAL_BYTES = 192 * 1024
        const val MAX_MEMBER_KEY_BYTES = 128
        const val MAX_IDENTIFIER_BYTES = 256
        const val MAX_BUILTIN_SLUG_BYTES = 128
        const val PROJECTION_HASH_HEX_LEN = 64

        /** The `respond_to` modes upstream accepts (`RespondTo::parse_wire`). */
        val RESPOND_TO_MODES = setOf("owner-only", "allowlist", "anyone")

        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = false
            }

        fun decodeFromJson(json: String): TeamCatalogContent = JSON.decodeFromString(json)

        /**
         * Upstream's avatar allowlist (`is_safe_catalog_avatar_url`): an `http(s)` URL of at
         * most 2048 bytes with no whitespace or parentheses, an inline
         * `data:image/svg+xml,` of at most 8192 bytes, or a strict-base64 inline
         * png/jpeg/gif/webp of at most 256 KiB. Upstream also runs the WHATWG URL parser on
         * the `http(s)` form; this checks only the scheme, so a malformed authority passes here.
         */
        fun isSafeCatalogAvatarUrl(url: String): Boolean {
            if (!url.startsWith("data:")) {
                if (url.utf8Size() > 2048) return false
                if (url.any { (it.isWhitespace() && it != '\u0085') || it == '\uFEFF' || it == '(' || it == ')' }) return false
                val lower = url.lowercase()
                return lower.startsWith("http:") || lower.startsWith("https:")
            }
            if (url.startsWith("data:image/svg+xml,")) return url.length <= 8192
            if (url.length > 256 * 1024) return false
            val rest = url.removePrefix("data:image/")
            if (rest.length == url.length) return false
            for (mime in listOf("png", "jpeg", "gif", "webp")) {
                val prefix = "$mime;base64,"
                if (!rest.startsWith(prefix)) continue
                val b64 = rest.substring(prefix.length)
                val trimmed = b64.trimEnd('=')
                if (b64.length - trimmed.length <= 2 &&
                    trimmed.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' } &&
                    b64.length % 4 == 0
                ) {
                    return true
                }
            }
            return false
        }

        internal fun String.utf8Size() = encodeToByteArray().size
    }
}

/**
 * One member's safe definition inside a [TeamCatalogContent]. Mirrors `TeamCatalogMember` in
 * Buzz's `desktop/src-tauri/src/managed_agents/team_catalog.rs`, in declaration order.
 *
 * [memberKey] is an opaque, publication-unique identity (a domain-separated SHA-256 of the
 * publisher's local id) — never resolve it as a `kind:30175` coordinate. [builtinSlug] and
 * [projectionHash] are an all-or-nothing reuse *hint*, not an identity.
 */
@Serializable
data class TeamCatalogMember(
    @SerialName("member_key") val memberKey: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val runtime: String? = null,
    @SerialName("acp_command") val acpCommand: String? = null,
    val model: String? = null,
    val provider: String? = null,
    @SerialName("name_pool") val namePool: List<String> = emptyList(),
    @SerialName("respond_to") val respondTo: String? = null,
    val parallelism: Int? = null,
    @SerialName("session_policy") val sessionPolicy: AcpSessionPolicy = AcpSessionPolicy.CHANNEL,
    @SerialName("builtin_slug") val builtinSlug: String? = null,
    @SerialName("projection_hash") val projectionHash: String? = null,
) {
    /** The first v1-contract violation (`validate_member` upstream), or null. */
    fun validate(): String? {
        val who = displayName
        if (memberKey.isBlank()) return "invalid team projection: a member key is empty"
        if (memberKey.utf8Size() > TeamCatalogContent.MAX_MEMBER_KEY_BYTES) return "team too large to share: a member key is too long"
        if (displayName.isBlank()) return "invalid team projection: a member display name is empty"
        if (displayName.utf8Size() > TeamCatalogContent.MAX_NAME_BYTES) return "team too large to share: a member display name is too long"
        if (systemPrompt != null && systemPrompt.utf8Size() > TeamCatalogContent.MAX_SYSTEM_PROMPT_BYTES) return "team too large to share: the system prompt for '$who' is too long"
        if (avatarUrl != null) {
            if (avatarUrl.utf8Size() > TeamCatalogContent.MAX_AVATAR_URL_BYTES) return "team too large to share: the avatar for '$who' is too large"
            if (!TeamCatalogContent.isSafeCatalogAvatarUrl(avatarUrl)) return "invalid team projection: the avatar for '$who' uses an unsafe URL scheme"
        }
        for ((value, label) in listOf(runtime to "runtime", model to "model", provider to "provider")) {
            if (value == null) continue
            if (value.isBlank()) return "invalid team projection: the $label for '$who' is empty"
            if (value.utf8Size() > TeamCatalogContent.MAX_IDENTIFIER_BYTES) return "team too large to share: the $label for '$who' is too long"
        }
        if (namePool.size > TeamCatalogContent.MAX_NAME_POOL_ENTRIES) return "team too large to share: '$who' has ${namePool.size} name-pool entries"
        for (entry in namePool) {
            if (entry.isBlank()) return "invalid team projection: a name-pool entry for '$who' is empty"
            if (entry.utf8Size() > TeamCatalogContent.MAX_NAME_BYTES) return "team too large to share: a name-pool entry for '$who' is too long"
        }
        if (respondTo != null && respondTo !in TeamCatalogContent.RESPOND_TO_MODES) return "definition respond_to '$respondTo' is not a recognized mode"
        if (parallelism != null && parallelism !in 1..32) return "invalid team projection: parallelism $parallelism for '$who' is out of range"
        if ((builtinSlug == null) != (projectionHash == null)) return "invalid team projection: '$who' has an incomplete built-in reuse hint"
        if (builtinSlug != null && projectionHash != null) {
            if (builtinSlug.isBlank()) return "invalid team projection: the built-in slug for '$who' is empty"
            if (builtinSlug.utf8Size() > TeamCatalogContent.MAX_BUILTIN_SLUG_BYTES) return "team too large to share: the built-in slug for '$who' is too long"
            if (projectionHash.length != TeamCatalogContent.PROJECTION_HASH_HEX_LEN ||
                !projectionHash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            ) {
                return "invalid team projection: the reuse hash for '$who' is not a SHA-256 hex digest"
            }
        }
        return null
    }

    private fun String.utf8Size() = encodeToByteArray().size
}
