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
package com.vitorpamplona.quartz.buzz.kinds

/**
 * The complete Buzz (`block/buzz`) event-kind registry, mirroring the authoritative
 * `KIND_*` constants in `crates/buzz-core/src/kind.rs`.
 *
 * Buzz runs NIP-01 on the wire and uses the `kind` integer as its only dispatch
 * switch: the relay routes, stores, indexes, audits, and triggers workflows purely
 * off the kind. This object is the single Kotlin-side source of truth for those
 * numbers.
 *
 * Many entries are *standard* Nostr kinds that Quartz already models with dedicated
 * `Event` subclasses (see the `mappedTo` notes). They are listed here for
 * completeness so a reader can see the whole protocol surface in one place; new
 * Quartz classes are only added for the **Buzz-custom** kinds that have no upstream
 * equivalent.
 *
 * Source of truth: the Rust code in `block/buzz` (kind.rs + the per-kind modules and
 * `buzz-sdk` builders), **not** the prose NIP drafts under `docs/nips/`, which lag
 * the implementation.
 */
object BuzzKinds {
    // ------------------------------------------------------------------
    // Standard Nostr kinds (already implemented in Quartz — listed for the map)
    // ------------------------------------------------------------------
    const val PROFILE = 0 // -> nip01Core MetadataEvent
    const val TEXT_NOTE = 1 // -> nip10Notes TextNoteEvent
    const val CONTACT_LIST = 3 // -> nip02FollowList ContactListEvent
    const val DELETION = 5 // -> nip09Deletions DeletionEvent
    const val REACTION = 7 // -> nip25Reactions ReactionEvent
    const val CHANNEL_METADATA = 41 // -> nip28PublicChat ChannelMetadataEvent
    const val GIFT_WRAP = 1059 // -> nip59Giftwrap GiftWrapEvent
    const val FILE_METADATA = 1063 // -> nip94FileMetadata FileHeaderEvent
    const val REPORT = 1984 // -> nip56Reports ReportEvent
    const val MUTE_LIST = 10000 // -> nip51Lists MuteListEvent
    const val PIN_LIST = 10001 // -> nip51Lists PinListEvent
    const val NIP65_RELAY_LIST_METADATA = 10002 // -> nip65RelayList AdvertisedRelayListEvent
    const val BOOKMARK_LIST = 10003 // -> nip51Lists BookmarkListEvent
    const val EMOJI_LIST = 10030 // -> nip30CustomEmoji EmojiPackSelectionEvent
    const val FOLLOW_SET = 30000 // -> nip51Lists PeopleListEvent
    const val BOOKMARK_SET = 30003 // -> nip51Lists LabeledBookmarkListEvent
    const val EMOJI_SET = 30030 // -> nip30CustomEmoji EmojiPackEvent
    const val LONG_FORM = 30023 // -> nip23LongContent LongTextNoteEvent
    const val READ_STATE = 30078 // -> nip78AppData AppSpecificDataEvent (see also NIP-RS below)
    const val USER_STATUS = 30315 // -> nip38UserStatus StatusEvent
    const val AUTH = 22242 // -> nip42RelayAuth RelayAuthEvent (see also NIP-AA below)
    const val BLOSSOM_AUTH = 24242 // -> nipB7Blossom BlossomAuthorizationEvent
    const val NOSTR_IDENTITY_BINDING = 24243
    const val HTTP_AUTH = 27235 // -> nip98HttpAuth HTTPAuthorizationEvent

    // NIP-34 git (all implemented in Quartz nip34Git)
    const val GIT_REPO_ANNOUNCEMENT = 30617
    const val GIT_REPO_STATE = 30618
    const val GIT_PATCH = 1617
    const val GIT_PULL_REQUEST = 1618
    const val GIT_PR_UPDATE = 1619
    const val GIT_ISSUE = 1621
    const val GIT_STATUS_OPEN = 1630
    const val GIT_STATUS_MERGED = 1631
    const val GIT_STATUS_CLOSED = 1632
    const val GIT_STATUS_DRAFT = 1633

    // NIP-29 relay groups (implemented in Quartz nip29RelayGroups)
    const val NIP29_PUT_USER = 9000
    const val NIP29_REMOVE_USER = 9001
    const val NIP29_EDIT_METADATA = 9002
    const val NIP29_DELETE_EVENT = 9005
    const val NIP29_CREATE_GROUP = 9007
    const val NIP29_DELETE_GROUP = 9008
    const val NIP29_CREATE_INVITE = 9009
    const val NIP29_JOIN_REQUEST = 9021
    const val NIP29_LEAVE_REQUEST = 9022
    const val NIP29_GROUP_METADATA = 39000
    const val NIP29_GROUP_ADMINS = 39001
    const val NIP29_GROUP_MEMBERS = 39002
    const val NIP29_GROUP_ROLES = 39003

    // NIP-43 relay membership (implemented in Quartz nip43RelayMembers)
    const val NIP43_MEMBERSHIP_LIST = 13534
    const val NIP43_MEMBER_ADDED = 8000
    const val NIP43_MEMBER_REMOVED = 8001
    const val NIP43_LEAVE_REQUEST = 28936

