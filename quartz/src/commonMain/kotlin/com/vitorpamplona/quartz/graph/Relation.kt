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
package com.vitorpamplona.quartz.graph

import com.vitorpamplona.quartz.graph.props.ActorProps
import com.vitorpamplona.quartz.graph.props.AuctionProps
import com.vitorpamplona.quartz.graph.props.AuditProps
import com.vitorpamplona.quartz.graph.props.BidProps
import com.vitorpamplona.quartz.graph.props.ChessResultProps
import com.vitorpamplona.quartz.graph.props.CollaborationProps
import com.vitorpamplona.quartz.graph.props.CreditProps
import com.vitorpamplona.quartz.graph.props.FoundProps
import com.vitorpamplona.quartz.graph.props.FrameProps
import com.vitorpamplona.quartz.graph.props.ItemProps
import com.vitorpamplona.quartz.graph.props.LabelProps
import com.vitorpamplona.quartz.graph.props.LinkProps
import com.vitorpamplona.quartz.graph.props.MemberProps
import com.vitorpamplona.quartz.graph.props.ModerationProps
import com.vitorpamplona.quartz.graph.props.MuteProps
import com.vitorpamplona.quartz.graph.props.NoProps
import com.vitorpamplona.quartz.graph.props.OrderProps
import com.vitorpamplona.quartz.graph.props.OwnerProps
import com.vitorpamplona.quartz.graph.props.ParticipantProps
import com.vitorpamplona.quartz.graph.props.PlatformProps
import com.vitorpamplona.quartz.graph.props.PollResponseProps
import com.vitorpamplona.quartz.graph.props.PositionProps
import com.vitorpamplona.quartz.graph.props.RatingProps
import com.vitorpamplona.quartz.graph.props.ReleaseProps
import com.vitorpamplona.quartz.graph.props.ReportProps
import com.vitorpamplona.quartz.graph.props.ResolutionProps
import com.vitorpamplona.quartz.graph.props.RoleProps
import com.vitorpamplona.quartz.graph.props.RsvpProps
import com.vitorpamplona.quartz.graph.props.ServiceProps
import com.vitorpamplona.quartz.graph.props.StatusProps
import com.vitorpamplona.quartz.graph.props.SubjectProps
import com.vitorpamplona.quartz.graph.props.ViewProps
import com.vitorpamplona.quartz.graph.props.VoteProps
import com.vitorpamplona.quartz.graph.props.WotProps
import com.vitorpamplona.quartz.graph.props.ZapProps
import com.vitorpamplona.quartz.graph.props.ZapSplitProps

/**
 * What a link's target IS to the event that states it: its `ROOT`, its `PARENT`, the
 * `ZAP_RECIPIENT`. The vocabulary, its rules and every class's use of it are in
 * `quartz/plans/2026-09-29-graph-link-vocabulary.md` and its appendix.
 *
 * Names are Nostr's own words for the slot (a NIP's marker or noun), else the past participle of
 * the NIP's action (`REACTED`, `DELETED`); lists name their entries as the list does (`FOLLOW`,
 * `BOOKMARK`). One relation per role across kinds: the SOURCE event's kind says which kind of
 * parent a `PARENT` is. UPPER_SNAKE, the Cypher convention for relationship types.
 *
 * [P] is the props type the relation's links carry ([NoProps] for none): the builder only accepts
 * that type for it, and it is the relation's schema for a consumer.
 *
 * The set is open (a consumer may mint its own), but every relation Quartz emits is a constant
 * here, so renaming one is a visible, breaking change.
 */
