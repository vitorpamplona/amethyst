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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
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
    fun getAnyChannel(note: Note): Channel?

    /**
     * Gets a User by public key hex.
     * Used for updating follower counts and user relationships.
     *
     * @param pubkey The user's public key in hex format
     * @return The User if exists in cache, null otherwise
     */
    fun getUserIfExists(pubkey: HexKey): User?

    /**
     * Counts users matching a predicate.
     * Used for calculating follower counts.
     *
     * @param predicate Filter function for counting users
     * @return Count of users matching the predicate
     */
    fun countUsers(predicate: (String, User) -> Boolean): Int

    /**
     * Gets a Note if it exists in cache.
     * Used by ThreadAssembler for finding existing notes.
     *
     * @param hexKey The note's ID in hex format
     * @return The Note if exists in cache, null otherwise
     */
    fun getNoteIfExists(hexKey: HexKey): Note?

    /**
     * Gets an existing Note or creates a new one if it doesn't exist.
     * Used by ThreadAssembler for building thread structures.
     *
     * @param hexKey The note's ID in hex format
     * @return The Note (existing or newly created)
     */
    fun checkGetOrCreateNote(hexKey: HexKey): Note?

    /**
     * Gets an existing AddressableNote or creates a new one if it doesn't exist.
     * Used by ThreadAssembler for building thread structures.
     *
     * @param address The note's ID in address format
     * @return The AddressableNote (existing or newly created)
     */
    fun getOrCreateAddressableNote(key: Address): AddressableNote

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

    /**
     * Finds users whose name, displayName, nip05, or lud16 starts with the given prefix.
     * Used by search functionality to find users by name.
     *
     * @param prefix The search prefix to match against user names
     * @param limit Maximum number of results to return
     * @return List of Users matching the prefix
     */
    fun findUsersStartingWith(
        prefix: String,
        limit: Int = 50,
    ): List<User> = emptyList()

    /**
     * Gets or creates a User by public key hex.
     * Used when processing events that reference users.
     *
     * @param pubkey The user's public key in hex format
     * @return The User (existing or newly created)
     */
    fun getOrCreateUser(pubkey: HexKey): User?

    fun justConsumeMyOwnEvent(event: Event): Boolean

    /**
     * Filters all notes in cache into a set using a predicate.
     * Used by feed filters that scan all cached notes.
     *
     * @param predicate Filter function applied to each (key, note) pair
     * @return Set of notes matching the predicate
     */
    fun filterNotesIntoSet(predicate: (HexKey, Note) -> Boolean): Set<Note> = emptySet()

    /**
     * Filters addressable notes by event kind into a set using a predicate.
     *
     * @param kind The event kind to filter by
     * @param predicate Filter function applied to each matching note
     * @return Set of addressable notes matching the kind and predicate
     */
    fun filterAddressablesIntoSet(
        kind: Int,
        predicate: (Any, AddressableNote) -> Boolean,
    ): Set<AddressableNote> = emptySet()

    /**
     * Filters addressable notes by multiple event kinds into a set using a predicate.
     *
     * @param kinds The event kinds to filter by
     * @param predicate Filter function applied to each matching note
     * @return Set of addressable notes matching any of the kinds and predicate
     */
    fun filterAddressablesIntoSet(
        kinds: List<Int>,
        predicate: (Any, AddressableNote) -> Boolean,
    ): Set<AddressableNote> {
        val result = mutableSetOf<AddressableNote>()
        kinds.forEach { kind ->
            result.addAll(filterAddressablesIntoSet(kind, predicate))
        }
        return result
    }

    /**
     * Filters addressable notes by multiple event kinds and author into a set.
     *
     * @param kinds The event kinds to filter by
     * @param pubKey The author's public key hex
     * @param predicate Filter function applied to each matching note
     * @return Set of addressable notes matching
     */
    fun filterAddressables(
        kinds: List<Int>,
        pubKey: HexKey,
        predicate: (Any, AddressableNote) -> Boolean,
    ): Set<AddressableNote> = emptySet()

    /**
     * Gets an AddressableNote if it exists in cache.
     *
     * @param address The addressable note's address
     * @return The AddressableNote if exists, null otherwise
     */
    fun getAddressableNoteIfExists(address: Address): AddressableNote? = null
}
