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
package com.vitorpamplona.quartz.nip01Core.links

import kotlin.jvm.JvmInline

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
 * The set is open (a consumer may mint its own), but every relation Quartz emits is a constant
 * here, so renaming one is a visible, breaking change.
 */
@JvmInline
value class Relation(
    val name: String,
) {
    override fun toString() = name

    companion object {
        // Authorship and identity
        val AUTHOR = Relation("AUTHOR")
        val ADDRESS = Relation("ADDRESS")

        // Conversation
        val ROOT = Relation("ROOT")
        val PARENT = Relation("PARENT")
        val ROOT_AUTHOR = Relation("ROOT_AUTHOR")
        val PARENT_AUTHOR = Relation("PARENT_AUTHOR")
        val MENTION = Relation("MENTION")
        val QUOTE = Relation("QUOTE")
        val FORK = Relation("FORK")
        val EDITED = Relation("EDITED")
        val EDITED_AUTHOR = Relation("EDITED_AUTHOR")
        val RECIPIENT = Relation("RECIPIENT")
        val COMMUNITY = Relation("COMMUNITY")
        val REPOSITORY = Relation("REPOSITORY")
        val REPOSITORY_OWNER = Relation("REPOSITORY_OWNER")

        // Reactions, reposts, zaps
        val REACTED = Relation("REACTED")
        val REACTED_AUTHOR = Relation("REACTED_AUTHOR")
        val REPOSTED = Relation("REPOSTED")
        val REPOSTED_AUTHOR = Relation("REPOSTED_AUTHOR")
        val ZAPPED = Relation("ZAPPED")
        val ZAP_RECIPIENT = Relation("ZAP_RECIPIENT")
        val ZAP_SENDER = Relation("ZAP_SENDER")
        val ZAP_REQUEST = Relation("ZAP_REQUEST")
        val HIGHLIGHTED = Relation("HIGHLIGHTED")
        val HIGHLIGHTED_AUTHOR = Relation("HIGHLIGHTED_AUTHOR")
        val RATED = Relation("RATED")
        val RATED_AUTHOR = Relation("RATED_AUTHOR")

        // Moderation
        val DELETED = Relation("DELETED")
        val DELETED_AUTHOR = Relation("DELETED_AUTHOR")
        val REPORTED_USER = Relation("REPORTED_USER")
        val REPORTED = Relation("REPORTED")
        val REPORTED_AUTHOR = Relation("REPORTED_AUTHOR")
        val LABELED = Relation("LABELED")
        val MUTE = Relation("MUTE")
        val HIDDEN = Relation("HIDDEN")
        val CHANNEL_MUTED = Relation("CHANNEL_MUTED")
        val APPROVED = Relation("APPROVED")
        val APPROVED_AUTHOR = Relation("APPROVED_AUTHOR")
        val MODERATOR = Relation("MODERATOR")

        // Social graph and lists
        val FOLLOW = Relation("FOLLOW")
        val SUBSCRIBED = Relation("SUBSCRIBED")
        val FAVORITE = Relation("FAVORITE")
        val MEMBER = Relation("MEMBER")
        val RECOMMENDED = Relation("RECOMMENDED")
        val BOOKMARK = Relation("BOOKMARK")
        val CURATED = Relation("CURATED")
        val PIN = Relation("PIN")

        // Badges (NIP-58)
        val AWARDED = Relation("AWARDED")
        val BADGE_DEFINITION = Relation("BADGE_DEFINITION")
        val BADGE_AWARD = Relation("BADGE_AWARD")
        val BADGE_SET = Relation("BADGE_SET")

        // Trust (NIP-85)
        val SUBJECT = Relation("SUBJECT")
        val SERVICE_PROVIDER = Relation("SERVICE_PROVIDER")

        // Events, calendars, live activities, markets
        val PARTICIPANT = Relation("PARTICIPANT")
        val CALENDAR_EVENT = Relation("CALENDAR_EVENT")
        val CALENDAR_EVENT_AUTHOR = Relation("CALENDAR_EVENT_AUTHOR")
        val CALENDAR = Relation("CALENDAR")
        val RAIDED = Relation("RAIDED")
        val CLIPPED = Relation("CLIPPED")
        val CLIPPED_AUTHOR = Relation("CLIPPED_AUTHOR")
        val POLL = Relation("POLL")
        val POLL_AUTHOR = Relation("POLL_AUTHOR")
        val AUCTION = Relation("AUCTION")
        val AUCTION_AUTHOR = Relation("AUCTION_AUTHOR")
        val BID = Relation("BID")
        val BID_AUTHOR = Relation("BID_AUTHOR")
        val TIMESTAMPED = Relation("TIMESTAMPED")
        val REDIRECT = Relation("REDIRECT")

        // Topics and plain tags
        val HASHTAG = Relation("HASHTAG")
        val TAG = Relation("TAG")

        // Every kind: tags any event may carry
        val CLIENT = Relation("CLIENT")
        val ZAP_SPLIT = Relation("ZAP_SPLIT")
        val EMOJI_SET = Relation("EMOJI_SET")

        // Added by the per-class review (see the appendix for each one's kinds and reason)
        val ABOUT = Relation("ABOUT")
        val ABOUT_AUTHOR = Relation("ABOUT_AUTHOR")
        val ACCEPTED = Relation("ACCEPTED")
        val ACTOR = Relation("ACTOR")
        val ADDED_USER = Relation("ADDED_USER")
        val ADMIN = Relation("ADMIN")
        val AGENT = Relation("AGENT")
        val ALLOWED = Relation("ALLOWED")
        val APP = Relation("APP")
        val APPLIED = Relation("APPLIED")
        val APPROVER = Relation("APPROVER")
        val ARCHIVED = Relation("ARCHIVED")
        val ASSERTION = Relation("ASSERTION")
        val ATTESTOR = Relation("ATTESTOR")
        val AUDITED = Relation("AUDITED")
        val AUTHORED = Relation("AUTHORED")
        val BANNED = Relation("BANNED")
        val BASE_VERSION = Relation("BASE_VERSION")
        val CHILD = Relation("CHILD")
        val COLLABORATED = Relation("COLLABORATED")
        val COLLABORATED_AUTHOR = Relation("COLLABORATED_AUTHOR")
        val CONCEPT_GRAPH = Relation("CONCEPT_GRAPH")
        val CONFIRMED = Relation("CONFIRMED")
        val COPIED = Relation("COPIED")
        val CREATED = Relation("CREATED")
        val CREDITED = Relation("CREDITED")
        val CURRENT_SCENE = Relation("CURRENT_SCENE")
        val DEFER = Relation("DEFER")
        val DENIED = Relation("DENIED")
        val DESTINATION = Relation("DESTINATION")
        val DESTINATION_AUTHOR = Relation("DESTINATION_AUTHOR")
        val DESTROYED = Relation("DESTROYED")
        val ELEMENT_OF = Relation("ELEMENT_OF")
        val EXERCISE = Relation("EXERCISE")
        val FILE_DATA = Relation("FILE_DATA")
        val FINDER = Relation("FINDER")
        val FOR_USER = Relation("FOR_USER")
        val FOUND = Relation("FOUND")
        val FUNDED = Relation("FUNDED")
        val GOAL = Relation("GOAL")
        val GROUP = Relation("GROUP")
        val INHERIT_FROM = Relation("INHERIT_FROM")
        val INPUT = Relation("INPUT")
        val INPUT_JOB = Relation("INPUT_JOB")
        val ITEM = Relation("ITEM")
        val KEY_PACKAGE = Relation("KEY_PACKAGE")
        val KICKED = Relation("KICKED")
        val LINKED = Relation("LINKED")
        val MAINTAINER = Relation("MAINTAINER")
        val MERCHANT = Relation("MERCHANT")
        val NOTIFICATION_SERVER = Relation("NOTIFICATION_SERVER")
        val OBSERVER = Relation("OBSERVER")
        val OPEN_TIMESTAMP = Relation("OPEN_TIMESTAMP")
        val OPPONENT = Relation("OPPONENT")
        val OPTION = Relation("OPTION")
        val ORIGIN = Relation("ORIGIN")
        val OWNER = Relation("OWNER")
        val PALETTE = Relation("PALETTE")
        val PARENT_LIST = Relation("PARENT_LIST")
        val PERSONA = Relation("PERSONA")
        val PODCAST_AUTHOR = Relation("PODCAST_AUTHOR")
        val PUBLICATION = Relation("PUBLICATION")
        val REDEEMED = Relation("REDEEMED")
        val REDEEMED_AUTHOR = Relation("REDEEMED_AUTHOR")
        val RELEASE = Relation("RELEASE")
        val REMINDED = Relation("REMINDED")
        val REMOVED_USER = Relation("REMOVED_USER")
        val REPLACED_BY = Relation("REPLACED_BY")
        val REQUEST = Relation("REQUEST")
        val REQUEST_AUTHOR = Relation("REQUEST_AUTHOR")
        val RESOLVED = Relation("RESOLVED")
        val RESULT = Relation("RESULT")
        val REVISED = Relation("REVISED")
        val ROLE_CHANGED = Relation("ROLE_CHANGED")
        val SCHEDULED = Relation("SCHEDULED")
        val SEARCH_AUTHOR = Relation("SEARCH_AUTHOR")
        val SITE_MANIFEST = Relation("SITE_MANIFEST")
        val SNAPSHOTTED = Relation("SNAPSHOTTED")
        val SOURCE = Relation("SOURCE")
        val SOURCE_TAG = Relation("SOURCE_TAG")
        val STALL = Relation("STALL")
        val SUBSET_OF = Relation("SUBSET_OF")
        val TAGGED = Relation("TAGGED")
        val TEMPLATE = Relation("TEMPLATE")
        val TEXT_TRACK = Relation("TEXT_TRACK")
        val TIMED_OUT = Relation("TIMED_OUT")
        val TIMEOUT_CLEARED = Relation("TIMEOUT_CLEARED")
        val TRIGGERED = Relation("TRIGGERED")
        val UNARCHIVED = Relation("UNARCHIVED")
        val VERIFIED = Relation("VERIFIED")
        val VERIFIER = Relation("VERIFIER")
        val VIEWED = Relation("VIEWED")
        val VIDEO = Relation("VIDEO")
        val VIEWER = Relation("VIEWER")
        val VOTED = Relation("VOTED")
        val WIKILINK = Relation("WIKILINK")
        val WIKILINK_AUTHOR = Relation("WIKILINK_AUTHOR")
        val WINNER = Relation("WINNER")
        val WOT_ROOT = Relation("WOT_ROOT")

        /** Every relation Quartz emits, for consumers that declare a schema up front. */
        val ALL: List<Relation> =
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
