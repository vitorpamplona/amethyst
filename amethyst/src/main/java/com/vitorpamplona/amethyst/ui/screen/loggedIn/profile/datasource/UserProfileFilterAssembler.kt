/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.datasource

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.ammolite.relays.NostrClient

// This allows multiple screen to be listening to tags, even the same tag
class UserProfileQueryState(
    val user: User,
)

class UserProfileFilterAssembler(
    client: NostrClient,
) : ComposeSubscriptionManager<UserProfileQueryState>() {
    val group =
        listOf(
            // 5 subs per visible user profile screen.
            UserProfileMetadataFilterSubAssembler(client, ::allKeys),
            UserProfilePostsFilterSubAssembler(client, ::allKeys),
            UserProfileMediaFilterSubAssembler(client, ::allKeys),
            UserProfileFollowersFilterSubAssembler(client, ::allKeys),
            UserProfileZapsFilterSubAssembler(client, ::allKeys),
            UserProfileTipsFilterSubAssembler(client, ::allKeys),
        )

    override fun start() = group.forEach { it.start() }

    override fun stop() = group.forEach { it.stop() }

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun invalidateKeys() = invalidateFilters()

    override fun destroy() = group.forEach { it.destroy() }

    override fun printStats() = group.forEach { it.printStats() }
}
