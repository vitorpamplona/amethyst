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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayLoadingCursors
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupAdminTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.RoleTag
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A NIP-29 relay-based group ("channel" in Discord terms). Unlike a NIP-C7
 * [com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel] — a
 * bare relay+room with no server-owned metadata — a relay group has a
 * relay-signed metadata event (kind 39000) carrying its name, picture, about and
 * status flags, so this channel is metadata-backed like a NIP-28
 * [com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel].
 *
 * It is keyed by [GroupId] (host relay + group id): the group only exists on its
 * host relay, so every read and write is pinned there via [relays].
 */
@Stable
open class RelayGroupChannel(
    val groupId: GroupId,
) : Channel() {
    /**
     * Per-relay backward-pagination cursors for this group's chat history. The live tail
     * (RelayGroupOpenChatTailSubAssembler) holds the recent window; this pages older kind-9/poll
     * messages by `until`+`limit` on the host relay, the NIP-29 analog of the per-conversation
     * NIP-04 / Concord history. Held here so the cursors share the channel's cache lifetime.
     */
    val history = RelayLoadingCursors()

    /**
     * Per-relay backward-pagination cursors for this group's **Threads** tab (kind-11 roots + kind-1111
     * comments), kept apart from [history] so paging the forum doesn't move the chat's cursor. The Threads
     * live tail holds the recent window; this pages older thread content by `until`+`limit` on the host relay
     * so a group with more threads than the relay's default result cap doesn't silently hide the older ones.
     */
    val threadsHistory = RelayLoadingCursors()

    /** The latest relay-signed kind 39000 metadata event, when known. */
    var event: GroupMetadataEvent? = null

    /** Long-term reference to the metadata note (LocalCache holds weak references). */
    var metadataNote: Note? = null

    var updatedMetadataAt: Long = 0
        private set

    /** Relay-signed member pubkeys (kind 39002). */
    var members: Set<HexKey> = emptySet()
        private set
    private var membersUpdatedAt: Long = 0

    /** Relay-signed admins with their roles (kind 39001). */
    var admins: List<GroupAdminTag> = emptyList()
        private set
    private var adminsUpdatedAt: Long = 0

    /**
     * Relay-signed pinned message ids (kind 39005), in the relay's display order.
     * Pinning replaces the whole list, so this is always the full current set.
     */
    var pinnedEventIds: List<HexKey> = emptyList()
        private set
    private var pinnedUpdatedAt: Long = 0

    /**
     * Relay-declared roles this group supports (kind 39003), e.g. `admin`, `moderator`,
     * `ceo`. Empty until (or unless) the relay publishes a 39003 for the group. These are
     * the role names a moderation UI should offer when assigning a role, since the exact
     * set is relay-defined (NIP-29 §Group management).
     */
    var supportedRoles: List<RoleTag> = emptyList()
        private set
    private var supportedRolesUpdatedAt: Long = 0

    /**
     * Members ∪ admins, recomputed only when a roster event lands. [memberCount] and the discovery
     * feed read this per note / per recomposition, so caching it avoids rebuilding the set each read.
     */
    private var allMembers: Set<HexKey> = emptySet()

    private fun recomputeAllMembers() {
        allMembers = if (admins.isEmpty()) members else members + admins.mapTo(HashSet()) { it.pubKey }
    }

    /**
     * Kind-11 threads (forum-style posts) scoped to this group, kept apart from the
     * kind-9 chat [Channel.notes] so the chat feed stays clean. Surfaced through the
     * group's "Threads" view; each thread's own comment tree (kind 1111) is loaded
     * on demand by the thread-detail screen.
     */
    private val threadNotes = LargeCache<HexKey, Note>()
    private val _threads = MutableStateFlow<List<Note>>(emptyList())
    val threads: StateFlow<List<Note>> = _threads

    private fun republishThreads() {
        _threads.value = threadNotes.values().sortedByDescending { it.createdAt() ?: 0L }
    }

    /** Add a kind-11 thread note; newest-first snapshot is re-published to [threads]. */
    fun addThread(note: Note) {
        if (threadNotes.containsKey(note.idHex)) return
        threadNotes.put(note.idHex, note)
        republishThreads()
    }

    fun removeThread(note: Note) {
        if (!threadNotes.containsKey(note.idHex)) return
        threadNotes.remove(note.idHex)
        republishThreads()
    }

    fun threadCount(): Int = threadNotes.size()

    /**
     * Whether this channel has received any relay-signed state — metadata, roster, roles or pins. Only the
     * group's **host** relay signs those (via the `isRelaySignedGroupEvent`-gated consume paths), so this is
     * true only for a confirmed host channel and never for a "phantom" one minted from a stray content event
     * that arrived from a non-host relay. Used to redirect such strays back to the real host (see
     * `LocalCache.attachToRelayGroupIfScoped` / the serving-relay hazard).
     */
    fun hasRelaySignedState(): Boolean =
        event != null ||
            members.isNotEmpty() ||
            admins.isNotEmpty() ||
            supportedRoles.isNotEmpty() ||
            pinnedEventIds.isNotEmpty()

    /** A relay group lives on exactly one relay: its host. */
    override fun relays() = setOf(groupId.relayUrl)

    override fun toBestDisplayName(): String = event?.name() ?: groupId.id

    fun summary(): String? = event?.about()

    fun profilePicture(): String? = event?.picture()

    fun isPrivate(): Boolean = event?.isPrivate() ?: false

    fun isRestricted(): Boolean = event?.isRestricted() ?: false

    fun isClosed(): Boolean = event?.isClosed() ?: false

    fun isHidden(): Boolean = event?.isHidden() ?: false

    fun hasLivekit(): Boolean = event?.hasLivekit() ?: false

    /** Subgroups: the id of this group's parent on the same host relay, or null when it's a root. */
    fun parentGroupId(): String? = event?.parent()

    /** Subgroups: the ordered ids of this group's direct children (empty when it has none). */
    fun childGroupIds(): List<String> = event?.children() ?: emptyList()

    /** Whether this group sits under a parent group (i.e. it is a subgroup). */
    fun isSubgroup(): Boolean = event?.isRoot() == false

    fun updateGroupInfo(
        event: GroupMetadataEvent,
        eventNote: Note? = null,
    ) {
        // Only newer metadata supersedes; equal-or-older is dropped, so a duplicate
        // arrival isn't reprocessed (no redundant emit) and first-arrival wins on a
        // createdAt tie. First load passes since real events have createdAt > 0.
        if (event.createdAt <= updatedMetadataAt) return
        this.event = event
        this.metadataNote = eventNote
        this.updatedMetadataAt = event.createdAt
        updateChannelInfo()
    }

    fun updateMembers(event: GroupMembersEvent) {
        if (event.createdAt <= membersUpdatedAt) return
        members = event.members().toSet()
        membersUpdatedAt = event.createdAt
        recomputeAllMembers()
        updateChannelInfo()
    }

    fun updateAdmins(event: GroupAdminsEvent) {
        if (event.createdAt <= adminsUpdatedAt) return
        admins = event.admins()
        adminsUpdatedAt = event.createdAt
        recomputeAllMembers()
        updateChannelInfo()
    }

    fun updatePinned(event: GroupPinnedEvent) {
        // Only newer lists supersede; equal-or-older is dropped so a duplicate
        // arrival isn't reprocessed (no redundant emit).
        if (event.createdAt <= pinnedUpdatedAt) return
        pinnedEventIds = event.pinnedEventIds()
        pinnedUpdatedAt = event.createdAt
        updateChannelInfo()
    }

    fun updateSupportedRoles(event: SupportedRolesEvent) {
        // Only newer definitions supersede; equal-or-older is dropped (no redundant emit).
        if (event.createdAt <= supportedRolesUpdatedAt) return
        supportedRoles = event.roles()
        supportedRolesUpdatedAt = event.createdAt
        updateChannelInfo()
    }

    fun isPinned(eventId: HexKey): Boolean = eventId in pinnedEventIds

    /**
     * NIP-29 timeline references (`previous` tag) for an event about to be sent to this
     * group. Returns the first-8-hex-char id prefixes of the most recent events seen here
     * from the host relay, excluding [selfPubkey]'s own posts.
     *
     * The spec uses these to stop a group's events from being replayed out of context on a
     * forked relay: a relay rejects an event whose `previous` refs it doesn't recognise, so
     * we only draw from events we actually received in this channel (guaranteeing the host
     * relay has them) and cap at the spec's window of the last 50. It recommends including
     * at least 3; [max] bounds how many we attach.
     */
    fun previousEventRefs(
        selfPubkey: HexKey,
        max: Int = 8,
    ): List<String> =
        notes
            .mapNotNull { _, note ->
                val createdAt = note.createdAt()
                val author = note.author
                // Require a resolved author so an unlinked note can't slip past the self-exclusion
                // and make us reference our own event (the very thing `previous` guards against).
                if (createdAt != null && author != null && author.pubkeyHex != selfPubkey) note to createdAt else null
            }.sortedByDescending { it.second }
            .take(max)
            .map { it.first.idHex.take(8) }

    /**
     * The shareable NIP-19 `naddr` coordinate for this group's metadata (kind
     * 39000, authored by the relay's own key, with the host relay as a hint), or
     * null until the relay-signed metadata has loaded (we need its author key).
     */
    fun toNAddr(): String? {
        val relaySelf = event?.pubKey ?: return null
        return NAddress.create(GroupMetadataEvent.KIND, relaySelf, groupId.id, groupId.relayUrl)
    }

    /** Number of known members (admins are members too). */
    fun memberCount(): Int = allMembers.size

    /**
     * The subset of this group's members/admins that [follows] contains — "people you follow who
     * are in here". Reads the cached [allMembers] set, so it's cheap to call per recomposition.
     */
    fun participatingFollows(follows: Set<HexKey>): List<HexKey> {
        if (follows.isEmpty() || allMembers.isEmpty()) return emptyList()
        return allMembers.filter { it in follows }
    }

    /**
     * The relay's view of [pubkey]'s membership, from the signed admin/member
     * lists. Returns [RelayGroupMembership.NONE] when not in either list (or when
     * the lists haven't loaded yet).
     */
    fun membershipOf(pubkey: HexKey): RelayGroupMembership {
        val admin = admins.firstOrNull { it.pubKey == pubkey }
        if (admin != null) {
            return when {
                admin.roles.any { it.equals(RelayGroupMembership.ROLE_ADMIN, true) } -> RelayGroupMembership.ADMIN
                // Presence in the kind-39001 admins list IS the moderation signal;
                // the role labels (moderator, ceo, owner, …) are relay-defined. So
                // anyone in that list who isn't the top-level admin is at least a
                // moderator — never demote them to a plain member just because the
                // role string is unrecognized or absent.
                else -> RelayGroupMembership.MODERATOR
            }
        }
        return if (pubkey in members) RelayGroupMembership.MEMBER else RelayGroupMembership.NONE
    }

    fun anyNameStartsWith(prefix: String): Boolean =
        groupId.id.contains(prefix, true) ||
            event?.name()?.contains(prefix, true) == true ||
            event?.about()?.contains(prefix, true) == true

    // Synthetic note representing this group in list views (the Messages tab) before any message
    // has loaded — so a group the user just joined shows up immediately instead of waiting for its
    // first cached kind-9. Mirrors MarmotGroupChatroom.placeholderNote(): adds this channel as a
    // gatherer so the list row resolves back to it, and is cached with a stable id so equality-based
    // feed diffing treats it as the same row across refreshes.
    private val placeholderLock = KmpLock()
    private var cachedPlaceholder: Note? = null

    fun placeholderNote(): Note =
        placeholderLock.withLock {
            cachedPlaceholder ?: Note(placeholderIdHex(groupId)).apply {
                addGatherer(this@RelayGroupChannel)
                cachedPlaceholder = this
            }
        }

    companion object {
        fun placeholderIdHex(groupId: GroupId): HexKey = "relaygroup-empty-${groupId.toKey()}"
    }
}
