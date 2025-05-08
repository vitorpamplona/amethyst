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

import android.util.Log
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Filter specifically for identifying DVMs that support Text Generation (kind 5050)
 */
class TextGenerationDVMFeedFilter(
    account: Account,
) : DiscoverNIP89FeedFilter(account) {
    override fun feed(): List<Note> {
        Log.d("DVM", "TextGenerationDVMFeedFilter.feed() running...")
        try {
            // Get all notes that match our DVM criteria
            val notes =
                LocalCache.addressables.filterIntoSet { _, it ->
                    try {
                        acceptDVM(it)
                    } catch (e: Exception) {
                        Log.e("DVM", "Error checking DVM acceptance: ${e.message}")
                        false
                    }
                }

            // Add additional scan for pure kinds
            val additionalNotes = HashSet<Note>()
            LocalCache.notes.forEach { _, note ->
                try {
                    if (note.event is AppDefinitionEvent) {
                        val appDef = note.event as AppDefinitionEvent
                        val includesKind = appDef.includeKind(5050)
                        if (includesKind) {
                            Log.d("DVM", "Additional scan found DVM with ID: ${note.idHex.take(8)}")
                            additionalNotes.add(note)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DVM", "Error in additional scan: ${e.message}")
                }
            }

            val combined = HashSet<Note>()
            combined.addAll(notes)
            combined.addAll(additionalNotes)

            val result = sort(combined)
            Log.d("DVM", "TextGenerationDVMFeedFilter found ${result.size} DVMs (${notes.size} primary, ${additionalNotes.size} additional)")

            return result
        } catch (e: Exception) {
            Log.e("DVM", "Error in TextGenerationDVMFeedFilter.feed(): ${e.message}", e)
            return emptyList()
        }
    }

    override fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        Log.d("DVM", "TextGenerationDVMFeedFilter.innerApplyFilter running with ${collection.size} notes")
        return collection.filterTo(HashSet()) {
            try {
                acceptDVM(it)
            } catch (e: Exception) {
                Log.e("DVM", "Error in innerApplyFilter: ${e.message}")
                false
            }
        }
    }

    override fun acceptDVM(noteEvent: AppDefinitionEvent): Boolean {
        try {
            val filterParams = buildFilterParams(account)

            // Include only DVMs that explicitly support kind 5050 (Text Generation)
            val supportedKinds = noteEvent.supportedKinds()
            val supportsTextGeneration = noteEvent.includeKind(5050)

            // Get metadata for better logging
            val metadata = noteEvent.appMetaData()
            val name = metadata?.name ?: "unnamed"

            // Log for debugging
            Log.d(
                "DVM",
                "Checking DVM $name with id=${noteEvent.id.take(8)}, " +
                    "kinds=${supportedKinds.joinToString()}, " +
                    "supports5050=$supportsTextGeneration",
            )

            // Only include DVMs active in the past 3 months
            val threeMonthsAgo = TimeUtils.now() - (90 * 24 * 60 * 60)
            val result =
                noteEvent.appMetaData()?.subscription != true &&
                    filterParams.match(noteEvent) &&
                    supportsTextGeneration &&
                    noteEvent.createdAt > threeMonthsAgo

            if (result) {
                Log.d("DVM", "Accepted DVM: $name")
            }

            return result
        } catch (e: Exception) {
            Log.e("DVM", "Error in acceptDVM: ${e.message}")
            return false
        }
    }
} 
