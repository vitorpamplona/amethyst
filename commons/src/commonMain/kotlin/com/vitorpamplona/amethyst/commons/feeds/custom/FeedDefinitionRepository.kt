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
package com.vitorpamplona.amethyst.commons.feeds.custom

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

const val MAX_PINNED_FEEDS = 3

@Stable
class FeedDefinitionRepository(
    private val scope: CoroutineScope,
) {
    private val _feeds = MutableStateFlow<ImmutableList<FeedDefinition>>(persistentListOf())
    val feeds: StateFlow<ImmutableList<FeedDefinition>> = _feeds.asStateFlow()

    val groupedFeeds: StateFlow<GroupedFeeds> =
        _feeds
            .map { all ->
                GroupedFeeds(
                    pinned = all.filter { it.pinned }.sortedBy { it.pinOrder }.toImmutableList(),
                    myFeeds = all.filter { !it.pinned && it.source !is FeedSource.DVM }.toImmutableList(),
                    algoFeeds = all.filter { it.source is FeedSource.DVM }.toImmutableList(),
                )
            }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, GroupedFeeds.EMPTY)

    val pinnedFeeds: StateFlow<ImmutableList<FeedDefinition>> =
        groupedFeeds
            .map { it.pinned }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    private val _events = MutableSharedFlow<FeedEvent>(replay = 0)
    val events: SharedFlow<FeedEvent> = _events.asSharedFlow()

    fun load(feeds: List<FeedDefinition>) {
        _feeds.value = feeds.toImmutableList()
    }

    fun snapshot(): List<FeedDefinition> = _feeds.value

    suspend fun add(feed: FeedDefinition) {
        _feeds.value = (_feeds.value + feed).toImmutableList()
        _events.emit(FeedEvent.Created(feed))
    }

    suspend fun update(feed: FeedDefinition) {
        _feeds.value =
            _feeds.value
                .map { if (it.id == feed.id) feed else it }
                .toImmutableList()
    }

    suspend fun delete(id: String) {
        _feeds.value = _feeds.value.filter { it.id != id }.toImmutableList()
    }

    suspend fun pin(id: String): Boolean {
        val currentPinned = _feeds.value.count { it.pinned }
        if (currentPinned >= MAX_PINNED_FEEDS) {
            _events.emit(FeedEvent.PinLimitReached(MAX_PINNED_FEEDS))
            return false
        }
        _feeds.value =
            _feeds.value
                .map {
                    if (it.id == id) it.copy(pinned = true, pinOrder = currentPinned) else it
                }.toImmutableList()
        return true
    }

    suspend fun unpin(id: String) {
        _feeds.value =
            _feeds.value
                .map { if (it.id == id) it.copy(pinned = false, pinOrder = Int.MAX_VALUE) else it }
                .toImmutableList()
        // Reindex remaining pinned
        reindexPinned()
    }

    suspend fun reorderPinned(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val pinned =
            _feeds.value
                .filter { it.pinned }
                .sortedBy { it.pinOrder }
                .toMutableList()
        if (fromIndex !in pinned.indices || toIndex !in pinned.indices) return
        val item = pinned.removeAt(fromIndex)
        pinned.add(toIndex, item)
        val reindexed = pinned.mapIndexed { i, feed -> feed.copy(pinOrder = i) }.associateBy { it.id }
        _feeds.value =
            _feeds.value
                .map { reindexed[it.id] ?: it }
                .toImmutableList()
    }

    private fun reindexPinned() {
        val pinned = _feeds.value.filter { it.pinned }.sortedBy { it.pinOrder }
        val reindexed = pinned.mapIndexed { i, feed -> feed.copy(pinOrder = i) }.associateBy { it.id }
        _feeds.value =
            _feeds.value
                .map { reindexed[it.id] ?: it }
                .toImmutableList()
    }
}

sealed interface FeedEvent {
    data class Created(
        val feed: FeedDefinition,
    ) : FeedEvent

    data class PinLimitReached(
        val max: Int,
    ) : FeedEvent
}

@Immutable
data class GroupedFeeds(
    val pinned: ImmutableList<FeedDefinition>,
    val myFeeds: ImmutableList<FeedDefinition>,
    val algoFeeds: ImmutableList<FeedDefinition>,
) {
    companion object {
        val EMPTY = GroupedFeeds(persistentListOf(), persistentListOf(), persistentListOf())
    }
}
