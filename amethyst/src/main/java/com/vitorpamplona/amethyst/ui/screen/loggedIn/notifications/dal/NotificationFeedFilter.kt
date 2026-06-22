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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal

import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.filterIntoSet
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.amethyst.ui.dal.sortedByDefaultFeedOrder
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.communityAddress
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotificationFeedFilter(
    val account: Account,
    val modeOverride: TopFilter? = null,
) : AdditiveFeedFilter<Note>() {
    // Pin to modeOverride for split-tab mode; otherwise follow the spinner.
    // Lazy so the eagerly-collected topNavFilter pipeline is only built when
    // the split UI actually opens this filter.
    private val overrideFollowLists: StateFlow<IFeedTopNavFilter>? by lazy {
        modeOverride?.let { account.topNavFilterFlow(MutableStateFlow(it)) }
    }

    companion object {
        val ADDRESSABLE_KINDS =
            listOf(
                AudioTrackEvent.KIND,
                MusicTrackEvent.KIND,
                MusicPlaylistEvent.KIND,
                PodcastEpisodeEvent.KIND,
                PodcastMetadataEvent.KIND,
                CalendarTimeSlotEvent.KIND,
                CalendarDateSlotEvent.KIND,
                CalendarRSVPEvent.KIND,
                ClassifiedsEvent.KIND,
                LiveActivitiesEvent.KIND,
                LiveChessGameAcceptEvent.KIND,
                LiveChessMoveEvent.KIND,
                LongTextNoteEvent.KIND,
                NipTextEvent.KIND,
                VideoVerticalEvent.KIND,
                VideoHorizontalEvent.KIND,
                WikiNoteEvent.KIND,
                AttestationRequestEvent.KIND,
            )

        val NOTIFICATION_KINDS =
            setOf(
                BadgeAwardEvent.KIND,
                ChannelMessageEvent.KIND,
                ChatMessageEvent.KIND,
                ChatMessageEncryptedFileHeaderEvent.KIND,
                CommentEvent.KIND,
                GenericRepostEvent.KIND,
                GitIssueEvent.KIND,
                GitPatchEvent.KIND,
                HighlightEvent.KIND,
                TextNoteEvent.KIND,
                ReactionEvent.KIND,
                RepostEvent.KIND,
                LnZapEvent.KIND,
                NutzapEvent.KIND,
                OnchainZapEvent.KIND,
                LiveActivitiesChatMessageEvent.KIND,
                PictureEvent.KIND,
                PollEvent.KIND,
                ZapPollEvent.KIND,
                PrivateDmEvent.KIND,
                PublicMessageEvent.KIND,
                VideoNormalEvent.KIND,
                VideoShortEvent.KIND,
                VoiceEvent.KIND,
                VoiceReplyEvent.KIND,
            ) + ADDRESSABLE_KINDS

        // How deep to walk a public chat reply chain looking for one of the
        // user's own messages. Bounds the cost on very long threads; the
        // visited-set guards against malformed cyclic replyTo links.
        private const val PUBLIC_CHAT_ANCESTOR_SCAN_LIMIT = 30

        /**
         * Public chats (NIP-28, kind 42) routinely reply to a user without
         * adding a `p` tag, so the normal mention gate ([Event.isTaggedUser])
         * misses them. Treats a channel message as "for me" when one of my own
         * messages appears in its reply chain — a direct reply to my message
         * (the common case: "the previous message was mine") or a later message
         * in a thread I'm already part of (an "active thread"). A kind-42
         * `replyTo` holds only the immediate parent, so the chain is walked
         * hop-by-hop through each cached ancestor.
         *
         * Cache-only (reads [Note.replyTo] + author, never the account), so the
         * push dispatcher and the in-app feed can both relax their p-tag gate
         * with it without loading the account or decrypting anything.
         */
        fun isNotifiablePublicChatReply(
            note: Note,
            authorHex: HexKey,
        ): Boolean {
            if (note.event !is ChannelMessageEvent) return false
            // Top-level channel posts have no parent to reply to — bail before
            // allocating the walk's scratch structures (the common case).
            val parents = note.replyTo
            if (parents.isNullOrEmpty()) return false

            var scanned = 0
            val seen = HashSet<HexKey>()
            val toVisit = ArrayDeque(parents)

            while (toVisit.isNotEmpty() && scanned < PUBLIC_CHAT_ANCESTOR_SCAN_LIMIT) {
                val ancestor = toVisit.removeFirst()
                if (!seen.add(ancestor.idHex)) continue
                scanned++

                if (ancestor.author?.pubkeyHex == authorHex) return true
                ancestor.replyTo?.let { toVisit.addAll(it) }
            }

            return false
        }

        // Shared with EventNotificationConsumer so push notifications and the
        // in-app feed apply the same per-kind "is this event for me" rule.
        fun tagsAnEventByUser(
            note: Note,
            authorHex: HexKey,
        ): Boolean {
            val event = note.event

            // Public chat replies into my messages, even without a p-tag.
            if (isNotifiablePublicChatReply(note, authorHex)) {
                return true
            }

            if (event is GitIssueEvent || event is GitPatchEvent) {
                return true
            }

            if (event is HighlightEvent) {
                return true
            }

            if (event is BaseNoteEvent) {
                if (note.replyTo?.any { it.author?.pubkeyHex == authorHex } == true) {
                    return true
                }

                if (event is CommentEvent) {
                    // NIP-22 comments carry their root/parent authors as tags, so a
                    // reply to the user's reaction stays detectable even when the
                    // reaction event is not in the local cache (the replyTo-author
                    // check above needs it loaded).
                    if (event.rootAuthorKeys().contains(authorHex) || event.replyAuthorKeys().contains(authorHex)) {
                        return true
                    }

                    // Replies to the user's zaps: the receipt — and therefore the
                    // author tags above — is signed by the recipient's lightning
                    // provider, not the zapper. The `k` tag (or the cached parent)
                    // proves the comment targets a zap; the reply's explicit p tag
                    // on the user marks it as theirs.
                    val targetsZapReceipt =
                        event.hasScopeKind(LnZapEvent.KIND.toString()) ||
                            note.replyTo?.any { it.event is LnZapEvent } == true

                    if (targetsZapReceipt && event.isTaggedUser(authorHex)) {
                        return true
                    }
                }

                if ((event is TextNoteEvent || event is CommentEvent)) {
                    val community =
                        event
                            .communityAddress()
                            ?.let {
                                LocalCache.getAddressableNoteIfExists(it)
                            }?.event as? CommunityDefinitionEvent
                    if (community != null) {
                        val moderators = community.moderatorKeys().toSet()
                        val isModerator = moderators.contains(authorHex)

                        if (isModerator && event.pubKey !in moderators) {
                            return true
                        }
                    }
                }

                val isAuthoredPostCited = event.findCitations().any { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == authorHex }

                if (isAuthoredPostCited) return true

                val isAuthorDirectlyCited = event.citedUsers().contains(authorHex)

                if (isAuthorDirectlyCited) return true

                return if (event is IForkableEvent && event.isAFork()) {
                    val address = event.forkFromAddress()
                    val version = event.forkFromVersion()

                    // Displays notifications about forks
                    address?.pubKeyHex == authorHex ||
                        (version?.let { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == authorHex } == true)
                } else {
                    false
                }
            }

            if (event is ReactionEvent) {
                // A reaction carries the reacted-to event's author in its own
                // NIP-25 `p` tag, so a reaction to my note is detectable from the
                // wrapper alone — before the reacted note loads into the cache. On
                // a cold start that note is often missing, leaving `replyTo` an
                // authorless shell; relying solely on the resolved parent author
                // then drops the reaction until the parent happens to arrive,
                // which is what makes the missing set differ every launch. The
                // resolved-parent check is kept as the fallback once it is present.
                return event.originalAuthor().contains(authorHex) ||
                    note.replyTo
                        ?.lastOrNull()
                        ?.author
                        ?.pubkeyHex == authorHex
            }

            if (event is RepostEvent) {
                // Same reasoning as reactions: NIP-18 reposts name the reposted
                // author in their `p` tag, so a repost of my note is relevant even
                // before the reposted note loads.
                return event.originalAuthorKeys().contains(authorHex) ||
                    note.replyTo
                        ?.lastOrNull()
                        ?.author
                        ?.pubkeyHex == authorHex
            }

            if (event is GenericRepostEvent) {
                return event.originalAuthorKeys().contains(authorHex) ||
                    note.replyTo
                        ?.lastOrNull()
                        ?.author
                        ?.pubkeyHex == authorHex
            }

            return true
        }
    }

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code + "-" + account.settings.showMessagesInNotifications.value

    fun followList(): TopFilter = modeOverride ?: account.settings.defaultNotificationFollowList.value

    fun TopFilter.isMuteList() = this is TopFilter.MuteList

    fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = overrideFollowLists?.value ?: account.liveNotificationFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    override fun feed(): List<Note> {
        val filterParams = buildFilterParams(account)

        val notifications =
            LocalCache.notes.filterIntoSet { _, note ->
                note.event !is AddressableEvent && acceptableEvent(note, filterParams)
            } +
                LocalCache.addressables.filterIntoSet(ADDRESSABLE_KINDS) { _, note ->
                    acceptableEvent(note, filterParams)
                }

        // Include marmot group messages as notifications (like DMs)
        val loggedInUserHex = account.userProfile().pubkeyHex
        val marmotMessages =
            account.marmotGroupList.rooms.mapFlatten { _, chatroom ->
                chatroom.messages.filter { it.author?.pubkeyHex != loggedInUserHex }
            }

        return sort(notifications + marmotMessages)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) { acceptableEvent(it, filterParams) }
    }

    fun acceptableEvent(
        it: Note,
        filterParams: FilterByListParams,
    ): Boolean {
        val loggedInUserHex = account.userProfile().pubkeyHex

        // When the user opts out of seeing Messages on the Notification tab, drop
        // direct/group message events (DMs and Marmot group chats) entirely.
        val showMessages = account.settings.showMessagesInNotifications.value

        // Marmot group messages are only acceptable if the gathering chatroom is
        // actually in the current account's group list. Notes are stored in the
        // global LocalCache and accumulate a gatherer reference from every
        // account's chatroom that has ever touched them, so seeing any
        // MarmotGroupChatroom is not enough — it could belong to a prior account
        // or a group the user has since left. Verify the chatroom is the same
        // instance held by this account's list.
        val marmotGatherers = it.inGatherers?.filterIsInstance<MarmotGroupChatroom>()
        if (!marmotGatherers.isNullOrEmpty()) {
            if (!showMessages) return false
            val inCurrentAccount =
                marmotGatherers.any { room ->
                    account.marmotGroupList.rooms.get(room.nostrGroupId) === room
                }
            if (!inCurrentAccount) return false
            return it.author?.pubkeyHex != loggedInUserHex
        }

        val noteEvent = it.event

        if (!showMessages &&
            (
                noteEvent is ChatMessageEvent ||
                    noteEvent is ChatMessageEncryptedFileHeaderEvent ||
                    noteEvent is PrivateDmEvent
            )
        ) {
            return false
        }
        val notifAuthor =
            if (noteEvent is LnZapEvent) {
                val zapRequest = noteEvent.zapRequest
                if (zapRequest != null) {
                    if (noteEvent.zapRequest?.isPrivateZap() == true) {
                        account.privateZapsDecryptionCache.cachedPrivateZap(zapRequest)?.pubKey ?: zapRequest.pubKey
                    } else {
                        zapRequest.pubKey
                    }
                } else {
                    noteEvent.pubKey
                }
            } else {
                if (it is AddressableNote) {
                    it.address.pubKeyHex
                } else {
                    it.author?.pubkeyHex
                }
            }

        // Reactions/zaps/reposts target a note via `replyTo`, not via thread-root tags,
        // so isNotInMutedThread on the wrapper event misses them.
        if (noteEvent is ReactionEvent || noteEvent is LnZapEvent ||
            noteEvent is RepostEvent || noteEvent is GenericRepostEvent
        ) {
            val target = it.replyTo?.lastOrNull()
            if (target != null && account.isThreadMuted(account.resolveThreadRoot(target))) {
                return false
            }
        }

        // Chess events bypass the follow filter — opponents may not be followed
        val isChessEvent = noteEvent is LiveChessGameAcceptEvent || noteEvent is LiveChessMoveEvent

        // Global keeps every event that p-tags the user; Selected (and the
        // follow/list modes) also applies the per-kind relevance heuristics.
        val isRawGlobal = followList() is TopFilter.Global

        // The p-tag gate is OR'd with isNotifiablePublicChatReply so channel
        // replies into my messages still notify without a p-tag. Kept inline
        // (not a pre-computed val) so the cheap kind check short-circuits ahead
        // of the tag scan + reply walk, which is also why the cheaper tag scan
        // is ordered first within the OR. In Global mode this is the only
        // relevance check (tagsAnEventByUser is skipped below); it still scopes
        // to genuine replies, so unrelated channel chatter never leaks through.
        return noteEvent?.kind in NOTIFICATION_KINDS &&
            (noteEvent is LnZapEvent || notifAuthor != loggedInUserHex) &&
            (isChessEvent || filterParams.isGlobal() || notifAuthor == null || filterParams.isAuthorInFollows(notifAuthor)) &&
            (noteEvent?.isTaggedUser(loggedInUserHex) == true || isNotifiablePublicChatReply(it, loggedInUserHex)) &&
            (filterParams.isHiddenList || notifAuthor == null || !account.isHidden(notifAuthor)) &&
            (noteEvent !is PrivateDmEvent || !account.isDecryptedContentHidden(noteEvent)) &&
            (isRawGlobal || tagsAnEventByUser(it, loggedInUserHex))
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
