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
package com.vitorpamplona.amethyst.service.eventCache

import android.util.Log
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import java.util.concurrent.atomic.AtomicBoolean

class MemoryTrimmingService {
    var isTrimmingMemoryMutex = AtomicBoolean(false)

    private suspend fun doTrim(account: Account?) {
        LocalCache.cleanObservers()

        val accounts = LocalPreferences.allSavedAccounts().mapNotNull { decodePublicKeyAsHexOrNull(it.npub) }.toSet()

        account?.let {
            LocalCache.pruneHiddenMessages(it)
            LocalCache.pruneOldAndHiddenMessages(it)
            NostrChatroomDataSource.clearEOSEs(it)

            LocalCache.pruneContactLists(accounts)
            LocalCache.pruneRepliesAndReactions(accounts)
            LocalCache.prunePastVersionsOfReplaceables()
            LocalCache.pruneExpiredEvents()
        }
    }

    suspend fun run(account: Account?) {
        if (isTrimmingMemoryMutex.compareAndSet(false, true)) {
            Log.d("ServiceManager", "Trimming Memory")
            try {
                doTrim(account)
            } finally {
                isTrimmingMemoryMutex.getAndSet(false)
            }
        }
    }
}
