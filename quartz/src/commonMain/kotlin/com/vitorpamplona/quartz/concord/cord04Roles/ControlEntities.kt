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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.floor

/**
 * JSON facility for Concord Control Plane entity content. Unknown keys are
 * ignored because entity shapes are deliberately client-extensible (CORD-03/04),
 * and defaults are lenient so a partial entity never throws mid-fold.
 */
object ConcordJson {
    val instance =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    inline fun <reified T> decodeOrNull(content: String): T? =
        try {
            instance.decodeFromString<T>(content)
        } catch (_: Exception) {
            null
        }

    /**
     * Encodes [value] as the next edition's content **without losing what the previous edition
     * carried and we don't model** (CORD-02 §6: "an editor MUST round-trip fields it doesn't
     * understand"). Every key [serializer] declares is ours to set — including to absent, so a form
     * can clear an optional field — and every other key of [previousContent] (another client's
     * `custom`, a newer protocol field like `av_brokers`) rides through verbatim.
     *
     * [previousContent] is the entity's current authorized head, or null for a genesis edition. A
     * head that is not a JSON object contributes nothing.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun <T> encodePreserving(
        serializer: KSerializer<T>,
        value: T,
        previousContent: String?,
    ): String {
        val next = instance.encodeToJsonElement(serializer, value).jsonObject
        val previous =
            previousContent?.let {
                try {
                    instance.parseToJsonElement(it) as? JsonObject
                } catch (_: Exception) {
                    null
                }
            } ?: return instance.encodeToString(JsonObject.serializer(), next)
        val managed = serializer.descriptor.elementNames.toSet()
        val kept = previous.filterKeys { it !in managed }
        if (kept.isEmpty()) return instance.encodeToString(JsonObject.serializer(), next)
        return instance.encodeToString(JsonObject.serializer(), JsonObject(next + kept))
    }

    /** Parses a Banlist edition's content (a bare JSON array of hex pubkeys). */
    fun decodeBanlist(content: String): List<String>? =
        try {
            instance.decodeFromString(ListSerializer(String.serializer()), content)
        } catch (_: Exception) {
            null
        }
}

/**
 * A Role's scope (CORD-04 §2): server-wide (`{"kind":"server"}`) or restricted to a
 * single channel (`{"kind":"channel","channel_id":"<hex32>"}`). It is an **object** on
 * the wire, pinned to the Concord v2 reference client — NOT a bare string. Typing it as
 * a `String` (the old bug) makes the whole [RoleEntity] fail to decode, which silently
 * drops the role, and with it every authority (grant) that depends on it.
 */
@Serializable
data class RoleScope(
    val kind: String = "server",
    @SerialName("channel_id") val channelId: String? = null,
)

/**
 * A Role's content (CORD-04): a named bundle of permissions at a [position].
 * Lower [position] ranks higher; no role may claim position 0 (reserved for the owner).
 *
 * [roleId] is the role's own id, which the spec puts in the content (CORD-04 §2) and which
 * must equal the edition's `eid`. The reference client drops a role without it, so every
 * role we write carries it; roles minted before this client wrote it are still read (the
 * `eid` is then the id), but a role whose [roleId] names a *different* coordinate is refused
 * ([isWellFormedAt]).
 */
@Serializable
data class RoleEntity(
    @SerialName("role_id") val roleId: String? = null,
    val name: String = "",
    val position: Long = 0,
    /** u64 permission bitfield as a decimal string. */
    val permissions: String = "0",
    /** Server-wide, or a single channel — an object (see [RoleScope]). Null = server. */
    val scope: RoleScope? = null,
    val color: Long = 0,
    val deleted: Boolean = false,
) {
    fun permissionBits(): ConcordPermissions = ConcordPermissions.fromWireOrNull(permissions) ?: ConcordPermissions.NONE

    /**
     * Whether this content may stand as the role at coordinate [entityIdHex] (CORD-04 §2/§3):
     * its [roleId], when present, names that coordinate; its name fits the 64-byte cap; and a
     * live role claims a position below the owner's 0. Mirrors Armada's `roleFromJSON`, except
     * that a legacy role with no [roleId] is still accepted.
     */
    fun isWellFormedAt(entityIdHex: String): Boolean {
        if (roleId != null && !roleId.equals(entityIdHex, ignoreCase = true)) return false
        if (!ConcordLimits.nameFits(name)) return false
        return deleted || position >= 1
    }
}

