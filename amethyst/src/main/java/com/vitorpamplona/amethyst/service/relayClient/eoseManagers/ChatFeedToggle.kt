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
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.model.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Rebuilds a Messages-inbox subscription's filters whenever the user flips [type]'s
 * load-toggle in Settings › Messages. A disabled type's `updateFilter` returns no filters, so a
 * flip to off empties the live subscription and a flip back on re-arms it — no restart needed.
 *
 * Only the boolean for [type] is watched (via distinct + drop(1)), so unrelated toggle changes
 * don't churn this assembler.
 */
fun CoroutineScope.launchChatFeedToggleObserver(
    account: Account,
    type: ChatFeedType,
    onToggle: () -> Unit,
): Job =
    launch(Dispatchers.IO) {
        account.settings.enabledChatFeeds
            .map { type in it }
            .distinctUntilChanged()
            .drop(1)
            .collect { onToggle() }
    }
