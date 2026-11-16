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
package com.vitorpamplona.amethyst.commons.relayClient.profile

import com.vitorpamplona.amethyst.commons.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Re-plans a profile's REQs when the profiled user's relay list (NIP-65) arrives or changes.
 *
 * The posts and media filters go to the user's outbox relays, falling back to the relays the user
 * was seen on. Opening the profile of someone not loaded yet finds neither, so the first REQ goes
 * nowhere; nothing else re-plans it, and the screen stayed on "Feed is empty" until it was opened
 * again. The relay list arrives moments later through the metadata REQ: this watches for it.
 */
class ProfileRelayListWatchers(
    private val onChange: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Touched only from the managers' updateSubscriptions/endSub, which run one at a time.
    private val jobs = mutableMapOf<User, Job>()

    fun watch(user: User) {
        if (user in jobs) return
        jobs[user] =
            scope.launch {
                user.nip65RelayListNote
                    .flow()
                    .metadata.stateFlow
                    .map { user.outboxRelays()?.toSet() }
                    .distinctUntilChanged()
                    // The first value is what the REQ was just planned with.
                    .drop(1)
                    .collect { onChange() }
            }
    }

    fun stop(user: User) {
        jobs.remove(user)?.cancel()
    }

    fun destroy() {
        jobs.clear()
        scope.cancel()
    }
}
