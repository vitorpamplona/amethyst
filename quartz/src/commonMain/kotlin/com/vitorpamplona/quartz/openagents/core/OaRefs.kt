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
package com.vitorpamplona.quartz.openagents.core

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The shared contracts' `EventRef`: one exact signed event, by id, signer and kind.
 *
 * `coordinate` is an optional NIP-01 address for the same event; it aids discovery and never
 * replaces the exact identity (a fetched event must match every field given).
 */
@Immutable
@Serializable
data class EventRef(
    val id: HexKey,
    val pubkey: HexKey,
    val kind: Int,
    val coordinate: String? = null,
) {
    /** Both keys are 64 lowercase hex, which is what the contracts require of an exact reference. */
    fun isWellFormed() = isLowerHex64(id) && isLowerHex64(pubkey) && kind >= 0
}

/**
 * The shared contracts' `ArtifactRef`: exact bytes, by digest and length, with their media type.
 *
 * `schema` names the dialect of a structured artifact (`openagents.package.v1` for a package
 * manifest); it is absent for uninterpreted bytes. `sources` are locator hints only: a bad one
 * cannot change the expected digest.
 */
@Immutable
@Serializable
data class ArtifactRef(
    val digest: String,
    val size: Long,
    @SerialName("media_type")
    val mediaType: String,
    val schema: String? = null,
    val event: EventRef? = null,
    val sources: List<ArtifactSource>? = null,
) {
    /** `sha256:` plus 64 lowercase hex. */
    fun isWellFormed() = digest.startsWith(DIGEST_PREFIX) && isLowerHex64(digest.substring(DIGEST_PREFIX.length)) && size >= 0

    companion object {
        const val DIGEST_PREFIX = "sha256:"
    }
}

/** One locator hint for an [ArtifactRef]: a URL, or a signed event that carries the bytes. */
@Immutable
@Serializable
data class ArtifactSource(
    val url: String? = null,
    val event: EventRef? = null,
)

/**
 * The shared contracts' `DefinitionRef`: a qualified component id, the exact bytes of its
 * definition, and optionally the publisher's signed declaration of them.
 */
@Immutable
@Serializable
data class DefinitionRef(
    val id: String,
    val artifact: ArtifactRef,
    val event: EventRef? = null,
)

/** 64 characters of lowercase hex: OpenAgents never accepts the upper-case form a NIP-01 parser would. */
internal fun isLowerHex64(value: String) = value.length == 64 && Hex.isHex64(value) && value.none { it in 'A'..'F' }
