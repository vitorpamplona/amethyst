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
package com.vitorpamplona.amethyst.service

import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.NostrDataSource
import com.vitorpamplona.ammolite.relays.filters.SinceAuthorPerRelayFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object NostrDiscoveryDataSource : NostrDataSource(Amethyst.instance.client) {
    lateinit var account: Account

    val scope = Amethyst.instance.applicationIOScope
    val latestEOSEs = EOSEAccount()

    var job: Job? = null

    private val TAG = "NostrDiscoveryDataSource"

    override fun start() {
        job?.cancel()
        job =
            scope.launch(Dispatchers.IO) {
                account.liveDiscoveryFollowLists.collect {
                    if (this@NostrDiscoveryDataSource::account.isInitialized) {
                        invalidateFilters()
                    }
                }
            }
        super.start()
    }

    override fun stop() {
        super.stop()
        job?.cancel()
    }

    override fun updateSubscriptions() {
        if (!this::account.isInitialized) return

        Log.d(TAG, "Updating subscriptions")

        val subscription =
            requestNewSubscription { timestamp, subId ->
                Log.d(TAG, "Received EOSE for subscription $subId")
            }

        subscription.typedFilters =
            createLiveStreamFilter()
                .plus(createNIP89Filter(listOf("5300", "5050")))
                .plus(createPublicChatFilter())
                .plus(createMarketplaceFilter())
                .plus(
                    listOfNotNull(
                        createLiveStreamTagsFilter(),
                        createLiveStreamGeohashesFilter(),
                        createCommunitiesFilter(),
                        createCommunitiesTagsFilter(),
                        createCommunitiesGeohashesFilter(),
                        createPublicChatsTagsFilter(),
                        createPublicChatsGeohashesFilter(),
                    ),
                ).toList()
                .ifEmpty { null }
    }

    fun createMarketplaceFilter(): List<TypedFilter> {
        val follows = account.liveDiscoveryListAuthorsPerRelay.value?.ifEmpty { null }
        val hashToLoad =
            account.liveDiscoveryFollowLists.value
                ?.hashtags
                ?.toList()
                ?.ifEmpty { null }
        val geohashToLoad =
            account.liveDiscoveryFollowLists.value
                ?.geotags
                ?.toList()
                ?.ifEmpty { null }

        return listOfNotNull(
            TypedFilter(
                types = if (follows == null) setOf(FeedType.GLOBAL) else setOf(FeedType.FOLLOWS),
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = follows,
                        kinds = listOf(ClassifiedsEvent.KIND),
                        limit = 300,
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultDiscoveryFollowList.value)
                                ?.relayList,
                    ),
            ),
            hashToLoad?.let {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(ClassifiedsEvent.KIND),
                            tags =
                                mapOf(
                                    "t" to
                                        it
                                            .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                            .flatten(),
                                ),
                            limit = 300,
                            since =
                                latestEOSEs.users[account.userProfile()]
                                    ?.followList
                                    ?.get(account.settings.defaultDiscoveryFollowList.value)
                                    ?.relayList,
                        ),
                )
            },
            geohashToLoad?.let {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(ClassifiedsEvent.KIND),
                            tags =
                                mapOf(
                                    "g" to it,
                                ),
                            limit = 300,
                            since =
                                latestEOSEs.users[account.userProfile()]
                                    ?.followList
                                    ?.get(account.settings.defaultDiscoveryFollowList.value)
                                    ?.relayList,
                        ),
                )
            },
        )
    }

    fun createNIP89Filter(kTags: List<String>): List<TypedFilter> {
        Log.d(TAG, "Creating NIP89Filter for tags: $kTags")
        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(AppDefinitionEvent.KIND),
                        limit = 500, // Increased limit for better discovery
                        tags = mapOf("k" to kTags),
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultDiscoveryFollowList.value)
                                ?.relayList,
                    ),
            ),
            // Add a filter without tags for global discovery
            if (kTags.contains("5050")) {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(AppDefinitionEvent.KIND),
                            limit = 300,
                            // No tag filter - find all AppDefinitionEvents regardless of k tag
                            since = null, // Don't use EOSE for this to get full history
                        ),
                )
            } else {
                null
            },
        )
    }

    fun createLiveStreamFilter(): List<TypedFilter> {
        val follows =
            account.liveDiscoveryFollowLists.value
                ?.authors
                ?.toList()
                ?.ifEmpty { null }

        val followsRelays = account.liveDiscoveryListAuthorsPerRelay.value

        return listOfNotNull(
            TypedFilter(
                types = if (follows == null) setOf(FeedType.GLOBAL) else setOf(FeedType.FOLLOWS),
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = followsRelays,
                        kinds = listOf(LiveActivitiesChatMessageEvent.KIND, LiveActivitiesEvent.KIND),
                        limit = 300,
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultDiscoveryFollowList.value)
                                ?.relayList,
                    ),
            ),
            follows?.let {
                TypedFilter(
                    types = setOf(FeedType.FOLLOWS),
                    filter =
                        SincePerRelayFilter(
                            tags = mapOf("p" to it),
                            kinds = listOf(LiveActivitiesEvent.KIND),
                            limit = 100,
                            since =
                                latestEOSEs.users[account.userProfile()]
                                    ?.followList
                                    ?.get(account.settings.defaultDiscoveryFollowList.value)
                                    ?.relayList,
                        ),
                )
            },
        )
    }

    fun createPublicChatFilter(): List<TypedFilter> {
        val follows = account.liveDiscoveryListAuthorsPerRelay.value?.ifEmpty { null }
        val followChats = account.selectedChatsFollowList().toList()

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = follows,
                        kinds = listOf(ChannelMessageEvent.KIND),
                        limit = 500,
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultDiscoveryFollowList.value)
                                ?.relayList,
                    ),
            ),
            if (followChats.isNotEmpty()) {
                TypedFilter(
                    types = setOf(FeedType.PUBLIC_CHATS),
                    filter =
                        SincePerRelayFilter(
                            ids = followChats,
                            kinds = listOf(ChannelCreateEvent.KIND, ChannelMessageEvent.KIND),
                            limit = 300,
                            since =
                                latestEOSEs.users[account.userProfile()]
                                    ?.followList
                                    ?.get(account.settings.defaultDiscoveryFollowList.value)
                                    ?.relayList,
                        ),
                )
            } else {
                null
            },
        )
    }

    fun createCommunitiesFilter(): TypedFilter {
        val follows = account.liveDiscoveryListAuthorsPerRelay.value

        return TypedFilter(
            types = if (follows == null) setOf(FeedType.GLOBAL) else setOf(FeedType.FOLLOWS),
            filter =
                SinceAuthorPerRelayFilter(
                    authors = follows,
                    kinds = listOf(CommunityDefinitionEvent.KIND, CommunityPostApprovalEvent.KIND),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createLiveStreamTagsFilter(): TypedFilter? {
        val hashToLoad =
            account.liveDiscoveryFollowLists.value
                ?.hashtags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(LiveActivitiesChatMessageEvent.KIND, LiveActivitiesEvent.KIND),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createLiveStreamGeohashesFilter(): TypedFilter? {
        val hashToLoad =
            account.liveDiscoveryFollowLists.value
                ?.geotags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(LiveActivitiesChatMessageEvent.KIND, LiveActivitiesEvent.KIND),
                    tags = mapOf("g" to hashToLoad),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createPublicChatsTagsFilter(): TypedFilter? {
        val hashToLoad =
            account.liveDiscoveryFollowLists.value
                ?.hashtags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(ChannelCreateEvent.KIND, ChannelMetadataEvent.KIND, ChannelMessageEvent.KIND),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createPublicChatsGeohashesFilter(): TypedFilter? {
        val hashToLoad =
            account.liveDiscoveryFollowLists.value
                ?.geotags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(ChannelCreateEvent.KIND, ChannelMetadataEvent.KIND, ChannelMessageEvent.KIND),
                    tags =
                        mapOf("g" to hashToLoad),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createCommunitiesTagsFilter(): TypedFilter? {
        val hashToLoad =
            account.liveDiscoveryFollowLists.value
                ?.hashtags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(CommunityDefinitionEvent.KIND, CommunityPostApprovalEvent.KIND),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createCommunitiesGeohashesFilter(): TypedFilter? {
        val hashToLoad =
            account.liveDiscoveryFollowLists.value
                ?.geotags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(CommunityDefinitionEvent.KIND, CommunityPostApprovalEvent.KIND),
                    tags = mapOf("g" to hashToLoad),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    // Add dedicated method to request only NIP89 events with specific kinds
    fun requestNIP89Kinds(): List<TypedFilter>? {
        if (!this::account.isInitialized) return null

        Log.d(TAG, "Requesting NIP89 events for kinds 5300, 5050")

        val subscription =
            requestNewSubscription { timestamp, subId ->
                Log.d(TAG, "EOSE received for NIP89 kinds subscription: $subId")
            }

        // Always include 5050 for text generation DVMs
        val filters = createNIP89Filter(listOf("5300", "5050"))
        subscription.typedFilters = filters
        return filters
    }

    // Start DVMs separately when needed
    fun requestTextGenerationDVMs(): List<TypedFilter>? {
        if (!this::account.isInitialized) return null

        Log.d(TAG, "Requesting Text Generation DVMs (kind 5050) from relays")

        val subscription =
            requestNewSubscription { timestamp, subId ->
                Log.d(TAG, "EOSE received for Text Generation DVMs subscription: $subId")
            }

        // Use a dedicated filter just for kind 5050 (text generation) with broader discovery
        val filters =
            listOf(
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(AppDefinitionEvent.KIND),
                            limit = 1000,
                            tags = mapOf("k" to listOf("5050")),
                            since = null, // Don't limit by EOSE to get full history
                        ),
                ),
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(AppDefinitionEvent.KIND),
                            limit = 500,
                        ),
                ),
            )

        subscription.typedFilters = filters
        return filters
    }

    override fun consume(
        event: Event,
        relay: Relay,
    ) {
        try {
            if (event is AppDefinitionEvent) {
                // Check if it's a Text Generation DVM (supports kind 5050)
                val isTextGen = event.includeKind(5050)
                val supportedKinds = event.supportedKinds()

                if (isTextGen) {
                    val metadata = event.appMetaData()
                    val name = metadata?.name ?: "unnamed"

                    Log.d(
                        TAG,
                        "Received DVM definition for $name: id=${event.id.take(8)} with " +
                            "supported kinds: ${supportedKinds.joinToString()}, " +
                            "from relay: ${relay.url}",
                    )
                }

                // Process all AppDefinition events
                LocalCache.consume(event, relay)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consuming event: ${e.message}", e)
        }
    }

    override fun markAsEOSE(
        subscriptionId: String,
        relay: Relay,
    ) {
        super.markAsEOSE(subscriptionId, relay)
        Log.d(TAG, "Marked as EOSE for subscription $subscriptionId on relay ${relay.url}")
    }

    override fun markAsSeenOnRelay(
        eventId: String,
        relay: Relay,
    ) {
        super.markAsSeenOnRelay(eventId, relay)
        // No need to log this as it would be too verbose
    }

    fun resetFilters() {
        invalidateFilters()
    }
}
