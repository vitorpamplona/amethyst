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
package com.vitorpamplona.quartz.buzz.mpProjects.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.arrayOfNotNull

/**
 * One member repository of a NIP-MP project: the NIP-34 repository announcement's
 * [address] (`30617:<owner>:<repo-d>`) and an optional, opaque, unauthenticated
 * [relayHint]. Member identity is the coordinate alone — two tags naming one coordinate with
 * different hints are duplicates.
 */
@Immutable
data class ProjectMember(
    val address: Address,
    val relayHint: String? = null,
) {
    /** The canonical coordinate string, the identity members are compared by. */
    val coordinate: String get() = address.toValue()
}

/**
 * A NIP-MP member `a` tag: `["a", "30617:<owner>:<repo-d>"]` or
 * `["a", "30617:<owner>:<repo-d>", "<relay-url>"]`.
 *
 * The coordinate grammar is stricter than a generic NIP-01 address: the kind is the literal
 * `30617` (a repository *announcement*, never `30618` state), the owner is exactly 64
 * **lowercase** hex characters (`#a` matching is byte-exact, so an uppercase owner would be
 * invisible to readers), and the repo `d` is non-empty and taken verbatim — the value splits
 * on the first two colons only, so a repo `d` containing `:` stays addressable. The relay
 * hint is never parsed or validated by content. Ground truth:
 * `parse_project_member_coordinate` in Buzz's `buzz-relay/src/handlers/ingest.rs` and
 * `ProjectMemberCoord` in `buzz-sdk/src/builders.rs`.
 */
object ProjectMemberTag {
    const val TAG_NAME = "a"
    const val MEMBER_KIND = GitRepositoryEvent.KIND
    private const val MEMBER_KIND_SEGMENT = "30617"

    /** Parses a strict member coordinate, or null when it is malformed. */
    fun parseCoordinate(coordinate: String): Address? {
        val parts = coordinate.split(':', limit = 3)
        if (parts.size != 3) return null
        val (kind, owner, repoD) = parts
        if (kind != MEMBER_KIND_SEGMENT) return null
        if (!isLowercaseHex64(owner)) return null
        if (repoD.isEmpty()) return null
        return Address(MEMBER_KIND, owner, repoD)
    }

    /**
     * The same rules as [parseCoordinate], checked in place: `30617:`, 64 lowercase hex, `:`,
     * then a non-empty repo `d`. Hex holds no `:`, so a fixed-offset scan is exactly the
     * first-two-colons split. Allocates nothing — it backs the linked-id and hint paths, which
     * run on every relay copy of every project event.
     */
    fun isValidCoordinate(coordinate: String): Boolean {
        val ownerStart = MEMBER_KIND_SEGMENT.length + 1
        val ownerEnd = ownerStart + 64
        if (coordinate.length <= ownerEnd + 1) return false
        if (!coordinate.startsWith(MEMBER_KIND_SEGMENT) || coordinate[ownerStart - 1] != ':') return false
        for (i in ownerStart until ownerEnd) {
            val c = coordinate[i]
            if (c !in '0'..'9' && c !in 'a'..'f') return false
        }
        return coordinate[ownerEnd] == ':'
    }

    /**
     * Parses a member tag with the arity (two or three elements) and coordinate rules the
     * relay enforces; null for anything else.
     */
    fun parse(tag: Tag): ProjectMember? {
        if (tag.size !in 2..3 || tag[0] != TAG_NAME) return null
        val address = parseCoordinate(tag[1]) ?: return null
        return ProjectMember(address, tag.getOrNull(2))
    }

    /**
     * The member's canonical `30617:<owner>:<repo>` coordinate, for every well-formed member tag.
     * A valid coordinate is already canonical (the kind is a literal, the owner must be
     * lowercase, the `d` is verbatim), so it is `tag[1]` itself, validated, not rebuilt.
     */
    fun parseAddressId(tag: Tag): String? = if (isMemberTag(tag)) tag[1] else null

    /** The member coordinate with its relay hint, only when the tag carries a valid relay URL. */
    fun parseAsHint(tag: Tag): AddressHint? {
        // Arity 3: the hint slot must exist; normalizeHintOrNull rejects an empty one.
        if (tag.size != 3 || !isMemberTag(tag)) return null
        val relay = RelayUrlNormalizer.normalizeHintOrNull(tag[2]) ?: return null
        return AddressHint(tag[1], relay)
    }

    // The arity, name and coordinate checks of [parse], without building the member.
    private fun isMemberTag(tag: Tag) = tag.size in 2..3 && tag[0] == TAG_NAME && isValidCoordinate(tag[1])

    fun assemble(
        coordinate: String,
        relayHint: String? = null,
    ) = arrayOfNotNull(TAG_NAME, coordinate, relayHint)

    fun assemble(member: ProjectMember) = assemble(member.coordinate, member.relayHint)

    fun assemble(
        owner: HexKey,
        repoD: String,
        relayHint: String? = null,
    ) = assemble("$MEMBER_KIND_SEGMENT:$owner:$repoD", relayHint)

    private fun isLowercaseHex64(value: String): Boolean {
        if (value.length != 64) return false
        for (c in value) {
            if (c !in '0'..'9' && c !in 'a'..'f') return false
        }
        return true
    }
}
