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
package com.vitorpamplona.amethyst.commons.relayClient.subscriptions

/**
 * Why a subscription exists — the client-side answer to "what is this relay doing for me?".
 *
 * Attached to an [ExplainedFilter] and **never sent to a relay**. It exists so the app can account
 * for its own connections: the always-on notification reports a bare "connected to N relays", which
 * is emergent rather than curated — nothing today can tell you whether those N are carrying your DMs
 * or re-dialling a stale outbox hint.
 *
 * ## Two levels on purpose
 *
 * The fine value names the actual job; [group] rolls it up. A relay screen or a bug report wants the
 * fine value ("hashtag feed" vs "thread you are reading"); a notification a user glances at wants the
 * group. Adding a fine value is cheap because the UI can keep rendering groups.
 *
 * ## Lifetime is the other axis
 *
 * [isBackground] marks the purposes *allowed* to outlive the foreground — the ones the always-on
 * notification is actually about. Read it as a ceiling, not a promise: a background-capable purpose
 * with nothing to fetch is simply absent, so absence proves nothing. The direction that does hold is
 * the other one — **a purpose with `isBackground = false` appearing while backgrounded is a leak**,
 * and that is now assertable.
 *
 * Measured on device (cold start, then HOME): foreground carried 13 purposes; backgrounded carried 9,
 * every one of them `isBackground = true`. `HOME_FEED` (337 relays) and `ENGAGEMENT` (20) — the only
 * two `false` entries in flight — were both gone, while the transient account loaders
 * ([RELAY_LISTS], [FOLLOW_LISTS]) were idle rather than torn down.
 */
enum class SubPurpose(
    val group: SubPurposeGroup,
    /** True when this subscription is permitted to stay active while the app is backgrounded. */
    val isBackground: Boolean = false,
) {
    // ---- the account itself: always on -------------------------------------

    /** My own profile, settings and app-specific data. */
    ACCOUNT_DATA(SubPurposeGroup.ACCOUNT, isBackground = true),

    /** Profiles (kind 0) of everyone we render. */
    PROFILE_METADATA(SubPurposeGroup.ACCOUNT, isBackground = true),

    /** NIP-65 relay lists, DM relay lists, blocked/trusted relay sets — outbox discovery. */
    RELAY_LISTS(SubPurposeGroup.ACCOUNT, isBackground = true),

    /** Kind-3 follows and the web-of-trust sets derived from them. */
    FOLLOW_LISTS(SubPurposeGroup.ACCOUNT, isBackground = true),

    /** Reports (kind 1984), mute lists, spam and ban state. */
    MODERATION(SubPurposeGroup.ACCOUNT, isBackground = true),

    /** Cashu wallet, nutzaps, mints and NWC. */
    WALLET(SubPurposeGroup.ACCOUNT, isBackground = true),

    // ---- things addressed to me: always on ---------------------------------

    /** Mentions, reactions, reposts and zaps addressed to me (`#p` = me). Inbox relays. */
    NOTIFICATIONS(SubPurposeGroup.MESSAGES, isBackground = true),

    /** NIP-17 gift wraps and NIP-04 legacy DMs. DM relays. */
    DIRECT_MESSAGES(SubPurposeGroup.MESSAGES, isBackground = true),

    /** NIP-28/29 public channels and relay groups. */
    PUBLIC_CHATS(SubPurposeGroup.MESSAGES, isBackground = true),

    /** Concord and Buzz community planes. */
    COMMUNITY_CHATS(SubPurposeGroup.MESSAGES, isBackground = true),

    /** Marmot / MLS encrypted group messaging. */
    ENCRYPTED_GROUPS(SubPurposeGroup.MESSAGES, isBackground = true),

    /** Live audio rooms (NIP-53 nests) and their presence. */
    LIVE_ROOMS(SubPurposeGroup.MESSAGES),

    // ---- feeds: on while their tab is alive --------------------------------

    /** Posts from the people I follow. Outbox relays. */
    HOME_FEED(SubPurposeGroup.FEEDS),

    /** The Discover tab: live streams, DVM feeds, marketplaces, community suggestions. */
    DISCOVER_FEED(SubPurposeGroup.FEEDS),

    /** Video, shorts, pictures, music, podcasts and long-form tabs. */
    MEDIA_FEED(SubPurposeGroup.FEEDS),

    /** A hashtag or geohash feed. */
    TAG_FEED(SubPurposeGroup.FEEDS),

    /** NIP-72 community feeds. */
    COMMUNITY_FEED(SubPurposeGroup.FEEDS),

    /** Calendars, polls, workouts, git repos, highlights and other typed feeds. */
    TOPIC_FEED(SubPurposeGroup.FEEDS),

    // ---- whatever is on screen right now -----------------------------------

    /** The conversation currently open. */
    THREAD(SubPurposeGroup.CURRENT_SCREEN),

    /** A profile page currently open. */
    USER_PROFILE(SubPurposeGroup.CURRENT_SCREEN),

    /** Search results. */
    SEARCH(SubPurposeGroup.CURRENT_SCREEN),

    /** Replies, reactions and zaps on the notes currently rendered. */
    ENGAGEMENT(SubPurposeGroup.CURRENT_SCREEN),

    /**
     * Events something on screen points at but we do not hold — a quoted note, the parent of a
     * reply, an addressable a card renders. Distinct from [ENGAGEMENT], which is inbound reactions
     * *to* what we already have; this is outbound resolution of references.
     */
    REFERENCED_EVENTS(SubPurposeGroup.CURRENT_SCREEN),

    /** Badges, emoji packs, napplets, nSites, software apps and other add-ons. */
    ADD_ONS(SubPurposeGroup.CURRENT_SCREEN),

    /** Games — chess and friends. */
    GAMES(SubPurposeGroup.CURRENT_SCREEN),

    /** Relay information screens probing a relay directly. */
    RELAY_INFO(SubPurposeGroup.CURRENT_SCREEN),

    /** Anything not worth its own bucket; carry detail in [ExplainedFilter.purposeDetail]. */
    OTHER(SubPurposeGroup.OTHER),
}

/** Coarse roll-up of [SubPurpose], for surfaces that should not list two dozen categories. */
enum class SubPurposeGroup {
    /** Keeping the account itself current: profile, follows, relays, moderation, wallet. */
    ACCOUNT,

    /** Anything addressed to me or to a room I am in. */
    MESSAGES,

    /** Timelines the user browses. */
    FEEDS,

    /** Bound to a screen that is open right now. */
    CURRENT_SCREEN,

    OTHER,
}
