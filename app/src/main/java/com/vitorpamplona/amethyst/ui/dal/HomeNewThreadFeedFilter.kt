package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.service.model.AudioTrackEvent
import com.vitorpamplona.amethyst.service.model.ClassifiedsEvent
import com.vitorpamplona.amethyst.service.model.GenericRepostEvent
import com.vitorpamplona.amethyst.service.model.HighlightEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PeopleListEvent
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

class HomeNewThreadFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultHomeFollowList
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultHomeFollowList == PeopleListEvent.blockList
    }

    override fun feed(): List<Note> {
        val notes = innerApplyFilter(LocalCache.notes.values, true)
        val longFormNotes = innerApplyFilter(LocalCache.addressables.values, false)

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection, false)
    }

    private fun innerApplyFilter(collection: Collection<Note>, ignoreAddressables: Boolean): Set<Note> {
        val isGlobal = account.defaultHomeFollowList == GLOBAL_FOLLOWS
        val gRelays = account.convertGlobalRelays()
        val isHiddenList = showHiddenKey()

        val followingKeySet = account.selectedUsersFollowList(account.defaultHomeFollowList) ?: emptySet()
        val followingTagSet = account.selectedTagsFollowList(account.defaultHomeFollowList) ?: emptySet()
        val followingGeoSet = account.selectedGeohashesFollowList(account.defaultHomeFollowList) ?: emptySet()
        val followingCommunities = account.selectedCommunitiesFollowList(account.defaultHomeFollowList) ?: emptySet()

        val oneMinuteInTheFuture = TimeUtils.now() + (1 * 60) // one minute in the future.
        val oneHr = 60 * 60

        return collection
            .asSequence()
            .filter { it ->
                val noteEvent = it.event
                val isGlobalRelay = it.relays?.any { gRelays.contains(it) } ?: false
                (noteEvent is TextNoteEvent || noteEvent is ClassifiedsEvent || noteEvent is RepostEvent || noteEvent is GenericRepostEvent || noteEvent is LongTextNoteEvent || noteEvent is PollNoteEvent || noteEvent is HighlightEvent || noteEvent is AudioTrackEvent) &&
                    (!ignoreAddressables || noteEvent.kind() < 10000) &&
                    ((isGlobal && isGlobalRelay) || it.author?.pubkeyHex in followingKeySet || noteEvent.isTaggedHashes(followingTagSet) || noteEvent.isTaggedGeoHashes(followingGeoSet) || noteEvent.isTaggedAddressableNotes(followingCommunities)) &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if acceptable
                    (isHiddenList || it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true) &&
                    ((it.event?.createdAt() ?: 0) < oneMinuteInTheFuture) &&
                    it.isNewThread() &&
                    (
                        (noteEvent !is RepostEvent && noteEvent !is GenericRepostEvent) || // not a repost
                            (
                                it.replyTo?.lastOrNull()?.author?.pubkeyHex !in followingKeySet ||
                                    (noteEvent.createdAt() > (it.replyTo?.lastOrNull()?.createdAt() ?: 0) + oneHr)
                                ) // or a repost of by a non-follower's post (likely not seen yet)
                        )
            }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
