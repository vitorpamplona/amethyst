/**
 * Copyright (c) 2023 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_LIVE

class DiscoverLiveNowFeedFilter(
    account: Account,
) : DiscoverLiveFeedFilter(account) {
    override fun followList(): String {
        // uses follows by default, but other lists if they were selected in the top bar
        val currentList = super.followList()
        return if (currentList == GLOBAL_FOLLOWS) {
            KIND3_FOLLOWS
        } else {
            currentList
        }
    }

    override fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val allItems = super.innerApplyFilter(collection)

        val onlineOnly =
            allItems.filter {
                val noteEvent = it.event as? LiveActivitiesEvent
                noteEvent?.status() == STATUS_LIVE && OnlineChecker.isOnline(noteEvent.streaming())
            }

        return onlineOnly.toSet()
    }
}
