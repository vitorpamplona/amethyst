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
package com.vitorpamplona.amethyst.desktop.chess

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip64Chess.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.JesterGameEvents
import com.vitorpamplona.quartz.nip64Chess.JesterProtocol
import com.vitorpamplona.quartz.nip64Chess.toJesterEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Local event cache for Desktop chess events using Jester protocol.
 *
 * Mimics Android's LocalCache.addressables behavior:
 * - Stores events by ID (deduplication)
 * - Queryable by kind with custom filters
 * - Thread-safe via ConcurrentHashMap
 * - Populated from subscription callbacks
 * - Persists across fetcher queries (unlike one-shot relay queries)
 *
 * Jester Protocol Notes:
 * - All chess events use kind 30
 * - Start events: content.kind=0, e-tag references START_POSITION_HASH
 * - Move events: content.kind=1, e-tags=[startEventId, headEventId]
 */
object DesktopChessEventCache {
    // Store all Jester events by ID for deduplication
    @PublishedApi
    internal val events = ConcurrentHashMap<String, JesterEvent>()

    // Index start events by event ID (for quick game lookup)
    private val startEvents = ConcurrentHashMap<String, JesterEvent>()

    // Index move events by startEventId (game ID) for quick game reconstruction
    private val movesByGame = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * Add an event to the cache.
     * Returns true if the event was new, false if it was a duplicate.
     */
    fun add(event: Event): Boolean {
        if (event.kind != JesterProtocol.KIND) return false

        val jesterEvent = event.toJesterEvent() ?: return false
        return addJesterEvent(jesterEvent)
    }

    /**
     * Add a JesterEvent directly.
     */
    fun addJesterEvent(event: JesterEvent): Boolean {
        val isNew = events.putIfAbsent(event.id, event) == null
        if (isNew) {
            if (event.isStartEvent()) {
                startEvents[event.id] = event
            } else if (event.isMoveEvent()) {
                val startId = event.startEventId()
                if (startId != null) {
                    movesByGame.computeIfAbsent(startId) { ConcurrentHashMap.newKeySet() }.add(event.id)
                }
            }
        }
        return isNew
    }

    /**
     * Get an event by ID.
     */
    fun get(id: String): JesterEvent? = events[id]

    /**
     * Get all start events (challenges).
     */
    fun getStartEvents(filter: (JesterEvent) -> Boolean = { true }): List<JesterEvent> = startEvents.values.filter { it.isStartEvent() && filter(it) }

    /**
     * Get all move events for a specific game.
     */
    fun getMovesForGame(startEventId: String): List<JesterEvent> {
        val moveIds = movesByGame[startEventId] ?: return emptyList()
        return moveIds.mapNotNull { events[it] }
    }

    /**
     * Get all events for a specific game (start + moves).
     */
    fun getGameEvents(startEventId: String): JesterGameEvents {
        val startEvent = startEvents[startEventId]
        val moves = getMovesForGame(startEventId)
        return JesterGameEvents(startEvent, moves)
    }

    /**
     * Query events with custom filter.
     */
    fun query(filter: (JesterEvent) -> Boolean): List<JesterEvent> = events.values.filter(filter)

    /**
     * Query start events with custom filter.
     */
    fun queryStartEvents(filter: (JesterEvent) -> Boolean = { true }): List<JesterEvent> = startEvents.values.filter { it.isStartEvent() && filter(it) }

    /**
     * Query move events with custom filter.
     */
    fun queryMoveEvents(filter: (JesterEvent) -> Boolean = { true }): List<JesterEvent> = events.values.filter { it.isMoveEvent() && filter(it) }

    /**
     * Get all game IDs (startEventIds) in the cache.
     */
    fun getAllGameIds(): Set<String> = startEvents.keys.toSet()

    /**
     * Get count of events.
     */
    fun size(): Int = events.size

    /**
     * Get count of start events.
     */
    fun startEventCount(): Int = startEvents.size

    /**
     * Clear all cached events.
     */
    fun clear() {
        events.clear()
        startEvents.clear()
        movesByGame.clear()
    }
}
