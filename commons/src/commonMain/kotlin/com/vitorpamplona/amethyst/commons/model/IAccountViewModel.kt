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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for the account view-model, abstracting Android-specific
 * AccountViewModel for use in multiplatform composables.
 *
 * Implementations should delegate to the platform Account and cache layer.
 */
interface IAccountViewModel {
    /** The underlying account */
    val account: IAccount

    /** Factory for NIP-05 verification clients */
    val nip05ClientBuilder: () -> INip05Client

    // ── Identity helpers ──────────────────────────────────────────

    /** Current user's profile */
    fun userProfile(): User

    /** Whether the given pubkey belongs to the logged-in user */
    fun isLoggedUser(pubkeyHex: HexKey?): Boolean

    /** Whether the given user is the logged-in user */
    fun isLoggedUser(user: User?): Boolean

    /** Whether the account has write permissions */
    fun isWriteable(): Boolean

    /** Whether the logged-in user follows the given user */
    fun isFollowing(user: User?): Boolean

    /** Whether the logged-in user follows the given hex pubkey */
    fun isFollowing(hex: HexKey): Boolean

    // ── Cache lookups ─────────────────────────────────────────────

    /** Retrieve a note from cache, or null */
    fun getNoteIfExists(hex: HexKey): Note?

    /** Retrieve an addressable note from cache by its string key, or null */
    fun getAddressableNoteIfExists(key: String): AddressableNote?

    /** Retrieve or lazily create a user from cache, or null if hex is invalid */
    fun checkGetOrCreateUser(key: HexKey): User?

    /** Retrieve or lazily create a note from cache, or null if hex is invalid */
    fun checkGetOrCreateNote(key: HexKey): Note?

    // ── Signer helpers ────────────────────────────────────────────

    /**
     * Launch a signing action in the background. If the signer is read-only,
     * implementations should surface an appropriate error to the user.
     *
     * Note: the Android implementation is `inline` with `crossinline`; this
     * interface method intentionally omits those modifiers so it can be
     * overridden on all platforms.
     */
    fun launchSigner(action: suspend () -> Unit)

    /** Run block on IO dispatcher */
    fun runOnIO(block: suspend () -> Unit)

    // ── Note display helpers ──────────────────────────────────────

    /**
     * Creates a [StateFlow] that emits whether the "show expand" button
     * should be displayed for the given note (e.g. based on relay count).
     */
    fun createMustShowExpandButtonFlows(note: Note): StateFlow<Boolean>

    /**
     * Decrypt the content of a note and deliver the plaintext to [onReady].
     */
    fun decrypt(
        note: Note,
        onReady: (String) -> Unit,
    )

    // ── Actions ───────────────────────────────────────────────────

    /** Follow a user */
    fun follow(user: User)

    /** Unfollow a user */
    fun unfollow(user: User)

    /** Delete a note */
    fun delete(note: Note)

    /** Hide a user (transient block) */
    fun hide(user: User)

    /** Hide a word */
    fun hide(word: String)
}
