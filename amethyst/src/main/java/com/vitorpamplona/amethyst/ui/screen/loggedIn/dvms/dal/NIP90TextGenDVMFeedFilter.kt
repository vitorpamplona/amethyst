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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.DiscoverNIP89FeedFilter
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class NIP90TextGenDVMFeedFilter(
    account: Account,
) : DiscoverNIP89FeedFilter(account) {
    companion object {
        const val KIND_REQUEST_DVM_TEXT = 5050
    }

    override fun feed(): List<Note> {
        try {
            // First, get all AppDefinition events in the cache
            val allAppDefinitions =
                LocalCache.addressables.filterIntoSet { _, note ->
                    try {
                        note.event is AppDefinitionEvent
                    } catch (e: Exception) {
                        false
                    }
                }

            // Then filter them specifically for kind:5050 support
            val textGenerationDVMs =
                allAppDefinitions.filter { note ->
                    try {
                        val event = note.event as? AppDefinitionEvent ?: return@filter false
                        val supportsKind5050 = acceptDVM(event)
                        val isValidDvm = event.appMetaData()?.subscription != true
                        val passesFilter = buildFilterParams(account).match(event)
                        val isRecentlyActive = event.createdAt > TimeUtils.oneWeekAgo()

                        supportsKind5050 && isValidDvm && passesFilter && isRecentlyActive
                    } catch (e: Exception) {
                        false
                    }
                }

            return sort(textGenerationDVMs.toSet())
        } catch (e: Exception) {
            return emptyList()
        }
    }

    override fun innerApplyFilter(collection: Collection<Note>): Set<Note> =
        collection.filterTo(HashSet()) { note ->
            try {
                val event = note.event
                if (event is AppDefinitionEvent) {
                    val supportsKind5050 = acceptDVM(event)
                    val isValidDvm = event.appMetaData()?.subscription != true
                    val passesFilter = buildFilterParams(account).match(event)
                    val isRecentlyActive = event.createdAt > TimeUtils.oneWeekAgo()

                    supportsKind5050 && isValidDvm && passesFilter && isRecentlyActive
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

    // Override to check for kind 5050 instead of 5300
    override fun acceptDVM(noteEvent: AppDefinitionEvent): Boolean {
        try {
            // Check if this is a kind 5050 Text Generation DVM
            if (noteEvent.includeKind(KIND_REQUEST_DVM_TEXT)) {
                return true
            }

            // Also check kind in tags - more permissive to catch DVMs with different tagging styles
            val hasTextGenerationKind =
                noteEvent.tags.any { tag ->
                    tag.size >= 2 &&
                        (
                            (tag[0] == "k" && tag[1] == "5050") ||
                                (tag[0] == "kind" && tag[1] == "5050") ||
                                (tag[0] == "kinds" && tag[1].contains("5050")) ||
                                // Some DVMs use comma-separated lists
                                (tag[0] == "k" && tag.drop(1).any { it.split(",").contains("5050") }) ||
                                // Some DVMs use spaces to separate in a single field
                                (tag[0] == "k" && tag.drop(1).any { it.split(" ").contains("5050") })
                        )
                }
            if (hasTextGenerationKind) {
                return true
            }

            // Check for text generation markers or capabilities in metadata
            val nip89Data = noteEvent.appMetaData() ?: return false
            val about = nip89Data.about?.lowercase() ?: ""
            val name = nip89Data.name?.lowercase() ?: ""

            // Look for specific text generation terms in metadata
            val isTextGeneration =
                about.contains("text generation") ||
                    about.contains("ai assistant") ||
                    about.contains("language model") ||
                    about.contains("large language model") ||
                    about.contains("llm") ||
                    about.contains("gpt") ||
                    about.contains("text generation") ||
                    about.contains("ai assistant") ||
                    about.contains("chatbot") ||
                    name.contains("text") && name.contains("generat") ||
                    name.contains("ai") && name.contains("assistant") ||
                    name.contains("chat") && (name.contains("bot") || name.contains("gpt"))

            return isTextGeneration
        } catch (e: Exception) {
            return false
        }
    }
} 