class Relation<P : LinkProps>(
    val name: String,
) {
    override fun equals(other: Any?) = other is Relation<*> && other.name == name

    override fun hashCode() = name.hashCode()

    override fun toString() = name

    companion object {
        // Authorship and identity
        val AUTHOR = Relation<NoProps>("AUTHOR")
        val ADDRESS = Relation<NoProps>("ADDRESS")

        // Conversation
        val ROOT = Relation<NoProps>("ROOT")
        val PARENT = Relation<NoProps>("PARENT")
        val ROOT_AUTHOR = Relation<NoProps>("ROOT_AUTHOR")
        val PARENT_AUTHOR = Relation<NoProps>("PARENT_AUTHOR")
        val MENTION = Relation<NoProps>("MENTION")
        val QUOTE = Relation<NoProps>("QUOTE")
        val FORK = Relation<NoProps>("FORK")
        val EDITED = Relation<NoProps>("EDITED")
        val EDITED_AUTHOR = Relation<NoProps>("EDITED_AUTHOR")
        val RECIPIENT = Relation<FrameProps>("RECIPIENT")
        val COMMUNITY = Relation<NoProps>("COMMUNITY")
        val REPOSITORY = Relation<NoProps>("REPOSITORY")
        val REPOSITORY_OWNER = Relation<NoProps>("REPOSITORY_OWNER")

        // Reactions, reposts, zaps
        val REACTED = Relation<NoProps>("REACTED")
        val REACTED_AUTHOR = Relation<NoProps>("REACTED_AUTHOR")
        val REPOSTED = Relation<NoProps>("REPOSTED")
        val REPOSTED_AUTHOR = Relation<NoProps>("REPOSTED_AUTHOR")
        val ZAPPED = Relation<ZapProps>("ZAPPED")
        val ZAP_RECIPIENT = Relation<ZapProps>("ZAP_RECIPIENT")
        val ZAP_SENDER = Relation<NoProps>("ZAP_SENDER")
        val ZAP_REQUEST = Relation<NoProps>("ZAP_REQUEST")
        val HIGHLIGHTED = Relation<NoProps>("HIGHLIGHTED")
        val HIGHLIGHTED_AUTHOR = Relation<RoleProps>("HIGHLIGHTED_AUTHOR")
        val RATED = Relation<RatingProps>("RATED")
        val RATED_AUTHOR = Relation<RatingProps>("RATED_AUTHOR")

        // Moderation
        val DELETED = Relation<NoProps>("DELETED")
        val DELETED_AUTHOR = Relation<NoProps>("DELETED_AUTHOR")
        val REPORTED_USER = Relation<ReportProps>("REPORTED_USER")
        val REPORTED = Relation<ReportProps>("REPORTED")
        val REPORTED_AUTHOR = Relation<ReportProps>("REPORTED_AUTHOR")
        val LABELED = Relation<LabelProps>("LABELED")
        val MUTE = Relation<MuteProps>("MUTE")
        val HIDDEN = Relation<NoProps>("HIDDEN")
        val CHANNEL_MUTED = Relation<NoProps>("CHANNEL_MUTED")
        val APPROVED = Relation<NoProps>("APPROVED")
        val APPROVED_AUTHOR = Relation<NoProps>("APPROVED_AUTHOR")
        val MODERATOR = Relation<NoProps>("MODERATOR")

        // Social graph and lists
        val FOLLOW = Relation<NoProps>("FOLLOW")
        val SUBSCRIBED = Relation<NoProps>("SUBSCRIBED")
        val FAVORITE = Relation<NoProps>("FAVORITE")
        val MEMBER = Relation<MemberProps>("MEMBER")
        val RECOMMENDED = Relation<PlatformProps>("RECOMMENDED")
        val BOOKMARK = Relation<NoProps>("BOOKMARK")
        val CURATED = Relation<OrderProps>("CURATED")
        val PIN = Relation<OrderProps>("PIN")

        // Badges (NIP-58)
        val AWARDED = Relation<NoProps>("AWARDED")
        val BADGE_DEFINITION = Relation<NoProps>("BADGE_DEFINITION")
        val BADGE_AWARD = Relation<NoProps>("BADGE_AWARD")
        val BADGE_SET = Relation<NoProps>("BADGE_SET")

        // Trust (NIP-85)
        val SUBJECT = Relation<SubjectProps>("SUBJECT")
        val SERVICE_PROVIDER = Relation<ServiceProps>("SERVICE_PROVIDER")

        // Events, calendars, live activities, markets
        val PARTICIPANT = Relation<ParticipantProps>("PARTICIPANT")
        val CALENDAR_EVENT = Relation<RsvpProps>("CALENDAR_EVENT")
        val CALENDAR_EVENT_AUTHOR = Relation<NoProps>("CALENDAR_EVENT_AUTHOR")
        val CALENDAR = Relation<NoProps>("CALENDAR")
        val RAIDED = Relation<NoProps>("RAIDED")
        val CLIPPED = Relation<NoProps>("CLIPPED")
        val CLIPPED_AUTHOR = Relation<NoProps>("CLIPPED_AUTHOR")
        val POLL = Relation<PollResponseProps>("POLL")
        val POLL_AUTHOR = Relation<NoProps>("POLL_AUTHOR")
        val AUCTION = Relation<AuctionProps>("AUCTION")
        val AUCTION_AUTHOR = Relation<NoProps>("AUCTION_AUTHOR")
        val BID = Relation<BidProps>("BID")
        val BID_AUTHOR = Relation<NoProps>("BID_AUTHOR")
        val TIMESTAMPED = Relation<NoProps>("TIMESTAMPED")
        val REDIRECT = Relation<NoProps>("REDIRECT")

        // Topics and plain tags
        val HASHTAG = Relation<NoProps>("HASHTAG")
        val TAG = Relation<NoProps>("TAG")

        // Every kind: tags any event may carry
        val CLIENT = Relation<NoProps>("CLIENT")
        val ZAP_SPLIT = Relation<ZapSplitProps>("ZAP_SPLIT")
        val EMOJI_SET = Relation<NoProps>("EMOJI_SET")

        // Added by the per-class review (see the appendix for each one's kinds and reason)
        val ABOUT = Relation<NoProps>("ABOUT")
        val ABOUT_AUTHOR = Relation<NoProps>("ABOUT_AUTHOR")
        val ACCEPTED = Relation<NoProps>("ACCEPTED")
        val ACTOR = Relation<ActorProps>("ACTOR")
        val ADDED_USER = Relation<RoleProps>("ADDED_USER")
        val ADMIN = Relation<RoleProps>("ADMIN")
        val AGENT = Relation<FrameProps>("AGENT")
        val ALLOWED = Relation<RoleProps>("ALLOWED")
        val APP = Relation<NoProps>("APP")
        val APPLIED = Relation<NoProps>("APPLIED")
        val APPROVER = Relation<NoProps>("APPROVER")
        val ARCHIVED = Relation<ModerationProps>("ARCHIVED")
        val ASSERTION = Relation<NoProps>("ASSERTION")
        val ATTESTOR = Relation<NoProps>("ATTESTOR")
        val AUDITED = Relation<AuditProps>("AUDITED")
        val AUTHORED = Relation<NoProps>("AUTHORED")
        val BANNED = Relation<ModerationProps>("BANNED")
        val BASE_VERSION = Relation<NoProps>("BASE_VERSION")
        val CHILD = Relation<NoProps>("CHILD")
        val COLLABORATED = Relation<CollaborationProps>("COLLABORATED")
        val COLLABORATED_AUTHOR = Relation<CollaborationProps>("COLLABORATED_AUTHOR")
        val CONCEPT_GRAPH = Relation<NoProps>("CONCEPT_GRAPH")
        val CONFIRMED = Relation<StatusProps>("CONFIRMED")
        val COPIED = Relation<NoProps>("COPIED")
        val CREATED = Relation<NoProps>("CREATED")
        val CREDITED = Relation<CreditProps>("CREDITED")
        val CURRENT_SCENE = Relation<NoProps>("CURRENT_SCENE")
        val DEFER = Relation<NoProps>("DEFER")
        val DENIED = Relation<NoProps>("DENIED")
        val DESTINATION = Relation<NoProps>("DESTINATION")
        val DESTINATION_AUTHOR = Relation<NoProps>("DESTINATION_AUTHOR")
        val DESTROYED = Relation<NoProps>("DESTROYED")
        val ELEMENT_OF = Relation<NoProps>("ELEMENT_OF")
        val EXERCISE = Relation<NoProps>("EXERCISE")
        val FILE_DATA = Relation<NoProps>("FILE_DATA")
        val FINDER = Relation<NoProps>("FINDER")
        val FOR_USER = Relation<NoProps>("FOR_USER")
        val FOUND = Relation<FoundProps>("FOUND")
        val FUNDED = Relation<NoProps>("FUNDED")
        val GOAL = Relation<NoProps>("GOAL")
        val GROUP = Relation<NoProps>("GROUP")
        val INHERIT_FROM = Relation<NoProps>("INHERIT_FROM")
        val INPUT = Relation<NoProps>("INPUT")
        val INPUT_JOB = Relation<NoProps>("INPUT_JOB")
        val ITEM = Relation<ItemProps>("ITEM")
        val KEY_PACKAGE = Relation<NoProps>("KEY_PACKAGE")
        val KICKED = Relation<NoProps>("KICKED")
        val LINKED = Relation<NoProps>("LINKED")
        val MAINTAINER = Relation<NoProps>("MAINTAINER")
        val MERCHANT = Relation<NoProps>("MERCHANT")
        val NOTIFICATION_SERVER = Relation<NoProps>("NOTIFICATION_SERVER")
        val OBSERVER = Relation<NoProps>("OBSERVER")
        val OPEN_TIMESTAMP = Relation<NoProps>("OPEN_TIMESTAMP")
        val OPPONENT = Relation<ChessResultProps>("OPPONENT")
        val OPTION = Relation<NoProps>("OPTION")
        val ORIGIN = Relation<NoProps>("ORIGIN")
        val OWNER = Relation<OwnerProps>("OWNER")
        val PALETTE = Relation<NoProps>("PALETTE")
        val PARENT_LIST = Relation<NoProps>("PARENT_LIST")
        val PERSONA = Relation<NoProps>("PERSONA")
        val PODCAST_AUTHOR = Relation<RoleProps>("PODCAST_AUTHOR")
        val PUBLICATION = Relation<NoProps>("PUBLICATION")
        val REDEEMED = Relation<NoProps>("REDEEMED")
        val REDEEMED_AUTHOR = Relation<NoProps>("REDEEMED_AUTHOR")
        val RELEASE = Relation<NoProps>("RELEASE")
        val REMINDED = Relation<NoProps>("REMINDED")
        val REMOVED_USER = Relation<NoProps>("REMOVED_USER")
        val REPLACED_BY = Relation<NoProps>("REPLACED_BY")
        val REQUEST = Relation<StatusProps>("REQUEST")
        val REQUEST_AUTHOR = Relation<NoProps>("REQUEST_AUTHOR")
        val RESOLVED = Relation<ResolutionProps>("RESOLVED")
        val RESULT = Relation<NoProps>("RESULT")
        val REVISED = Relation<NoProps>("REVISED")
        val ROLE_CHANGED = Relation<RoleProps>("ROLE_CHANGED")
        val SCHEDULED = Relation<NoProps>("SCHEDULED")
        val SEARCH_AUTHOR = Relation<NoProps>("SEARCH_AUTHOR")
        val SITE_MANIFEST = Relation<ReleaseProps>("SITE_MANIFEST")
        val SNAPSHOTTED = Relation<NoProps>("SNAPSHOTTED")
        val SOURCE = Relation<NoProps>("SOURCE")
        val SOURCE_TAG = Relation<NoProps>("SOURCE_TAG")
        val STALL = Relation<NoProps>("STALL")
        val SUBSET_OF = Relation<NoProps>("SUBSET_OF")
        val TAGGED = Relation<PositionProps>("TAGGED")
        val TEMPLATE = Relation<NoProps>("TEMPLATE")
        val TEXT_TRACK = Relation<NoProps>("TEXT_TRACK")
        val TIMED_OUT = Relation<ModerationProps>("TIMED_OUT")
        val TIMEOUT_CLEARED = Relation<NoProps>("TIMEOUT_CLEARED")
        val TRIGGERED = Relation<NoProps>("TRIGGERED")
        val UNARCHIVED = Relation<ModerationProps>("UNARCHIVED")
        val VERIFIED = Relation<NoProps>("VERIFIED")
        val VERIFIER = Relation<NoProps>("VERIFIER")
        val VIEWED = Relation<ViewProps>("VIEWED")
        val VIDEO = Relation<NoProps>("VIDEO")
        val VIEWER = Relation<NoProps>("VIEWER")
        val VOTED = Relation<VoteProps>("VOTED")
        val WIKILINK = Relation<NoProps>("WIKILINK")
        val WIKILINK_AUTHOR = Relation<NoProps>("WIKILINK_AUTHOR")
        val WINNER = Relation<ChessResultProps>("WINNER")
        val WOT_ROOT = Relation<WotProps>("WOT_ROOT")

        /** Every relation Quartz emits, for consumers that declare a schema up front. */
        val ALL: List<Relation<*>> =
            listOf(
                AUTHOR,
                ADDRESS,
                ROOT,
                PARENT,
                ROOT_AUTHOR,
                PARENT_AUTHOR,
                MENTION,
                QUOTE,
                FORK,
                EDITED,
                EDITED_AUTHOR,
                RECIPIENT,
                COMMUNITY,
                REPOSITORY,
                REPOSITORY_OWNER,
                REACTED,
                REACTED_AUTHOR,
                REPOSTED,
                REPOSTED_AUTHOR,
                ZAPPED,
                ZAP_RECIPIENT,
                ZAP_SENDER,
                ZAP_REQUEST,
                HIGHLIGHTED,
                HIGHLIGHTED_AUTHOR,
                RATED,
                RATED_AUTHOR,
                DELETED,
                DELETED_AUTHOR,
                REPORTED_USER,
                REPORTED,
                REPORTED_AUTHOR,
                LABELED,
                MUTE,
                HIDDEN,
                CHANNEL_MUTED,
                APPROVED,
                APPROVED_AUTHOR,
                MODERATOR,
                FOLLOW,
                SUBSCRIBED,
                FAVORITE,
                MEMBER,
                RECOMMENDED,
                BOOKMARK,
                CURATED,
                PIN,
                AWARDED,
                BADGE_DEFINITION,
                BADGE_AWARD,
                BADGE_SET,
                SUBJECT,
                SERVICE_PROVIDER,
                PARTICIPANT,
                CALENDAR_EVENT,
                CALENDAR_EVENT_AUTHOR,
                CALENDAR,
                RAIDED,
                CLIPPED,
                CLIPPED_AUTHOR,
                POLL,
                POLL_AUTHOR,
                AUCTION,
                AUCTION_AUTHOR,
                BID,
                BID_AUTHOR,
                TIMESTAMPED,
                REDIRECT,
                HASHTAG,
                TAG,
                CLIENT,
                ZAP_SPLIT,
                EMOJI_SET,
                ABOUT,
                ABOUT_AUTHOR,
                ACCEPTED,
                ACTOR,
                ADDED_USER,
                ADMIN,
                AGENT,
                ALLOWED,
                APP,
                APPLIED,
                APPROVER,
                ARCHIVED,
                ASSERTION,
                ATTESTOR,
                AUDITED,
                AUTHORED,
                BANNED,
                BASE_VERSION,
                CHILD,
                COLLABORATED,
                COLLABORATED_AUTHOR,
                CONCEPT_GRAPH,
                CONFIRMED,
                COPIED,
                CREATED,
                CREDITED,
                CURRENT_SCENE,
                DEFER,
                DENIED,
                DESTINATION,
                DESTINATION_AUTHOR,
                DESTROYED,
                ELEMENT_OF,
                EXERCISE,
                FILE_DATA,
                FINDER,
                FOR_USER,
                FOUND,
                FUNDED,
                GOAL,
                GROUP,
                INHERIT_FROM,
                INPUT,
                INPUT_JOB,
                ITEM,
                KEY_PACKAGE,
                KICKED,
                LINKED,
                MAINTAINER,
                MERCHANT,
                NOTIFICATION_SERVER,
                OBSERVER,
                OPEN_TIMESTAMP,
                OPPONENT,
                OPTION,
                ORIGIN,
                OWNER,
                PALETTE,
                PARENT_LIST,
                PERSONA,
                PODCAST_AUTHOR,
                PUBLICATION,
                REDEEMED,
                REDEEMED_AUTHOR,
                RELEASE,
                REMINDED,
                REMOVED_USER,
                REPLACED_BY,
                REQUEST,
                REQUEST_AUTHOR,
                RESOLVED,
                RESULT,
                REVISED,
                ROLE_CHANGED,
                SCHEDULED,
                SEARCH_AUTHOR,
                SITE_MANIFEST,
                SNAPSHOTTED,
                SOURCE,
                SOURCE_TAG,
                STALL,
                SUBSET_OF,
                TAGGED,
                TEMPLATE,
                TEXT_TRACK,
                TIMED_OUT,
                TIMEOUT_CLEARED,
                TRIGGERED,
                UNARCHIVED,
                VERIFIED,
                VERIFIER,
                VIEWED,
                VIDEO,
                VIEWER,
                VOTED,
                WIKILINK,
                WIKILINK_AUTHOR,
                WINNER,
                WOT_ROOT,
            )
    }
}
