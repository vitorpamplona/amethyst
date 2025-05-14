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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.loaders

import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.SingleSubNoEoseCacheEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter

class UserLoaderSubAssembler(
    client: NostrClient,
    allKeys: () -> Set<UserFinderQueryState>,
) : SingleSubNoEoseCacheEoseManager<UserFinderQueryState>(client, allKeys, invalidateAfterEose = true) {
    override fun updateFilter(keys: List<UserFinderQueryState>): List<TypedFilter>? {
        val firstTimers = keys.filter { it.user.latestMetadata == null }.mapTo(mutableSetOf()) { it.user.pubkeyHex }

        if (firstTimers.isEmpty()) return null

        return filterNewUserMetadataForKey(firstTimers)
    }

    override fun distinct(key: UserFinderQueryState) = key.user
}
