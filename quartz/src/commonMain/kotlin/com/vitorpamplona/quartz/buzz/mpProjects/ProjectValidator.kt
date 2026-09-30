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
package com.vitorpamplona.quartz.buzz.mpProjects

import com.vitorpamplona.quartz.buzz.mpProjects.tags.ChannelTag
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectMemberTag
import com.vitorpamplona.quartz.buzz.mpProjects.tags.VisibilityTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.DescriptionTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.NameTag

/**
 * The eight NIP-MP ingest rules. [id] is the stable identifier the shared conformance
 * fixtures (`docs/nips/NIP-MP.fixtures.json` in Buzz) name in `reject_rules`.
 */
enum class ProjectRule(
    val id: String,
) {
    D_CARDINALITY("d-cardinality"),
    D_EMPTY("d-empty"),
    MEMBER_CAP("member-cap"),
    MEMBER_TAG_ARITY("member-tag-arity"),
    MEMBER_COORDINATE_MALFORMED("member-coordinate-malformed"),
    MEMBER_DUPLICATE("member-duplicate"),
    METADATA_CARDINALITY("metadata-cardinality"),
    METADATA_LENGTH("metadata-length"),
}

/** A rejected project envelope: which [rule] fired and why. */
data class ProjectRejection(
    val rule: ProjectRule,
    val message: String,
)

/**
 * NIP-MP envelope validation — a port of `validate_project_envelope` in Buzz's
 * `buzz-relay/src/handlers/ingest.rs` (and the matching Layer-A validator in
 * `buzz-sdk/src/builders.rs`), evaluated in the relay's order so the first failing rule is the
 * one the relay would report. `content` is never inspected and unknown tags are ignored.
 * Deliberately absent, as upstream: any membership authorization — referencing another
 * owner's repository is legal and grants nothing.
 */
object ProjectValidator {
    /** Maximum member `a` tags, counted over raw tags (duplicates included). */
    const val MEMBER_CAP = 64

    /** The relay's generic `d` bound (`D_TAG_MAX_LEN`); the SDK reports it as `d-empty`. */
    const val D_MAX_BYTES = 1024
    const val NAME_MAX_BYTES = 256
    const val DESCRIPTION_MAX_BYTES = 2048
    const val METADATA_TAG_MAX_BYTES = 256

    /** Metadata tags a project may carry at most once each, in the relay's check order. */
    private val SINGLETON_METADATA =
        listOf(
            NameTag.TAG_NAME to NAME_MAX_BYTES,
            DescriptionTag.TAG_NAME to DESCRIPTION_MAX_BYTES,
            ChannelTag.TAG_NAME to ChannelTag.MAX_BYTES,
            VisibilityTag.TAG_NAME to VisibilityTag.MAX_BYTES,
        )

    /** Null when [tags] form a valid project envelope; otherwise the first rule that fails. */
    fun validate(tags: TagArray): ProjectRejection? {
        val dValues = mutableListOf<String>()
        val members = mutableListOf<Array<String>>()
        val singletonCounts = IntArray(SINGLETON_METADATA.size)
        val singletonValues = arrayOfNulls<String>(SINGLETON_METADATA.size)

        for (tag in tags) {
            if (tag.isEmpty()) continue
            val value = tag.getOrNull(1) ?: ""
            when (val name = tag[0]) {
                DTag.TAG_NAME -> dValues.add(value)
                ProjectMemberTag.TAG_NAME -> members.add(tag)
                else -> {
                    val index = SINGLETON_METADATA.indexOfFirst { it.first == name }
                    if (index >= 0) {
                        singletonCounts[index]++
                        singletonValues[index] = value
                    }
                }
            }
        }

        if (dValues.size != 1) return reject(ProjectRule.D_CARDINALITY, "project event must have exactly one `d` tag (got ${dValues.size})")
        if (dValues[0].isEmpty()) return reject(ProjectRule.D_EMPTY, "project event `d` tag must not be empty")
        if (dValues[0].utf8Size() > D_MAX_BYTES) return reject(ProjectRule.D_EMPTY, "project event `d` tag exceeds $D_MAX_BYTES bytes")

        if (members.size > MEMBER_CAP) return reject(ProjectRule.MEMBER_CAP, "project event must have at most $MEMBER_CAP member `a` tags (got ${members.size})")
        for (member in members) {
            if (member.size !in 2..3) return reject(ProjectRule.MEMBER_TAG_ARITY, "project event member `a` tag must have exactly 2 or 3 elements (got ${member.size})")
        }
        val seen = HashSet<String>()
        for (member in members) {
            val coordinate = member[1]
            if (!ProjectMemberTag.isValidCoordinate(coordinate)) {
                return reject(ProjectRule.MEMBER_COORDINATE_MALFORMED, "project event member `a` tag must be `30617:<lowercase-64-hex-owner>:<repo-d>` (got \"$coordinate\")")
            }
            if (!seen.add(coordinate)) return reject(ProjectRule.MEMBER_DUPLICATE, "project event has duplicate member coordinate \"$coordinate\"")
        }

        for (i in SINGLETON_METADATA.indices) {
            if (singletonCounts[i] > 1) {
                return reject(ProjectRule.METADATA_CARDINALITY, "project event must have at most one `${SINGLETON_METADATA[i].first}` tag (got ${singletonCounts[i]})")
            }
        }
        for (i in SINGLETON_METADATA.indices) {
            val value = singletonValues[i] ?: continue
            val (name, max) = SINGLETON_METADATA[i]
            if (value.utf8Size() > max) return reject(ProjectRule.METADATA_LENGTH, "project event `$name` tag too long (${value.utf8Size()} bytes, max $max)")
        }
        return null
    }

    private fun reject(
        rule: ProjectRule,
        message: String,
    ) = ProjectRejection(rule, message)

    private fun String.utf8Size() = encodeToByteArray().size
}
