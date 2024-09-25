/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.ammolite.relays

import com.vitorpamplona.quartz.encoders.RelayUrlFormatter

object Constants {
    val activeTypes = setOf(FeedType.FOLLOWS, FeedType.PRIVATE_DMS)
    val activeTypesChats = setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS)
    val activeTypesGlobalChats = setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS, FeedType.GLOBAL)
    val activeTypesSearch = setOf(FeedType.SEARCH)

    val defaultRelays =
        arrayOf(
            // Free relays for only DMs, Chats and Follows due to the amount of spam
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://nostr.bitcoiner.social"), read = true, write = true, feedTypes = activeTypesChats),
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://relay.nostr.bg"), read = true, write = true, feedTypes = activeTypesChats),
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://nostr.oxtr.dev"), read = true, write = true, feedTypes = activeTypesChats),
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://nostr.fmt.wiz.biz"), read = true, write = false, feedTypes = activeTypesChats),
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://relay.damus.io"), read = true, write = true, feedTypes = activeTypes),
            // Global
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://nostr.mom"), read = true, write = true, feedTypes = activeTypesGlobalChats),
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://nos.lol"), read = true, write = true, feedTypes = activeTypesGlobalChats),
            // Paid relays
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://nostr.wine"), read = true, write = false, feedTypes = activeTypesGlobalChats),
            // Supporting NIP-50
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://relay.nostr.band"), read = true, write = false, feedTypes = activeTypesSearch),
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://nostr.wine"), read = true, write = false, feedTypes = activeTypesSearch),
            RelaySetupInfo(RelayUrlFormatter.normalize("wss://relay.noswhere.com"), read = true, write = false, feedTypes = activeTypesSearch),
        )

    val defaultSearchRelaySet =
        setOf(
            RelayUrlFormatter.normalize("wss://relay.nostr.band"),
            RelayUrlFormatter.normalize("wss://nostr.wine"),
            RelayUrlFormatter.normalize("wss://relay.noswhere.com"),
        )
}
