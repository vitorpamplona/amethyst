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
package com.vitorpamplona.quartz.buzz.arArtifacts

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.ArtifactOp
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.OpTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.PrevTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.RootTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.TitleTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.TypeTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.VersionTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/**
 * The authoritative NIP-AR envelope of a revision, independent of any client's content
 * schema. Mirrors `ArtifactEnvelope` in Buzz's `buzz-core/src/artifact.rs`.
 */
@Immutable
data class ArtifactEnvelope(
    /** Community-local stable identity — the `d` UUID. */
    val id: String,
    /** Home channel — the `h` UUID. */
    val home: String,
    /** Immutable namespaced type name. */
    val type: String,
    val op: ArtifactOp,
    /** The revision this one replaces; null exactly on `create`. */
    val prev: HexKey?,
    /** Optional conversation anchor. */
    val root: HexKey?,
)

/** The outcome of [ArtifactValidator.validate]. */
sealed interface ArtifactValidation {
    data class Valid(
        val envelope: ArtifactEnvelope,
    ) : ArtifactValidation

    /** [reason] is upstream's rejection message, verbatim. */
    data class Invalid(
        val reason: String,
    ) : ArtifactValidation
}

/**
 * NIP-AR envelope validation — a line-for-line port of `validate` in Buzz's
 * `buzz-core/src/artifact.rs`, which the relay runs on every `kind:45010` before
 * authorization. Payloads (content and client tags) stay opaque; NIP-OA `auth` tag
 * verification is left to the relay, as upstream does.
 */
object ArtifactValidator {
    /** Maximum artifact tags, including the envelope. */
    const val MAX_TAGS = 256

    /** Maximum UTF-8 bytes in a tag name. */
    const val MAX_TAG_NAME_BYTES = 128

    /** Maximum UTF-8 bytes in each tag element. */
    const val MAX_TAG_VALUE_BYTES = 4096

    /** Maximum UTF-8 bytes across all tag elements. */
    const val MAX_TAG_BYTES = 65536

    /** The NIP-OA attestation tag, the only non-envelope tag a delete may carry. */
    const val AUTH_TAG_NAME = "auth"

    /** Envelope tag names: each at most once, with exactly two elements. */
    val ENVELOPE_TAG_NAMES =
        setOf(
            VersionTag.TAG_NAME,
            DTag.TAG_NAME,
            GroupIdTag.TAG_NAME,
            TypeTag.TAG_NAME,
            TitleTag.TAG_NAME,
            OpTag.TAG_NAME,
            RootTag.TAG_NAME,
            PrevTag.TAG_NAME,
        )

    fun validate(
        tags: TagArray,
        content: String,
    ): ArtifactValidation {
        if (tags.size > MAX_TAGS) return invalid("too many tags")

        val fields = HashMap<String, String>()
        var bytes = 0
        for (tag in tags) {
            if (tag.isEmpty()) return invalid("empty tag")
            val name = tag[0]
            if (name.utf8Size() > MAX_TAG_NAME_BYTES) return invalid("tag name too long")
            for (value in tag) {
                val size = value.utf8Size()
                bytes += size
                if (size > MAX_TAG_VALUE_BYTES) return invalid("tag value too long")
            }
            if (name in ENVELOPE_TAG_NAMES && (tag.size != 2 || fields.put(name, tag[1]) != null)) {
                return invalid("envelope tags must occur once with exactly two elements")
            }
        }
        if (bytes > MAX_TAG_BYTES) return invalid("total tag bytes exceeded")

        val version = fields[VersionTag.TAG_NAME] ?: return missing()
        if (version != VersionTag.CURRENT) return invalid("unsupported artifact envelope version")

        val id = fields[DTag.TAG_NAME] ?: return missing()
        if (!ArtifactIds.isCanonicalUuid(id)) return invalidUuid(id)
        val home = fields[GroupIdTag.TAG_NAME] ?: return missing()
        if (!ArtifactIds.isCanonicalUuid(home)) return invalidUuid(home)

        val type = fields[TypeTag.TAG_NAME] ?: return missing()
        if (!TypeTag.isValid(type)) return invalid("invalid namespaced artifact type")

        val opCode = fields[OpTag.TAG_NAME] ?: return missing()
        val op = ArtifactOp.parse(opCode) ?: return invalid("invalid artifact operation")

        val prev = fields[PrevTag.TAG_NAME]
        if (prev != null && !ArtifactIds.isEventId(prev)) return invalid("event ID must be 64 lowercase hex characters")
        if ((op == ArtifactOp.CREATE) != (prev == null)) return invalid("prev required exactly on non-create revisions")

        val root = fields[RootTag.TAG_NAME]
        if (root != null && !ArtifactIds.isEventId(root)) return invalid("event ID must be 64 lowercase hex characters")

        if (op == ArtifactOp.DELETE) {
            if (TitleTag.TAG_NAME in fields || content.isNotEmpty()) {
                return invalid("delete must omit title and have empty content")
            }
            if (tags.fastAny { it[0] !in ENVELOPE_TAG_NAMES && it[0] != AUTH_TAG_NAME }) {
                return invalid("delete allows only envelope and verified auth tags")
            }
        } else {
            val title = fields[TitleTag.TAG_NAME] ?: return missing()
            if (!TitleTag.isValid(title)) return invalid("title must be nonblank and at most 512 UTF-8 bytes")
        }

        return ArtifactValidation.Valid(ArtifactEnvelope(id, home, type, op, prev, root))
    }

    private fun String.utf8Size() = encodeToByteArray().size

    private fun invalid(reason: String) = ArtifactValidation.Invalid(reason)

    private fun missing() = invalid("missing required envelope tag")

    // `canonical_uuid` distinguishes an unparseable value from a non-canonical one.
    private fun invalidUuid(value: String) =
        if (UUID_ANY_FORM.matches(value)) {
            invalid("UUID must be canonical lowercase and non-nil")
        } else {
            invalid("invalid UUID")
        }

    private val UUID_ANY_FORM = Regex("^(urn:uuid:)?\\{?[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}}?$")
}
