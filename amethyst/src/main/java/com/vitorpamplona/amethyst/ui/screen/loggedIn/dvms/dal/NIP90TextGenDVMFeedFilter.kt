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

import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.DiscoverNIP89FeedFilter
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class NIP90TextGenDVMFeedFilter(
    account: Account,
) : DiscoverNIP89FeedFilter(account) {
    companion object {
        const val KIND_REQUEST_DVM_TEXT = DiscoverNIP89FeedFilter.TEXT_GENERATION_KIND
    }

    // helper function to check if an event is a valid text generation DVM
    private fun isValidTextGenDVM(event: AppDefinitionEvent): Boolean {
        try {
            val supportsKind5050 = acceptDVM(event)
            val metadata = event.appMetaData()
            val isValidDvm = metadata != null && metadata.subscription != true
            val passesFilter = buildFilterParams(account).match(event)
            val isRecentlyActive = event.createdAt > TimeUtils.oneWeekAgo()

            return supportsKind5050 && isValidDvm && passesFilter && isRecentlyActive
        } catch (e: Exception) {
            Log.e("DVM_DEBUG", "Error checking if event is valid text generation DVM: ${e.message}")
            return false
        }
    }

    override fun feed(): List<Note> {
        try {
            Log.d("DVM_DEBUG", "NIP90TextGenDVMFeedFilter.feed(): Searching for AppDefinition events in cache")

            // First, get all AppDefinition events in the cache
            val allAppDefinitions =
                LocalCache.addressables.filterIntoSet { _, note ->
                    try {
                        val event = note.event
                        if (event !is AppDefinitionEvent) {
                            false
                        } else {
                            event is AppDefinitionEvent
                        }
                    } catch (e: Exception) {
                        Log.w("DVM_DEBUG", "Error accessing note.event: ${e.message}", e)
                        false
                    }
                }

            Log.d("DVM_DEBUG", "Found ${allAppDefinitions.size} total AppDefinition events in cache")

            // Then filter them specifically for kind:5050 support
            val textGenerationDVMs =
                allAppDefinitions.filter { note ->
                    try {
                        val event = note.event as? AppDefinitionEvent ?: return@filter false
                        isValidTextGenDVM(event)
                    } catch (e: Exception) {
                        Log.e("DVM_DEBUG", "Error filtering AppDefinition: ${e.message}")
                        false
                    }
                }

            Log.d("DVM_DEBUG", "Found ${textGenerationDVMs.size} text generation DVMs after filtering")

            return sort(textGenerationDVMs.toSet())
        } catch (e: Exception) {
            Log.e("DVM_DEBUG", "Error in NIP90TextGenDVMFeedFilter.feed(): ${e.message}", e)
            return emptyList()
        }
    }

    override fun innerApplyFilter(collection: Collection<Note>): Set<Note> =
        collection.filterTo(HashSet()) { note ->
            try {
                val event = note.event
                if (event is AppDefinitionEvent) {
                    isValidTextGenDVM(event)
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
                Log.d("DVM_DEBUG", "DVM supports kind 5050 through direct kind tag")
                return true
            }

            // Also check kind in tags - more permissive to catch DVMs with different tagging styles
            val hasTextGenerationKind =
                noteEvent.tags.any { tag ->
                    val result =
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
                    if (result) {
                        Log.d("DVM_DEBUG", "DVM supports kind 5050 through tag format: ${tag.joinToString(",")}")
                    }
                    result
                }
            if (hasTextGenerationKind) {
                return true
            }

            // Check for text generation markers or capabilities in metadata
            val nip89Data = noteEvent.appMetaData() ?: return false
            val about = nip89Data.about?.lowercase() ?: ""
            val name = nip89Data.name?.lowercase() ?: ""

            // Log metadata for debugging
            Log.d("DVM_DEBUG", "Checking metadata for DVM: name='$name', about='${about.take(50)}...'")

            // Define term patterns to check for text generation capabilities
            val singleTerms: List<String> =
                listOf(
                    stringRes(Amethyst.instance, R.string.text_gen_term_language_model),
                    stringRes(Amethyst.instance, R.string.text_gen_term_llm),
                    stringRes(Amethyst.instance, R.string.text_gen_term_gpt),
                    stringRes(Amethyst.instance, R.string.text_gen_term_ai_assistant),
                )

            val combinedTerms: List<Pair<String, List<String>>> =
                listOf(
                    Pair(
                        stringRes(Amethyst.instance, R.string.text_gen_term_chat),
                        listOf(
                            stringRes(Amethyst.instance, R.string.text_gen_term_bot),
                            stringRes(Amethyst.instance, R.string.text_gen_term_gpt),
                        ),
                    ),
                    Pair(
                        stringRes(Amethyst.instance, R.string.text_gen_term_text),
                        listOf(stringRes(Amethyst.instance, R.string.text_gen_term_generat)),
                    ),
                    Pair(
                        stringRes(Amethyst.instance, R.string.text_gen_term_ai),
                        listOf(stringRes(Amethyst.instance, R.string.text_gen_term_assistant)),
                    ),
                )

            // Check single terms in about
            val hasAboutSingleTerm = singleTerms.any { term: String -> about.contains(term) }

            // Check term combinations in about
            val hasAboutCombination =
                combinedTerms.any { pair: Pair<String, List<String>> ->
                    about.contains(pair.first) && pair.second.any { secondTerm: String -> about.contains(secondTerm) }
                }

            // Check term combinations in name
            val hasNameCombination =
                combinedTerms.any { pair: Pair<String, List<String>> ->
                    name.contains(pair.first) && pair.second.any { secondTerm: String -> name.contains(secondTerm) }
                }

            val isTextGeneration = hasAboutSingleTerm || hasAboutCombination || hasNameCombination

            if (isTextGeneration) {
                Log.d("DVM_DEBUG", "DVM identified as text generation through metadata")
            }

            return isTextGeneration
        } catch (e: Exception) {
            Log.e("DVM_DEBUG", "Error in acceptDVM check: ${e.message}", e)
            return false
        }
    }
} 