/**
 * A Grant's content (CORD-04): maps a [member] to the set of [roleIds] they hold.
 * Honored only if the granting actor outranks every assigned Role and the chain
 * terminates at the owner (see [AuthorityResolver]).
 *
 * A staff-making Grant also delivers the Control Plane write secret in
 * [controlWrap] (CORD-04 §3): the `control_root` NIP-44-encrypted under the
 * granter↔member pairwise conversation key, its plaintext the fixed-width 40
 * bytes `epoch_be[8] ‖ control_root[32]` (see [ControlRootWrap]). Delivery, never
 * authority — every reader but the member treats it as opaque bytes, and the
 * member adopts the secret only if it derives to the `control_pk` they hold for
 * the named epoch.
 */
@Serializable
data class GrantEntity(
    val member: String = "",
    @SerialName("role_ids") val roleIds: List<String> = emptyList(),
    @SerialName("control_wrap") val controlWrap: String? = null,
)

/**
 * A Channel's content (CORD-03). The channel id is the edition entity id.
 * [private] selects derived-key visibility. A [deleted] channel is terminal — its id is never
 * reused.
 *
 * There is no voice flag: every Channel is callable (CORD-07). A `voice` key an older client
 * wrote is not ours to interpret; it rides through edits untouched like any unknown field
 * ([ConcordJson.encodePreserving]), as does the optional `custom` object (CORD-02 §6).
 */
@Serializable
data class ChannelEntity(
    val name: String = "",
    val private: Boolean = false,
    val deleted: Boolean = false,
) {
    /** True when [name] is within the protocol's name rule ([isValidName]). */
    fun hasValidName(): Boolean = isValidName(name)

    companion object {
        /** The protocol-wide name cap, in UTF-8 bytes (CORD-03 §2, CORD-04). */
        const val NAME_MAX_BYTES = 64

        /**
         * A Channel name must be non-empty and at most [NAME_MAX_BYTES] UTF-8 bytes. Enforced when
         * building an edition and again when folding one: an edition naming an empty or over-cap
         * Channel is unauthorized, and the fold falls back to the previous candidate (the reference
         * client's channel gate).
         */
        fun isValidName(name: String): Boolean = name.isNotEmpty() && name.encodeToByteArray().size <= NAME_MAX_BYTES
    }
}

/**
 * A community's Metadata content (CORD-02): display [name], optional [description], the community's
 * bootstrap [relays], and the encrypted-media [icon]/[banner] pointers. Client-extensible.
 *
 * [icon]/[banner] are CORD-02 §6 [ImagePointer]s (an object `{url,key,nonce,hash}`), NOT plain URLs —
 * the wire shape is pinned to the Concord v2 reference client. Deserializing them into anything else
 * (e.g. a `String`) fails the whole entity's decode, which is why a wrong type silently drops the
 * community name too.
 */
@Serializable
data class MetadataEntity(
    val name: String = "",
    val icon: ImagePointer? = null,
    val banner: ImagePointer? = null,
    val description: String? = null,
    val relays: List<String> = emptyList(),
    /**
     * The disappearing-messages timer (CORD-08 §1) exactly as the edition carried it. Kept raw so
     * a malformed value is carried through an edit untouched; read it through [messageExpirationSecs].
     */
    @SerialName("message_expiration") val messageExpiration: JsonElement? = null,
) {
    /**
     * The disappearing-messages timer in whole seconds, or null when it is off (CORD-08 §1).
     * Absent, `0`, negative or malformed (a string, an object, a non-finite number) all read as
     * off — a reader MUST NOT guess a default from garbage. A fractional value floors, as the
     * reference client does.
     */
    fun messageExpirationSecs(): Long? {
        val primitive = messageExpiration as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        val value = primitive.doubleOrNull ?: return null
        if (!value.isFinite()) return null
        val secs = floor(value)
        if (secs < 1 || secs > Long.MAX_VALUE.toDouble()) return null
        return secs.toLong()
    }

    /** This metadata with the timer set to [secs], or turned off when [secs] is null or below 1. */
    fun withMessageExpiration(secs: Long?): MetadataEntity = copy(messageExpiration = secs?.takeIf { it >= 1 }?.let { JsonPrimitive(it) })
}
