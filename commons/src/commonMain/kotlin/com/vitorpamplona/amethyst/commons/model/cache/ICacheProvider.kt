/**
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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Cache provider interface for accessing cached Notes, Users, and Channels.
 *
 * This abstraction allows Note and User models to access the cache without
 * direct coupling to LocalCache singleton. Platform-specific implementations
 * (Android LocalCache, Desktop DesktopLocalCache) implement this interface.
 *
 * Benefits:
 * - Dependency injection instead of singleton coupling
 * - Testable (can mock for unit tests)
 * - Platform-agnostic model layer
 */
interface ICacheProvider {
    /**
     * Gets a channel by Note reference.
     * Used for resolving relay hints for channel messages.
     *
     * @param note The note to look up channel for
     * @return The channel if found, null otherwise
     */
    fun getAnyChannel(note: Any?): IChannel?

    /**
     * Gets a User by public key hex.
     * Used for updating follower counts and user relationships.
     *
     * @param pubkey The user's public key in hex format
     * @return The User if exists in cache, null otherwise
     */
    fun getUserIfExists(pubkey: HexKey): Any?

    /**
     * Counts users matching a predicate.
     * Used for calculating follower counts.
     *
     * @param predicate Filter function for counting users
     * @return Count of users matching the predicate
     */
    fun countUsers(predicate: (String, Any) -> Boolean): Int

    /**
     * Gets a Note if it exists in cache.
     * Used by ThreadAssembler for finding existing notes.
     *
     * @param hexKey The note's ID in hex format
     * @return The Note if exists in cache, null otherwise
     */
    fun getNoteIfExists(hexKey: HexKey): Any?

    /**
     * Gets an existing Note or creates a new one if it doesn't exist.
     * Used by ThreadAssembler for building thread structures.
     *
     * @param hexKey The note's ID in hex format
     * @return The Note (existing or newly created)
     */
    fun checkGetOrCreateNote(hexKey: HexKey): Any?

    /**
     * Gets the event stream for cache updates.
     * Used by ViewModels to react to new notes and deletions.
     *
     * @return The event stream interface
     */
    fun getEventStream(): ICacheEventStream

    /**
     * Checks if an event has been deleted via NIP-09 deletion events.
     * Used by feed state to filter out deleted notes.
     *
     * @param event The event to check
     * @return true if the event has been deleted, false otherwise
     */
    fun hasBeenDeleted(event: Any): Boolean
}

/**
 * Minimal channel interface for relay resolution.
 * Full channel implementations (PublicChatChannel, LiveActivitiesChannel)
 * implement this interface.
 */
interface IChannel {
    /**
     * Gets the relay URLs for this channel.
     * @return List of relay URLs or null if none configured
     */
    fun relays(): List<Any>?
}
