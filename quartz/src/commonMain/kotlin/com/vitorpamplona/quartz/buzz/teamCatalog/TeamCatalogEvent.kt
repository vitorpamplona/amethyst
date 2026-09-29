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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.buzz.apPersonas.tags.SharedTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import kotlinx.coroutines.CancellationException

/**
 * A Buzz team catalog entry (`kind:30178`): the shareable projection of a team — its name,
 * description, instructions and every member's safe definition embedded in full
 * ([TeamCatalogContent]) — so another community member can adopt the team without reading
 * the owner's personas. Addressable by `(owner, 30178, d)` where `d` is the team's stable id,
 * the same `d` as its `kind:30176` [com.vitorpamplona.quartz.buzz.teams.TeamEvent]. The two
 * kinds are separate so an ordinary team edit (30176) cannot disturb the catalog's share
 * state, which lives only on this head's [SharedTag].
 *
 * Read access is **author-only unless shared**: the relay serves the head to others only when
 * it carries exactly `["shared","true"]`; an untagged head is the durable "published but not
 * discoverable" state unsharing produces. A reader keys by `(pubkey, d)`, takes the newest
 * head, and only then checks sharing and parses — a newer unshared or malformed head must not
 * resurrect an older shared one.
 *
 * Tags: exactly one `d` (non-empty, at most 64 characters, no control characters or
 * whitespace — `single_bounded_d_tag`) and at most one exact `["shared","true"]`
 * (`validate_shared_tag`). Ground truth: `build_team_catalog_event` /
 * `team_catalog_content_from_event` in Buzz's
 * `desktop/src-tauri/src/managed_agents/team_catalog.rs`, the reader in
 * `desktop/src-tauri/src/team_catalog.rs`, and `validate_team_catalog_envelope` in
 * `buzz-relay/src/handlers/ingest.rs`.
 */
@Immutable
class TeamCatalogEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The team's stable id — the `d` tag (shared with its `kind:30176`). */
    fun teamId() = dTag()

    /** True when the entry is discoverable by the community (`["shared","true"]`). */
    fun isShared() = SharedTag.isShared(tags)

    /** True when the tags pass the relay's ingest envelope check. */
    fun isEnvelopeValid() = envelopeError(tags) == null

    /**
     * Parses the body, all-or-nothing like upstream: throws when the JSON is malformed, the
     * schema version is not 1, or any field breaks the v1 contract. Use [catalogOrNull].
     */
    fun catalog(): TeamCatalogContent {
        val parsed = TeamCatalogContent.decodeFromJson(content)
        parsed.validate()?.let { throw IllegalArgumentException(it) }
        return parsed
    }

    fun catalogOrNull(): TeamCatalogContent? =
        try {
            catalog()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 30178

        /** Maximum characters in the `d` tag (`single_bounded_d_tag`). */
        const val MAX_D_CHARS = 64

        /**
         * The relay's ingest check for this kind, or null when it passes: exactly one `d`
         * (a valueless `["d"]` counts), non-empty, at most [MAX_D_CHARS] characters, with no
         * control or whitespace characters; and no `shared` tag other than a single exact
         * `["shared","true"]`.
         */
        fun envelopeError(tags: TagArray): String? {
            var sharedCount = 0
            var dCount = 0
            var d: String? = null
            for (tag in tags) {
                if (tag.isEmpty()) continue
                when (tag[0]) {
                    SharedTag.TAG_NAME -> sharedCount++
                    DTag.TAG_NAME -> {
                        dCount++
                        d = tag.getOrNull(1) ?: ""
                    }
                }
            }
            if (sharedCount > 0 && !SharedTag.isShared(tags)) return "team-catalog event `shared` tag must be exactly one [\"shared\",\"true\"]"
            if (dCount != 1) return "team-catalog event must have exactly one `d` tag (got $dCount)"
            return teamIdError(d ?: "")
        }

        /** Why [teamId] cannot be a catalog `d` value, or null when it can. */
        fun teamIdError(teamId: String): String? {
            if (teamId.isEmpty()) return "team-catalog event `d` tag must not be empty"
            var chars = 0
            for (c in teamId) {
                if (!c.isLowSurrogate()) chars++
                if (c.isISOControl() || c.isWhitespace()) return "team-catalog event `d` tag must not contain control characters or whitespace"
            }
            if (chars > MAX_D_CHARS) return "team-catalog event `d` tag too long ($chars chars, max $MAX_D_CHARS)"
            return null
        }

        /**
         * Builds a catalog head the way `build_team_catalog_event` does: `d` = [teamId], plus
         * `["shared","true"]` only when [shared] (unsharing republishes without it). The body
         * must pass [TeamCatalogContent.validate]. Republishing should pass the replaced
         * head's [priorHeadCreatedAt] so the new head sorts after it even when this clock
         * lags (upstream signs with `monotonic_created_at`).
         */
        fun build(
            catalog: TeamCatalogContent,
            teamId: String,
            shared: Boolean,
            priorHeadCreatedAt: Long? = null,
            createdAt: Long = PersonaEvent.monotonicCreatedAt(priorHeadCreatedAt),
            initializer: TagArrayBuilder<TeamCatalogEvent>.() -> Unit = {},
        ): EventTemplate<TeamCatalogEvent> {
            teamIdError(teamId)?.let { throw IllegalArgumentException(it) }
            catalog.validate()?.let { throw IllegalArgumentException(it) }
            return eventTemplate(KIND, catalog.encodeToJson(), createdAt) {
                dTag(teamId)
                if (shared) addUnique(SharedTag.assemble())
                initializer()
            }
        }
    }
}
