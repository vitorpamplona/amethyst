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
package com.vitorpamplona.quartz.marmot.foundation.appEvents

import com.vitorpamplona.quartz.marmot.appComponents.MarmotGroupAvatar
import com.vitorpamplona.quartz.marmot.appComponents.MarmotGroupState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The slice of canonical group state that kind `1210` rows are derived from.
 *
 * Group system rows are **synthesized locally from canonical group state**,
 * never received as messages — that is what makes them trustworthy, since a
 * row derived from an MLS-authenticated commit cannot be forged by one member
 * and every client applying the same commit derives the same row. So the input
 * to that derivation is a snapshot of the state itself, and a row is the
 * difference between two of them.
 *
 * Only the fields the registry's row types actually speak about are kept.
 * Anything else changing is a state change with no row to describe it, and
 * inventing one would put a caption in the timeline that no other
 * implementation writes.
 */
data class MarmotGroupSnapshot(
    /** MLS-authenticated account identities holding at least one member leaf. */
    val members: Set<HexKey>,
    /** Accounts named by the admin policy, whether or not they still hold a leaf. */
    val admins: Set<HexKey>,
    val name: String,
    /** True once the group has an avatar on either carrier. */
    val hasAvatar: Boolean,
    /** The avatar's identity, so a REPLACEMENT is a change and not a no-op. */
    val avatarFingerprint: String,
    val isDisbanded: Boolean,
) {
    /**
     * The snapshot as JSON, for the local store that remembers what the last
     * derived rows were derived FROM.
     *
     * This never crosses the wire and is not a Marmot payload — it is a
     * client's memory of where it left off, so nothing here has to be
     * canonical. Sets are sorted anyway so a re-encode of unchanged state is
     * byte-identical and cannot look like a change.
     */
    fun encode(): String =
        Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "members" to JsonArray(members.sorted().map { JsonPrimitive(it) }),
                    "admins" to JsonArray(admins.sorted().map { JsonPrimitive(it) }),
                    "name" to JsonPrimitive(name),
                    "has_avatar" to JsonPrimitive(hasAvatar),
                    "avatar" to JsonPrimitive(avatarFingerprint),
                    "disbanded" to JsonPrimitive(isDisbanded),
                ),
            ),
        )

    companion object {
        /**
         * Read a snapshot back, or null when the stored text is not one.
         *
         * A null is not a failure to recover from: the caller treats it as
         * "no baseline", which re-establishes one on the next observation. The
         * cost is missing the rows for one transition, not a broken group.
         */
        fun decode(json: String): MarmotGroupSnapshot? =
            try {
                val obj = Json.parseToJsonElement(json).jsonObject
                MarmotGroupSnapshot(
                    members = obj["members"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
                    admins = obj["admins"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                    hasAvatar = obj["has_avatar"]?.jsonPrimitive?.boolean == true,
                    avatarFingerprint = obj["avatar"]?.jsonPrimitive?.content.orEmpty(),
                    isDisbanded = obj["disbanded"]?.jsonPrimitive?.boolean == true,
                )
            } catch (_: Exception) {
                null
            }

        val EMPTY =
            MarmotGroupSnapshot(
                members = emptySet(),
                admins = emptySet(),
                name = "",
                hasAvatar = false,
                avatarFingerprint = "",
                isDisbanded = false,
            )

        /**
         * Take a snapshot of one epoch's state.
         *
         * [memberAccounts] must be the account identities of the CURRENT
         * member leaves in the same epoch as [state] — the admin policy alone
         * cannot say who is in the group, only who is allowed to act if they
         * are.
         *
         * [name] and [admins] default to the current profile's components and
         * are overridable for a reason: a legacy MIP-01 group keeps both in its
         * single `0xF2EE` extension, where [state] cannot see them. Reading
         * them off [state] alone would make every legacy group look permanently
         * nameless and adminless — so no rename would ever produce a row.
         */
        fun of(
            state: MarmotGroupState,
            memberAccounts: Collection<HexKey>,
            name: String = state.profile?.name.orEmpty(),
            admins: Collection<HexKey> = state.adminPolicy?.adminHexKeys.orEmpty(),
        ): MarmotGroupSnapshot {
            val avatar = state.preferredAvatar
            return MarmotGroupSnapshot(
                members = memberAccounts.toSet(),
                admins = admins.toSet(),
                name = name,
                hasAvatar = avatar != null,
                avatarFingerprint = avatarFingerprint(avatar),
                isDisbanded = state.isDisbanded,
            )
        }

        /**
         * A stable identity for whichever avatar the group resolves to.
         *
         * Swapping one avatar for another is a change a member should see, and
         * a plain "is there one" flag would call that a no-op. The URL avatar
         * is identified by its (already normalized) URL and the Blossom one by
         * its content hash; the carrier is part of the fingerprint so moving
         * between them counts even if nothing else did.
         */
        private fun avatarFingerprint(avatar: MarmotGroupAvatar?): String =
            when (avatar) {
                null -> ""
                is MarmotGroupAvatar.Url -> "url:${avatar.avatar.url}"
                is MarmotGroupAvatar.Blossom ->
                    "blossom:" +
                        avatar.image.imageHash
                            ?.toHexKey()
                            .orEmpty()
            }
    }
}

/**
 * Derives kind `1210` rows from the difference between two group snapshots.
 *
 * The whole point is that this is a pure function of canonical state: two
 * clients that applied the same commits hold the same before and after, so they
 * write the same rows in the same order without ever exchanging one. Nothing
 * here reads the wire.
 */
object MarmotSystemRowDiff {
    /**
     * The rows describing the transition from [before] to [after].
     *
     * [actor] is the account that committed the change, when it is known.
     * A row whose actor is unknown is still a true row — the change happened —
     * so it is emitted unattributed rather than dropped.
     *
     * Ordering is deliberate and fixed: departures before arrivals, membership
     * before admin rights, and the group-wide changes last. Two clients that
     * derive rows in different orders would show the same history differently.
     */
    fun diff(
        before: MarmotGroupSnapshot,
        after: MarmotGroupSnapshot,
        actor: HexKey? = null,
    ): List<MarmotSystemEvent> {
        val rows = ArrayList<MarmotSystemEvent>()

        // A member who left under their own SelfRemove proposal is not the
        // same event as one an admin removed, and the registry has both. We
        // can only tell them apart when the actor is the departing account
        // itself — anything else is a removal by someone.
        for (gone in (before.members - after.members).sorted()) {
            val type = if (actor != null && actor == gone) MarmotSystemType.MEMBER_LEFT else MarmotSystemType.MEMBER_REMOVED
            rows.add(MarmotSystemEvent(type, actor = actor, subject = gone))
        }
        for (added in (after.members - before.members).sorted()) {
            rows.add(MarmotSystemEvent(MarmotSystemType.MEMBER_ADDED, actor = actor, subject = added))
        }

        // Admin rows are about the policy, not about presence: an account can
        // gain admin rights in the same commit that adds it, and both rows are
        // true and both are worth showing.
        for (demoted in (before.admins - after.admins).sorted()) {
            rows.add(MarmotSystemEvent(MarmotSystemType.ADMIN_REMOVED, actor = actor, subject = demoted))
        }
        for (promoted in (after.admins - before.admins).sorted()) {
            rows.add(MarmotSystemEvent(MarmotSystemType.ADMIN_ADDED, actor = actor, subject = promoted))
        }

        if (before.name != after.name) {
            rows.add(MarmotSystemEvent(MarmotSystemType.GROUP_RENAMED, actor = actor, name = after.name))
        }
        if (before.avatarFingerprint != after.avatarFingerprint) {
            rows.add(MarmotSystemEvent(MarmotSystemType.GROUP_AVATAR_CHANGED, actor = actor))
        }
        // Disband is absorbing and terminal, so it can only be entered once —
        // and a group that somehow reported leaving it has no row for that.
        if (!before.isDisbanded && after.isDisbanded) {
            rows.add(MarmotSystemEvent(MarmotSystemType.GROUP_DISBANDED, actor = actor))
        }
        return rows
    }
}
