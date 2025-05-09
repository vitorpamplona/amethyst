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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs

import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

open class DiscoverNIP89FeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    val lastAnnounced = 365 * 24 * 60 * 60 // 365 Days ago
    val lastAnnouncedTextGen = 30 * 24 * 60 * 60 // 30 Days ago for text generation DVMs
    // TODO better than announced would be last active, as this requires the DVM provider to regularly update the NIP89 announcement

    companion object {
        const val APP_DEFINITION_KIND = 31990
        const val TEXT_GENERATION_KIND = 5050
        const val CONTENT_DISCOVERY_KIND = 5300

        // Track time of last DVM request to avoid excessive relay traffic
        private val lastDvmRequestTime = AtomicLong(0)
        private const val DVM_REQUEST_COOLDOWN = 60 * 1000 // 1 minute cooldown

        /**
         * Request DVM-related events from relays to ensure our cache has them
         * @param forceRefresh If true, bypass the cooldown check (for TextGen DVMs)
         */
        suspend fun requestDVMEvents(forceRefresh: Boolean = false) {
            val currentTime = System.currentTimeMillis()

            // Check if we've requested recently (skip check if forceRefresh is true)
            if (!forceRefresh && currentTime - lastDvmRequestTime.get() < DVM_REQUEST_COOLDOWN) {
                Log.d("DVM_DEBUG", "Skipping DVM request - on cooldown")
                return
            }

            Log.d("DVM_DEBUG", "Requesting DVM events from relays")

            try {
                // Create a subscription ID
                val subscriptionId = "dvm_discovery_${UUID.randomUUID()}"

                // Create a filter for AppDefinition events
                val relayFilter =
                    SincePerRelayFilter(
                        kinds = listOf(APP_DEFINITION_KIND),
                        limit = 100,
                    )

                // Send the filter to request events
                Amethyst.instance.client.sendFilter(subscriptionId, listOf(TypedFilter(setOf(FeedType.GLOBAL), relayFilter)))

                // Wait to allow relays to respond
                Log.d("DVM_DEBUG", "Waiting for relay responses for DVM events")
                delay(3500)

                // Close the subscription
                Amethyst.instance.client.close(subscriptionId)

                // Update last request time
                lastDvmRequestTime.set(currentTime)

                Log.d("DVM_DEBUG", "Completed DVM events request")

                // Count how many AppDefinitionEvents we have in the cache now
                val count =
                    LocalCache.addressables.count { _, note ->
                        try {
                            note.event is AppDefinitionEvent
                        } catch (e: Exception) {
                            Log.w("DVM_DEBUG", "Error checking if event is AppDefinitionEvent: ${e.message}", e)
                            false
                        }
                    }

                Log.d("DVM_DEBUG", "After request, cache has $count AppDefinition events")
            } catch (e: Exception) {
                Log.e("DVM_DEBUG", "Error requesting DVM events: ${e.message}", e)
            }
        }
    }

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList()

    open fun followList(): String = account.settings.defaultDiscoveryFollowList.value

    override fun showHiddenKey(): Boolean =
        followList() == PeopleListEvent.Companion.blockListFor(account.userProfile().pubkeyHex) ||
            followList() == MuteListEvent.Companion.blockListFor(account.userProfile().pubkeyHex)

    override fun feed(): List<Note> {
        // Launch a request for DVM events in background - non-blocking
        CoroutineScope(Dispatchers.IO).launch {
            // We'll check if this filter cares about TextGen DVMs
            val includesTextGen =
                this@DiscoverNIP89FeedFilter.javaClass.name.contains("TextGen") ||
                    this@DiscoverNIP89FeedFilter.javaClass.name.contains("Text")

            // Pass forceRefresh=true for TextGen DVM filters
            requestDVMEvents(forceRefresh = includesTextGen)
        }

        val notes =
            LocalCache.addressables.filterIntoSet { _, it ->
                acceptDVM(it)
            }

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            account.userProfile().pubkeyHex,
            account.settings.defaultDiscoveryFollowList.value,
            account.liveDiscoveryFollowLists.value,
            account.flowHiddenUsers.value,
        )

    fun acceptDVM(note: Note): Boolean {
        val noteEvent = note.event
        return if (noteEvent is AppDefinitionEvent) {
            acceptDVM(noteEvent)
        } else {
            false
        }
    }

    open fun acceptDVM(noteEvent: AppDefinitionEvent): Boolean {
        val filterParams = buildFilterParams(account)
        // Include regular DVMs (kind 5300) announced within the last year
        // OR text generation DVMs (kind 5050) announced within the last month
        return noteEvent.appMetaData()?.subscription != true &&
            filterParams.match(noteEvent) &&
            (
                (noteEvent.includeKind(CONTENT_DISCOVERY_KIND) && noteEvent.createdAt > TimeUtils.now() - lastAnnounced) ||
                    (noteEvent.includeKind(TEXT_GENERATION_KIND) && noteEvent.createdAt > TimeUtils.now() - lastAnnouncedTextGen)
            )
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> =
        collection.filterTo(HashSet()) {
            acceptDVM(it)
        }

    override fun sort(collection: Set<Note>): List<Note> = collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
}
