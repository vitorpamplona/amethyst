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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

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

    /** Parses a Banlist edition's content (a bare JSON array of hex pubkeys). */
    fun decodeBanlist(content: String): List<String>? =
        try {
            instance.decodeFromString(ListSerializer(String.serializer()), content)
        } catch (_: Exception) {
            null
        }
}

/**
 * A Role's content (CORD-04): a named bundle of permissions at a [position].
 * The role's id is the edition's entity id, not a content field. Lower [position]
 * ranks higher; no role may claim position 0 (reserved for the owner).
 */
@Serializable
class RoleEntity(
    val name: String = "",
    val position: Long = 0,
    /** u64 permission bitfield as a decimal string. */
    val permissions: String = "0",
    /** "server"-wide, or a channel id for a channel-scoped role. Null = server. */
    val scope: String? = null,
    val color: Long = 0,
    val deleted: Boolean = false,
) {
    fun permissionBits(): ConcordPermissions = ConcordPermissions.fromWireOrNull(permissions) ?: ConcordPermissions.NONE
}

/**
 * A Grant's content (CORD-04): maps a [member] to the set of [roleIds] they hold.
 * Honored only if the granting actor outranks every assigned Role and the chain
 * terminates at the owner (see [AuthorityResolver]).
 */
@Serializable
class GrantEntity(
    val member: String = "",
    @SerialName("role_ids") val roleIds: List<String> = emptyList(),
)

/**
 * A Channel's content (CORD-03). The channel id is the edition entity id.
 * [private] selects derived-key visibility; [voice] flags an audio channel.
 * A [deleted] channel is terminal — its id is never reused.
 */
@Serializable
class ChannelEntity(
    val name: String = "",
    val private: Boolean = false,
    val voice: Boolean = false,
    val deleted: Boolean = false,
)

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
class MetadataEntity(
    val name: String = "",
    val icon: ImagePointer? = null,
    val banner: ImagePointer? = null,
    val description: String? = null,
    val relays: List<String> = emptyList(),
)
