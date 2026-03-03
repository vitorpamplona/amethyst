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
package com.vitorpamplona.amethyst.commons.chess

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip64Chess.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.JesterGameEvents
import com.vitorpamplona.quartz.nip64Chess.JesterProtocol
import com.vitorpamplona.quartz.nip64Chess.toJesterEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects and aggregates Jester chess events for a game from any source.
 *
 * This class provides a unified way to collect events regardless of whether
 * they come from LocalCache (Android), direct relay (Desktop), or test fixtures.
 * It handles:
 * - Deduplication by event ID
 * - Thread-safe concurrent updates
 * - Producing JesterGameEvents snapshots for reconstruction
 *
 * Jester Protocol:
 * - All chess events use kind 30
 * - Start events (content.kind=0) reference START_POSITION_HASH via e-tag
 * - Move events (content.kind=1) reference [startEventId, headEventId] via e-tags
 * - Full move history is included in every move event
 *
 * Usage:
 * ```
 * val collector = ChessEventCollector(startEventId)
 *
 * // Add events as they arrive from any source
 * collector.addEvent(jesterEvent)
 *
 * // Get current state for reconstruction
 * val events = collector.getEvents()
 * val result = ChessStateReconstructor.reconstruct(events, viewerPubkey)
 * ```
 */
class ChessEventCollector(
    /** The start event ID - this is the game identifier in Jester protocol */
    val startEventId: String,
) {
    // Start event (only one per game)
    private val _startEvent = MutableStateFlow<JesterEvent?>(null)
    val startEvent: StateFlow<JesterEvent?> = _startEvent.asStateFlow()

    // Move events (deduplicated by event ID)
    private val moves = ConcurrentHashMap<String, JesterEvent>()

    // Track all processed event IDs for fast deduplication
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()

    // Flow that emits when any event is added (for reactive updates)
    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount.asStateFlow()

    /**
     * Check if an event has already been processed.
     */
    fun hasEvent(eventId: String): Boolean = processedEventIds.contains(eventId)

    /**
     * Add a Jester event for this game.
     * Automatically categorizes as start or move based on content.kind.
     *
     * @return true if the event was added, false if already exists or invalid
     */
    fun addEvent(event: JesterEvent): Boolean {
        if (processedEventIds.contains(event.id)) return false

        // Check if this event belongs to our game
        val eventStartId = event.startEventId()
        val isStartEvent = event.isStartEvent() && event.id == startEventId
        val isMoveEvent = event.isMoveEvent() && eventStartId == startEventId

        if (!isStartEvent && !isMoveEvent) return false

        return if (isStartEvent) {
            addStartEvent(event)
        } else {
            addMoveEvent(event)
        }
    }

    /**
     * Add a raw Event if it's a valid Jester event for this game.
     *
     * @return true if the event was added, false if invalid or not for this game
     */
    fun addRawEvent(event: Event): Boolean {
        if (event.kind != JesterProtocol.KIND) return false
        val jesterEvent = event.toJesterEvent() ?: return false
        return addEvent(jesterEvent)
    }

    /**
     * Add the start event for this game.
     * Only the first valid start is kept.
     *
     * @return true if the event was added, false if already exists or invalid
     */
    private fun addStartEvent(event: JesterEvent): Boolean {
        if (processedEventIds.contains(event.id)) return false
        if (!event.isStartEvent()) return false
        if (event.id != startEventId) return false

        if (_startEvent.compareAndSet(null, event)) {
            processedEventIds.add(event.id)
            incrementEventCount()
            return true
        }
        return false
    }

    /**
     * Add a move event for this game.
     * Moves are deduplicated by event ID.
     *
     * @return true if the event was added, false if already exists or invalid
     */
    private fun addMoveEvent(event: JesterEvent): Boolean {
        if (processedEventIds.contains(event.id)) return false
        if (!event.isMoveEvent()) return false
        if (event.startEventId() != startEventId) return false

        if (moves.putIfAbsent(event.id, event) == null) {
            processedEventIds.add(event.id)
            incrementEventCount()
            return true
        }
        return false
    }

    /**
     * Get a snapshot of all collected events for reconstruction.
     */
    fun getEvents(): JesterGameEvents =
        JesterGameEvents(
            startEvent = _startEvent.value,
            moves = moves.values.toList(),
        )

    /**
     * Check if the game has a start event.
     */
    fun hasStartEvent(): Boolean = _startEvent.value != null

    /**
     * Check if the game has any moves (indicates game is active).
     */
    fun hasMoves(): Boolean = moves.isNotEmpty()

    /**
     * Check if the game has ended (has a move with result).
     */
    fun hasEnded(): Boolean = moves.values.any { it.result() != null }

    /**
     * Get the number of moves collected.
     */
    fun moveCount(): Int = moves.size

    /**
     * Get the latest move (with longest history).
     */
    fun latestMove(): JesterEvent? = moves.values.maxByOrNull { it.history().size }

    /**
     * Clear all collected events.
     */
    fun clear() {
        _startEvent.value = null
        moves.clear()
        processedEventIds.clear()
        _eventCount.value = 0
    }

    private fun incrementEventCount() {
        _eventCount.value = processedEventIds.size
    }
}

/**
 * Manager for multiple game collectors.
 *
 * This is useful when managing multiple concurrent games,
 * such as in a chess lobby or when spectating multiple games.
 */
class ChessEventCollectorManager {
    private val collectors = ConcurrentHashMap<String, ChessEventCollector>()

    /**
     * Get or create a collector for a game.
     *
     * @param startEventId The start event ID (game identifier)
     */
    fun getOrCreate(startEventId: String): ChessEventCollector = collectors.getOrPut(startEventId) { ChessEventCollector(startEventId) }

    /**
     * Get a collector if it exists.
     *
     * @param startEventId The start event ID (game identifier)
     */
    fun get(startEventId: String): ChessEventCollector? = collectors[startEventId]

    /**
     * Remove a collector for a game.
     *
     * @param startEventId The start event ID (game identifier)
     */
    fun remove(startEventId: String): ChessEventCollector? = collectors.remove(startEventId)

    /**
     * Get all active game IDs (start event IDs).
     */
    fun activeGameIds(): Set<String> = collectors.keys.toSet()

    /**
     * Clear all collectors.
     */
    fun clear() {
        collectors.values.forEach { it.clear() }
        collectors.clear()
    }

    /**
     * Add an event to the appropriate collector (auto-routing).
     * Creates a collector if needed for start events.
     *
     * @return true if the event was added to a collector
     */
    fun addEvent(event: JesterEvent): Boolean {
        // For start events, create collector with event ID
        if (event.isStartEvent()) {
            val collector = getOrCreate(event.id)
            return collector.addEvent(event)
        }

        // For move events, find the collector by startEventId
        val startId = event.startEventId() ?: return false
        val collector = collectors[startId] ?: return false
        return collector.addEvent(event)
    }
}