    // ------------------------------------------------------------------
    // Buzz-custom kinds (no upstream equivalent — implemented under buzz/)
    // ------------------------------------------------------------------

    // Agent identity & memory
    const val AGENT_PROFILE = 10100
    const val AGENT_ENGRAM = 30174 // NIP-AE
    const val PERSONA = 30175 // NIP-AP
    const val TEAM = 30176
    const val MANAGED_AGENT = 30177

    // Agent observability & accounting
    const val AGENT_OBSERVER_FRAME = 24200 // NIP-AO (ephemeral)
    const val AGENT_TURN_METRIC = 44200 // NIP-AM

    // Workspace / relay-scoped
    const val EVENT_REMINDER = 30300 // NIP-ER
    const val PUSH_LEASE = 30350 // NIP-PL
    const val DM_VISIBILITY = 30622 // NIP-DV (relay-signed)
    const val SET_WORKSPACE_PROFILE = 9033 // NIP-WP (relay admin)
    const val THREAD_SUMMARY = 39005 // NIP-CW (relay-signed)
    const val WINDOW_BOUNDS = 39006 // NIP-CW (relay-signed)

    // Relay admin
    const val RELAY_ADMIN_ADD_MEMBER = 9030
    const val RELAY_ADMIN_REMOVE_MEMBER = 9031
    const val RELAY_ADMIN_CHANGE_ROLE = 9032

    // Moderation
    const val MODERATION_BAN = 9040
    const val MODERATION_UNBAN = 9041 // NOTE: collides with NIP-75 zap-goal (GoalEvent) in Quartz
    const val MODERATION_TIMEOUT = 9042
    const val MODERATION_UNTIMEOUT = 9043
    const val MODERATION_RESOLVE_REPORT = 9044
    const val PRODUCT_FEEDBACK = 42000

    // Identity archival (NIP-IA)
    const val IA_ARCHIVE_REQUEST = 9035
    const val IA_UNARCHIVE_REQUEST = 9036
    const val IA_ARCHIVED = 8002
    const val IA_UNARCHIVED = 8003
    const val IA_ARCHIVED_LIST = 13535

    // Presence & typing (ephemeral)
    const val PRESENCE_UPDATE = 20001
    const val TYPING_INDICATOR = 20002
    const val HUDDLE_REACTION = 24810
    const val PAIRING = 24134

    // Stream messaging
    const val STREAM_MESSAGE = 9
    const val STREAM_MESSAGE_V2 = 40002
    const val STREAM_MESSAGE_EDIT = 40003
    const val STREAM_MESSAGE_PINNED = 40004
    const val STREAM_MESSAGE_BOOKMARKED = 40005
    const val STREAM_MESSAGE_SCHEDULED = 40006
    const val STREAM_REMINDER = 40007
    const val STREAM_MESSAGE_DIFF = 40008
    const val SYSTEM_MESSAGE = 40099
    const val CANVAS = 40100

    // Relay-only sidecars
    const val CHANNEL_SUMMARY = 40901
    const val PRESENCE_SNAPSHOT = 40902

    // Direct messages
    const val DM_CREATED = 41001
    const val DM_OPEN = 41010
    const val DM_ADD_MEMBER = 41011
    const val DM_HIDE = 41012

    // Agent job protocol
    const val JOB_REQUEST = 43001
    const val JOB_ACCEPTED = 43002
    const val JOB_PROGRESS = 43003
    const val JOB_RESULT = 43004
    const val JOB_CANCEL = 43005
    const val JOB_ERROR = 43006

    // Notifications
    const val MEMBER_ADDED_NOTIFICATION = 44100
    const val MEMBER_REMOVED_NOTIFICATION = 44101

    // Forum / social
    const val FORUM_POST = 45001
    const val FORUM_VOTE = 45002
    const val FORUM_COMMENT = 45003

    // Workflow engine
    const val WORKFLOW_DEF = 30620
    const val WORKFLOW_TRIGGER = 46020
    const val APPROVAL_GRANT = 46030
    const val APPROVAL_DENY = 46031
    const val WORKFLOW_TRIGGERED = 46001
    const val WORKFLOW_STEP_STARTED = 46002
    const val WORKFLOW_STEP_COMPLETED = 46003
    const val WORKFLOW_STEP_FAILED = 46004
    const val WORKFLOW_COMPLETED = 46005
    const val WORKFLOW_FAILED = 46006
    const val WORKFLOW_CANCELLED = 46007
    const val WORKFLOW_APPROVAL_REQUESTED = 46010
    const val WORKFLOW_APPROVAL_GRANTED = 46011
    const val WORKFLOW_APPROVAL_DENIED = 46012

    // System / admin
    const val AUDIT_ENTRY = 48001
    const val HUDDLE_STARTED = 48100
    const val HUDDLE_PARTICIPANT_JOINED = 48101
    const val HUDDLE_PARTICIPANT_LEFT = 48102
    const val HUDDLE_ENDED = 48103
    const val HUDDLE_GUIDELINES = 48106

    // Media
    const val MEDIA_UPLOAD = 49001
}
