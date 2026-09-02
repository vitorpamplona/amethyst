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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.tagValueContains
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.ClientTag
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CancellationException

/**
 * Prefix/content search over the [LocalCache] stores: users, notes, and the
 * public-chat / ephemeral / live-activity channel maps. Pure read-side policy —
 * no state beyond the cache reference — so ranking and filtering rules can be
 * tested against a populated cache.
 */
class CacheSearch(
    private val cache: LocalCache,
) {
    fun findUsersStartingWith(
        username: String,
        forAccount: Account?,
    ): List<User> {
        if (username.isBlank()) return emptyList()

        checkNotInMainThread()

        val key = decodePublicKeyAsHexOrNull(username)

        if (key != null) {
            val user = cache.getUserIfExists(key)
            if (user != null) {
                return listOfNotNull(user)
            }
        }

        val dualCase =
            listOf(
                DualCase(username.lowercase(), username.uppercase()),
            )

        val finds =
            cache.users.filter { _, user: User ->
                val metadata = user.metadataOrNull()
                if (metadata == null) {
                    user.pubkeyHex.startsWith(username, true) ||
                        user.pubkeyNpub().startsWith(username, true)
                } else {
                    (
                        metadata.anyNameOrAddressContains(dualCase) ||
                            user.pubkeyHex.startsWith(username, true) ||
                            user.pubkeyNpub().startsWith(username, true)
                    ) &&
                        (forAccount == null || (!forAccount.isHidden(user) && !metadata.anyPropertyContains(forAccount.hiddenUsers.flow.value.hiddenWordsCase)))
                }
            }

        val findsFollowing = finds.associateWith { forAccount?.isFollowing(it) == true }
        val anyNameStartsWith = finds.associateWith { it.metadataOrNull()?.anyNameStartsWith(dualCase) == true }
        val anyAddressStartsWith = finds.associateWith { it.metadataOrNull()?.anyAddressStartsWith(dualCase) == true }
        val displayNames = finds.associateWith { it.toBestDisplayName().lowercase() }

        return finds.sortedWith(
            compareBy(
                { findsFollowing[it] == false },
                { anyNameStartsWith[it] == false },
                { anyAddressStartsWith[it] == false },
                { displayNames[it] },
                { it.pubkeyHex },
            ),
        )
    }

    /**
     * Will return true if supplied note is one of events to be excluded from
     * search results.
     */
    private fun excludeNoteEventFromSearchResults(note: Note): Boolean =
        (
            note.event is GenericRepostEvent ||
                note.event is RepostEvent ||
                note.event is CommunityPostApprovalEvent ||
                note.event is ReactionEvent ||
                note.event is LnZapEvent ||
                note.event is LnZapRequestEvent ||
                note.event is FileHeaderEvent ||
                note.event is MetadataEvent ||
                note.event is ContactListEvent ||
                note.event is AppSpecificDataEvent
        )

    /**
     * Tag names whose values should not match text searches: the `client` tag
     * names the app that published the event (searching for "Amethyst" would
     * otherwise return every event posted through Amethyst), and `p`/`e`/`a`/`alt`
     * values are ids or descriptions of other events, not content of this one.
     */
    private val excludedTagNamesFromSearch =
        setOf(
            ClientTag.TAG_NAME,
            PTag.TAG_NAME,
            ETag.TAG_NAME,
            ATag.TAG_NAME,
            AltTag.TAG_NAME,
        )

    fun findNotesStartingWith(
        text: String,
        hiddenUsers: HiddenUsersState,
    ): List<Note> {
        checkNotInMainThread()

        if (text.isBlank()) return emptyList()

        val key = decodeEventIdAsHexOrNull(text)

        if (key != null) {
            val note = cache.getNoteIfExists(key)
            val noteEvent = note?.event
            val newNote =
                if (noteEvent is AddressableEvent) {
                    val addressableNote = cache.getAddressableNoteIfExists(noteEvent.address())
                    if (addressableNote?.event?.id == note.idHex) {
                        addressableNote
                    } else {
                        note
                    }
                } else {
                    note
                }

            if ((newNote != null) && !excludeNoteEventFromSearchResults(newNote)) {
                return listOfNotNull(newNote)
            }
        }

        return cache.notes.filter { _, note ->
            if (note.event is AddressableEvent) {
                return@filter false
            }

            if (excludeNoteEventFromSearchResults(note)) {
                return@filter false
            }

            if (note.event?.tags?.tagValueContains(text, true, excludedTagNamesFromSearch) == true ||
                note.idHex.startsWith(text, true)
            ) {
                return@filter !note.isHiddenFor(hiddenUsers.flow.value)
            }

            if (note.event?.isContentEncoded() == false) {
                return@filter if (!note.isHiddenFor(hiddenUsers.flow.value)) {
                    note.event?.content?.contains(text, true) ?: false
                } else {
                    false
                }
            }

            return@filter false
        } +
            cache.addressables.filter { _, addressable ->
                if (excludeNoteEventFromSearchResults(addressable)) {
                    return@filter false
                }

                if (addressable.event?.tags?.tagValueContains(text, true, excludedTagNamesFromSearch) == true ||
                    addressable.idHex.startsWith(text, true)
                ) {
                    return@filter !addressable.isHiddenFor(hiddenUsers.flow.value)
                }

                if (addressable.event?.isContentEncoded() == false) {
                    return@filter if (!addressable.isHiddenFor(hiddenUsers.flow.value)) {
                        addressable.event?.content?.contains(text, true) ?: false
                    } else {
                        false
                    }
                }

                return@filter false
            }
    }

    fun findPublicChatChannelsStartingWith(text: String): List<PublicChatChannel> {
        if (text.isBlank()) return emptyList()

        val key = decodeEventIdAsHexOrNull(text)
        if (key != null) {
            cache.getPublicChatChannelIfExists(key)?.let {
                return listOf(it)
            }
        }

        return cache.publicChatChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun findEphemeralChatChannelsStartingWith(text: String): List<EphemeralChatChannel> {
        if (text.isBlank()) return emptyList()

        return cache.ephemeralChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun findLiveActivityChannelsStartingWith(text: String): List<LiveActivitiesChannel> {
        if (text.isBlank()) return emptyList()

        try {
            val parsed = Nip19Parser.uriToRoute(text)?.entity
            if (parsed is NAddress && parsed.kind == LiveActivitiesEvent.KIND) {
                return listOf(cache.getOrCreateLiveChannel(parsed.address()))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        return cache.liveChatChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }
}
