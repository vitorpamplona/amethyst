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
package com.vitorpamplona.amethyst.ui.navigation.routes

import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupScoped
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.HasInnerEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip73ExternalIds.location.isGeohashedScoped
import com.vitorpamplona.quartz.nip73ExternalIds.topics.isHashtagScoped
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent

/**
 * A minichat reply — a kind-1111 [CommentEvent] posted into a chat message's thread — should open
 * that message's minichat ([Route.ChatMinichat]), not the whole channel it lives in. Regular chat
 * messages are kind 9 / 42, so a [CommentEvent] in a *chat context* is always a thread reply. We
 * gate on the chat context (the reply is attached to a Concord / relay-group / public-chat gatherer,
 * or its root message is) precisely so a generic NIP-22 comment on an article or note keeps its own
 * thread route and isn't mistaken for a minichat. Returns null when it isn't a chat-context comment.
 */
fun minichatRouteFor(note: Note): Route? {
    val comment = note.event as? CommentEvent ?: return null
    val rootId = comment.rootEventIds().firstOrNull() ?: return null

    // Prefer the reply's own Concord channel (the reply arrived over that plane, so its gatherer
    // always carries the community/channel), else the root note's if it happens to be loaded. Passing
    // these lets the minichat screen resolve the plane + relays even when the parent isn't cached.
    val concord =
        note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
            ?: LocalCache.getNoteIfExists(rootId)?.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
    if (concord != null) {
        return Route.ChatMinichat(rootId, concord.channelId.communityId, concord.channelId.channelId)
    }

    val inChatContext = note.isInChatGatherer() || LocalCache.getNoteIfExists(rootId)?.isInChatGatherer() == true
    return if (inChatContext) Route.ChatMinichat(rootId) else null
}

private fun Note.isInChatGatherer(): Boolean = inGatherers?.any { it is ConcordChannel || it is RelayGroupChannel || it is PublicChatChannel } == true

fun routeFor(
    note: Note,
    loggedIn: Account,
): Route? {
    // A minichat reply opens the message's thread, not the whole channel it belongs to. Must run
    // before the channel-gatherer shortcuts below, which would otherwise swallow it into the channel.
    minichatRouteFor(note)?.let { return it }

    // Marmot group messages should navigate to the group chat
    val marmotGroup = note.inGatherers?.firstNotNullOfOrNull { it as? MarmotGroupChatroom }
    if (marmotGroup != null) {
        return Route.MarmotGroupChat(marmotGroup.nostrGroupId)
    }

    // NIP-29 relay-group content (kind 9 chat, 11 thread, 1111 comment, polls) should open the
    // group chat screen — like every other chat NIP — not the generic thread view it would fall
    // through to. A consumed group message is attached to its RelayGroupChannel (which carries the
    // host relay), so route via that; fall back to the note's `h` tag + provenance relay otherwise.
    val relayGroup = note.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }
    if (relayGroup != null) {
        return routeFor(relayGroup)
    }

    // Concord channel content (kind 9 chat, 1111 reply, 7 reaction) lands in LocalCache as a real
    // Note attached to its ConcordChannel gatherer. Like the relay-group case above, route to the
    // Concord channel screen instead of the generic thread view it would otherwise fall through to.
    val concordChannel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
    if (concordChannel != null) {
        return routeFor(concordChannel)
    }

    val noteEvent = note.event ?: return Route.EventRedirect(note.idHex)

    if (noteEvent.isGroupScoped()) {
        val groupId = noteEvent.groupId()
        val hostRelay = note.relays.firstOrNull()
        if (groupId != null && hostRelay != null) {
            return routeFor(GroupId(groupId, hostRelay))
        }
    }

    return routeFor(noteEvent, loggedIn)
}

fun routeFor(
    noteEvent: Event,
    loggedIn: Account,
): Route? =
    if (noteEvent is DraftWrapEvent) {
        val innerEvent = loggedIn.draftsDecryptionCache.preCachedDraft(noteEvent)

        if (innerEvent != null) {
            routeForInner(innerEvent, loggedIn)
        } else {
            Route.Note(noteEvent.id)
        }
    } else {
        routeForInner(noteEvent, loggedIn)
    }

