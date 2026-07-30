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
 * Attached to an [ExplainedFilter] and **never sent to a relay** (see [ExplainedFilter]). It exists
 * so the app can account for its own connections: the always-on notification reports a bare
 * "connected to N relays", which is emergent rather than curated — nothing today can tell you
 * whether those N are carrying your DMs or just re-dialling a stale outbox hint.
 *
 * An enum rather than a free-form string so the UI can group and localize by a stable key instead of
 * matching on text. Use [other] with a label when a subscription genuinely doesn't fit a category —
 * that keeps one-off debug filters describable without diluting the buckets a user sees.
 */
enum class SubPurpose {
    /** Mentions, reactions, reposts, zaps addressed to me (`#p` = me). Inbox relays. */
    NOTIFICATIONS,

    /** NIP-17 gift wraps and NIP-04 legacy DMs. DM relays. */
    DIRECT_MESSAGES,

    /** Posts from the people I follow. Outbox relays. */
    HOME_FEED,

    /** Profiles (kind 0) — mine and everyone I render. */
    PROFILE_METADATA,

    /** Relay lists (NIP-65), DM relay lists, blocked/trusted relay sets. */
    RELAY_LISTS,

    /** Follow lists and follow-list-derived sets (kind 3, web of trust). */
    FOLLOW_LISTS,

    /** Group, channel and community chat (NIP-28/29, Concord, Buzz). */
    CHATS,

    /** Reports and moderation state (kind 1984, mute lists). */
    MODERATION,

    /** Cashu wallet, nutzaps and mint directories. */
    WALLET,

    /** A thread, profile page, hashtag or anything else opened on demand. Short-lived. */
    SCREEN_CONTENT,

    /** Anything not worth its own bucket; carry detail in [ExplainedFilter.purposeDetail]. */
    OTHER,
}
