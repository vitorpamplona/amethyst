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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Filter specifically for identifying DVMs that support Text Generation (kind 5050)
 * and are active within the past week based on their NIP89 advertisement timestamps
 */
class TextGenerationDVMFeedFilter(
    account: Account,
) : DiscoverNIP89FeedFilter(account) {
    override fun feed(): List<Note> {
        try {
            // Get all AppDefinition notes that include kind 5050
            val notes =
                LocalCache.addressables.filterIntoSet { _, note ->
                    try {
                        val event = note.event
                        if (event is AppDefinitionEvent) {
                            // Direct check for kind 5050 support
                            event.includeKind(5050) && acceptDVM(event)
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                }

            return sort(notes)
        } catch (e: Exception) {
            return emptyList()
        }
    }

    override fun innerApplyFilter(collection: Collection<Note>): Set<Note> =
        collection.filterTo(HashSet()) { note ->
            try {
                acceptDVM(note)
            } catch (e: Exception) {
                false
            }
        }

    override fun acceptDVM(noteEvent: AppDefinitionEvent): Boolean {
        try {
            val filterParams = buildFilterParams(account)

            // Check if it's a valid DVM and not a subscription
            val isValidDvm = noteEvent.appMetaData()?.subscription != true

            // Check if it passes our filter parameters
            val passesFilter = filterParams.match(noteEvent)

            // Check if it's specifically for kind 5050
            val isKind5050 = noteEvent.includeKind(5050)

            // Check if it was created in the last week (7 days)
            val isRecentlyActive = noteEvent.createdAt > TimeUtils.oneWeekAgo()

            return isValidDvm && passesFilter && isKind5050 && isRecentlyActive
        } catch (e: Exception) {
            return false
        }
    }
} 
