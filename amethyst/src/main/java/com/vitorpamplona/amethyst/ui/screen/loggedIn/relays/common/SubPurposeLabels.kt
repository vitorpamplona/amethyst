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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurposeGroup

/**
 * The one place a [SubPurpose] becomes words, shared by the relay screens and the always-on
 * notification so both call the same job the same thing.
 *
 * **Reuses the app's existing vocabulary wherever it exists** — nav routes (`Home`, `Discover`,
 * `Messages`, `Notifications`, `Chess`), event-kind names (`Reports`, `Profile`, `Follow List`,
 * `Outbox Relays`, `Drafts`, `Reactions`), and feature names (`Communities`, `Nests`,
 * `Marmot Group`, `Wallet`). A parallel vocabulary invented here would read as fuzzy exactly because
 * the words match nothing the user can navigate to, and it would need its own translations.
 * New strings exist only for the handful of jobs the app never had to name before.
 */
object SubPurposeLabels {
    fun labelOf(purpose: SubPurpose): Int =
        when (purpose) {
            // account — always on
            SubPurpose.ACCOUNT_DATA -> R.string.kind_drafts
            SubPurpose.PROFILE_METADATA -> R.string.kind_profile
            SubPurpose.RELAY_LISTS -> R.string.kind_outbox_relays
            SubPurpose.FOLLOW_LISTS -> R.string.kind_follow_list
            SubPurpose.MODERATION -> R.string.kind_reports
            SubPurpose.WALLET -> R.string.wallet
            // messages — always on
            SubPurpose.NOTIFICATIONS -> R.string.route_notifications
            SubPurpose.DIRECT_MESSAGES -> R.string.route_messages
            SubPurpose.PUBLIC_CHATS -> R.string.relay_chat_title
            SubPurpose.COMMUNITY_CHATS -> R.string.communities
            SubPurpose.ENCRYPTED_GROUPS -> R.string.marmot_group
            SubPurpose.LIVE_ROOMS -> R.string.nests
            // feeds
            SubPurpose.HOME_FEED -> R.string.route_home
            SubPurpose.DISCOVER_FEED -> R.string.route_discover
            SubPurpose.MEDIA_FEED -> R.string.relay_purpose_media
            SubPurpose.TAG_FEED -> R.string.relay_purpose_tags
            SubPurpose.COMMUNITY_FEED -> R.string.communities
            SubPurpose.TOPIC_FEED -> R.string.relay_purpose_topics
            // current screen
            SubPurpose.THREAD -> R.string.relay_purpose_thread
            SubPurpose.USER_PROFILE -> R.string.kind_profile
            SubPurpose.SEARCH -> R.string.relay_purpose_search
            SubPurpose.ENGAGEMENT -> R.string.kind_reactions
            SubPurpose.REFERENCED_EVENTS -> R.string.relay_purpose_referenced
            SubPurpose.ADD_ONS -> R.string.relay_purpose_add_ons
            SubPurpose.GAMES -> R.string.route_chess
            SubPurpose.RELAY_INFO -> R.string.relay_purpose_relay_info
            SubPurpose.OTHER -> R.string.relay_purpose_other
        }

    /**
     * Whether this job earns its own line in the always-on notification.
     *
     * Derived from the taxonomy rather than hand-listed: the [SubPurposeGroup.ACCOUNT] and
     * [SubPurposeGroup.MESSAGES] groups are exactly the jobs that keep running with the app closed,
     * which is what that notification is about. Feeds and current-screen work tear themselves down on
     * backgrounding, so itemising them would add noise precisely when nobody is looking.
     */
    fun isWorthNamingInNotification(purpose: SubPurpose): Boolean = purpose.group == SubPurposeGroup.ACCOUNT || purpose.group == SubPurposeGroup.MESSAGES
}
