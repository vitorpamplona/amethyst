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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Per-subscription watchers for a subscription manager whose filters read some Settings › Messages
 * toggles ([types]): while a subscription is open, a flip of any of them — as published by the account's
 * [com.vitorpamplona.amethyst.commons.model.chats.ChatFeedToggles.applied], i.e. once the rooms have
 * caught up — calls [onToggle]. [BaseEoseManager] owns one, so a manager only declares which toggles it
 * reads (`watchedChatFeeds`) and the base classes start and stop the watchers with each subscription.
 */
class ChatFeedWatchers(
    private val types: Set<ChatFeedType>,
    private val onToggle: () -> Unit,
) {
    private val jobs = mutableMapOf<Any, Job>()

    /**
     * Starts watching for [subKey] unless already watching. Call it *before* building that
     * subscription's filters: the baseline is read here, synchronously, so a toggle applied between the
     * filter build and the watcher's first collection is still seen as a change.
     */
    fun ensure(
        subKey: Any,
        account: IAccount,
    ) {
        if (types.isEmpty() || jobs[subKey]?.isActive == true) return
        // Only a full Account has toggles; other IAccount implementations (the legacy desktop one) don't.
        val owner = account as? Account ?: return
        val toggles = owner.chatFeedToggles
        val baseline = toggles.applied.value intersect types
        jobs[subKey] =
            owner.scope.launch(Dispatchers.IO) {
                toggles.applied
                    .map { it intersect types }
                    .distinctUntilChanged()
                    .dropWhile { it == baseline }
                    .collect { onToggle() }
            }
    }

    fun stop(subKey: Any) {
        jobs.remove(subKey)?.cancel()
    }
}