fun routeForInner(
    noteEvent: Event,
    loggedIn: Account,
): Route? =
    when (noteEvent) {
        is AppDefinitionEvent -> {
            if (noteEvent.includeKind(5300)) {
                Route.ContentDiscovery(noteEvent.id)
            } else {
                // By address, not version id: the per-id note may have been
                // evicted and relays don't serve replaceables by old ids.
                Route.Note(noteEvent.addressTag())
            }
        }

        is IsInPublicChatChannel -> {
            noteEvent.channelId()?.let {
                Route.PublicChatChannel(it)
            }
        }

        is ChannelCreateEvent -> {
            Route.PublicChatChannel(noteEvent.id)
        }

        is LiveActivitiesEvent -> {
            noteEvent.address().let {
                Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag)
            }
        }

        is LiveActivitiesChatMessageEvent -> {
            noteEvent.activityAddress()?.let {
                Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag)
            }
        }

        is EphemeralChatEvent -> {
            noteEvent.roomId()?.let {
                Route.EphemeralChat(it.id, it.relayUrl.url)
            }
        }

        is FollowListEvent -> {
            Route.FollowPack(noteEvent.address())
        }

        is ChatroomKeyable -> {
            val room = noteEvent.chatroomKey(loggedIn.userProfile().pubkeyHex)
            loggedIn.chatroomList.getOrCreatePrivateChatroom(room)
            Route.Room(room)
        }

        is CommunityDefinitionEvent -> {
            Route.Community(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        is GitRepositoryEvent -> {
            Route.GitRepository(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        is SoftwareApplicationEvent -> {
            Route.SoftwareAppDetail(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        // Calendar appointments route to their dedicated detail screen rather than the generic
        // Route.Note that AddressableEvent would fall through to — without this the notification
        // tap and `nostr:naddr…` deep links land on the bare note view instead of the calendar
        // detail with RSVPs, participants, and the "in calendars" list.
        is com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent -> {
            Route.CalendarEventDetail(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        is com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent -> {
            Route.CalendarEventDetail(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        is GiftWrapEvent, is SealedRumorEvent -> {
            val wrap = noteEvent as HasInnerEvent
            wrap.innerEventId?.let {
                routeFor(LocalCache.getOrCreateNote(it), loggedIn)
            }
        }

        is AddressableEvent -> {
            Route.Note(noteEvent.addressTag())
        }

        else -> {
            Route.Note(noteEvent.id)
        }
    }

fun routeToMessage(
    user: HexKey,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: AccountViewModel,
): Route =
    routeToMessage(
        setOf(user),
        draftMessage,
        replyId,
        draftId,
        expiresDays,
        accountViewModel,
    )

fun routeToMessage(
    users: Set<HexKey>,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: AccountViewModel,
) = routeToMessage(
    ChatroomKey(users),
    draftMessage,
    replyId,
    draftId,
    expiresDays,
    accountViewModel,
)

fun routeToMessage(
    room: ChatroomKey,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: AccountViewModel,
): Route = routeToMessage(room, draftMessage, replyId, draftId, expiresDays, accountViewModel.account)

fun routeToMessage(
    room: ChatroomKey,
    draftMessage: String? = null,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    account: Account,
): Route {
    account.chatroomList.getOrCreatePrivateChatroom(room)

    return Route.Room(room, message = draftMessage, replyId = replyId, draftId = draftId, expiresDays = expiresDays)
}

fun routeToMessage(
    user: User,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: AccountViewModel,
): Route = routeToMessage(user.pubkeyHex, draftMessage, replyId, draftId, expiresDays, accountViewModel)

fun routeFor(note: EphemeralChatChannel): Route = Route.EphemeralChat(note.roomId.id, note.roomId.relayUrl.url)

fun routeFor(note: PublicChatChannel): Route = Route.PublicChatChannel(note.idHex)

fun routeFor(note: LiveActivitiesChannel): Route = Route.LiveActivityChannel(note.address.kind, note.address.pubKeyHex, note.address.dTag)

fun routeFor(roomId: RoomId): Route = Route.EphemeralChat(roomId.id, roomId.relayUrl.url)

fun routeFor(note: RelayGroupChannel): Route = Route.RelayGroup(note.groupId.id, note.groupId.relayUrl.url)

fun routeFor(groupId: GroupId): Route = Route.RelayGroup(groupId.id, groupId.relayUrl.url)

fun routeFor(channel: ConcordChannel): Route = Route.Concord(channel.channelId.communityId, channel.channelId.channelId)

fun routeFor(user: User): Route.Profile = Route.Profile(user.pubkeyHex)

fun routeForUser(userHex: HexKey): Route.Profile = Route.Profile(userHex)

fun authorRouteFor(note: Note): Route.Profile? = note.author?.pubkeyHex?.let { Route.Profile(it) }

fun routeReplyTo(
    note: Note,
    account: Account,
): Route? {
    // Marmot group messages must reply inside the encrypted group, not as a
    // public kind:1111 comment. The inner kind:9 event has no group hint of
    // its own — we detect the group via the gathering MarmotGroupChatroom,
    // mirroring routeFor() above.
    val marmotGroup = note.inGatherers?.firstNotNullOfOrNull { it as? MarmotGroupChatroom }
    if (marmotGroup != null) {
        return Route.MarmotGroupChat(marmotGroup.nostrGroupId, replyId = note.idHex)
    }

    // Concord messages are end-to-end encrypted: a reply must go through the channel plane (a sealed
    // kind-1111), never a public kind-1111 — which would leak the private rumor id onto public relays
    // and wouldn't bind to the channel. Route the reply into the message's minichat, whose composer
    // sends the wrapped reply. For a thread reply (kind-1111) reply into the same flat thread (its
    // root); for a top-level message (kind-9) the message itself is the thread root.
    val concord = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
    if (concord != null) {
        val rootId = (note.event as? CommentEvent)?.rootEventIds()?.firstOrNull() ?: note.idHex
        return Route.ChatMinichat(rootId, concord.channelId.communityId, concord.channelId.channelId)
    }

    val noteEvent = note.event
    return when (noteEvent) {
        is ChannelMessageEvent -> {
            noteEvent.channelId()?.let { channelId ->
                Route.PublicChatChannel(channelId, replyTo = note.idHex)
            }
        }

        is LiveActivitiesChatMessageEvent -> {
            noteEvent.activityAddress()?.let {
                Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag, replyTo = note.idHex)
            }
        }

        is EphemeralChatEvent -> {
            noteEvent.roomId()?.let {
                Route.EphemeralChat(it.id, it.relayUrl.url, replyTo = note.idHex)
            }
        }

        is PublicMessageEvent -> {
            Route.NewPublicMessage(
                users = noteEvent.groupKeySet() - account.userProfile().pubkeyHex,
                parentId = noteEvent.id,
            )
        }

        is TextNoteEvent -> {
            Route.NewShortNote(baseReplyTo = note.idHex)
        }

        is ChatroomKeyable -> {
            // Covers PrivateDmEvent (NIP-04) and BaseDMGroupEvent (NIP-17) — both
            // implement ChatroomKeyable with identical routing semantics here.
            routeToMessage(
                room = noteEvent.chatroomKey(account.userProfile().pubkeyHex),
                draftMessage = null,
                replyId = noteEvent.id,
                draftId = null,
                account = account,
            )
        }

        is CommentEvent -> {
            if (noteEvent.isGeohashedScoped()) {
                Route.GeoPost(replyTo = note.idHex)
            } else if (noteEvent.isHashtagScoped()) {
                Route.HashtagPost(replyTo = note.idHex)
            } else {
                Route.GenericCommentPost(replyTo = note.idHex)
            }
        }

        is LnZapEvent -> {
            // A public reply can't tag a private zapper without exposing them.
            // When we hold the decrypted sender (we are the zap recipient), reply
            // in their DM room instead of the public comment composer.
            val request = noteEvent.zapRequest
            val privateSender =
                if (request?.isPrivateZap() == true) {
                    account.privateZapsDecryptionCache.cachedPrivateZap(request)?.pubKey
                } else {
                    null
                }

            if (privateSender != null) {
                routeToMessage(ChatroomKey(setOf(privateSender)), null, account = account)
            } else {
                Route.GenericCommentPost(replyTo = note.idHex)
            }
        }

        else -> {
            Route.GenericCommentPost(replyTo = note.idHex)
        }
    }
}

suspend fun routeEditDraftTo(
    note: Note,
    account: Account,
): Route? {
    val noteEvent = note.event as DraftWrapEvent
    val draft = account.draftsDecryptionCache.cachedDraft(noteEvent)

    return when (draft) {
        is ChannelMessageEvent -> {
            draft.channelId()?.let { Route.PublicChatChannel(it, draftId = note.idHex) }
        }

        is LiveActivitiesChatMessageEvent -> {
            draft.activityAddress()?.let { Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag, draftId = note.idHex) }
        }

        is EphemeralChatEvent -> {
            draft.roomId()?.let { Route.EphemeralChat(it.id, it.relayUrl.url, draftId = note.idHex) }
        }

        is ChatroomKeyable -> {
            val room = draft.chatroomKey(account.userProfile().pubkeyHex)
            account.chatroomList.getOrCreatePrivateChatroom(room)
            Route.Room(room, draftId = note.idHex)
        }

        is TextNoteEvent -> {
            Route.NewShortNote(draft = note.idHex)
        }

        is LongTextNoteEvent -> {
            Route.NewLongFormPost(draft = note.idHex)
        }

        is ClassifiedsEvent -> {
            Route.NewProduct(draft = note.idHex)
        }

        is PublicMessageEvent -> {
            Route.NewPublicMessage(
                users = draft.groupKeySet() - account.userProfile().pubkeyHex,
                parentId = noteEvent.id,
                draftId = note.idHex,
            )
        }

        is PollEvent -> {
            Route.NewPoll(draft = note.idHex)
        }

        is ZapPollEvent -> {
            Route.NewPoll(draft = note.idHex)
        }

        is CommentEvent -> {
            if (draft.isGeohashedScoped()) {
                Route.GeoPost(draft = note.idHex)
            } else if (draft.isHashtagScoped()) {
                Route.HashtagPost(draft = note.idHex)
            } else {
                Route.GenericCommentPost(draft = note.idHex)
            }
        }

        else -> {
            null
        }
    }
}
