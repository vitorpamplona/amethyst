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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.components.toasts.IToastManager
import com.vitorpamplona.amethyst.commons.ui.screen.IUiSettingsState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic interface for AccountViewModel.
 *
 * Exposes the most commonly used members of AccountViewModel using only
 * commons-compatible types. Composables and ViewModels in the commons module
 * can depend on this interface instead of the concrete Android AccountViewModel,
 * enabling incremental migration to KMP.
 *
 * Members are ordered by usage frequency (most-referenced first).
 */
interface IAccountViewModel {
    // ── core properties (475+ usages) ─────────────────────────────────

    /** The underlying account abstraction. */
    val account: IAccount

    /** UI settings (connectivity-aware display preferences). 78 usages / 37 files. */
    val settings: IUiSettingsState

    /** Toast/snackbar manager. 119 usages / 48 files. */
    val toastManager: IToastManager

    // ── identity helpers (50-20 usages) ───────────────────────────────

    /** Current user's profile. */
    fun userProfile(): User

    /** Whether the account can sign events. */
    fun isWriteable(): Boolean

    /** Whether the given pubkey is the logged-in user. */
    fun isLoggedUser(pubkeyHex: HexKey?): Boolean

    /** Whether the given user is the logged-in user. */
    fun isLoggedUser(user: User?): Boolean

    /** Whether the logged-in user follows the given user. */
    fun isFollowing(user: User?): Boolean

    /** Whether the logged-in user follows the given pubkey. */
    fun isFollowing(user: HexKey): Boolean

    // ── cache access (33-10 usages) ──────────────────────────────────

    /** Get a note from cache if it exists. */
    fun getNoteIfExists(hex: HexKey): Note?

    /** Get or create a user in cache. */
    fun checkGetOrCreateUser(key: HexKey): User?

    /** Get a user from cache if it exists. */
    fun getUserIfExists(hex: HexKey): User?

    /** Get or create a note in cache. */
    fun checkGetOrCreateNote(key: HexKey): Note?

    /** Get or create an addressable note in cache. */
    fun getOrCreateAddressableNote(address: Address): AddressableNote

    // ── coroutine helpers ─────────────────────────────────────────────

    /** Launch a coroutine on IO. */
    fun runOnIO(runOnIO: suspend () -> Unit)

    // ── follow / unfollow (10-8 usages each) ─────────────────────────

    fun follow(user: User)

    fun follow(users: List<User>)

    fun follow(community: AddressableNote)

    fun follow(channel: PublicChatChannel)

    fun follow(channel: EphemeralChatChannel)

    fun unfollow(user: User)

    fun unfollow(community: AddressableNote)

    fun unfollow(channel: PublicChatChannel)

    fun unfollow(channel: EphemeralChatChannel)

    fun followHashtag(tag: String)

    fun unfollowHashtag(tag: String)

    fun followGeohash(tag: String)

    fun unfollowGeohash(tag: String)

    fun followRelayFeed(url: NormalizedRelayUrl)

    fun unfollowRelayFeed(url: NormalizedRelayUrl)

    // ── note actions (7-10 usages each) ──────────────────────────────

    fun broadcast(note: Note)

    fun delete(note: Note)

    fun delete(notes: List<Note>)

    fun hide(user: User)

    fun show(user: User)

    fun hide(word: String)

    fun showWord(word: String)

    fun hideWord(word: String)

    fun showUser(pubkeyHex: String)

    fun report(
        note: Note,
        type: com.vitorpamplona.quartz.nip56Reports.ReportType,
        content: String,
    )

    // ── settings accessors (9+ usages) ───────────────────────────────

    fun showSensitiveContent(): MutableStateFlow<Boolean?>

    fun zapAmountChoices(): List<Long>

    fun zapAmountChoicesFlow(): StateFlow<List<Long>>

    fun reactionChoices(): List<String>

    fun reactionChoicesFlow(): StateFlow<List<String>>

    fun defaultZapType(): com.vitorpamplona.quartz.nip57Zaps.LnZapEvent.ZapType

    fun dontTranslateFrom(): Set<String>

    fun translateTo(): String

    fun filterSpamFromStrangers(): MutableStateFlow<Boolean>

    // ── misc (5-9 usages) ────────────────────────────────────────────

    fun prefer(
        source: String,
        target: String,
        preference: String,
    )

    /** Decrypt a note's content from cache or null. */
    fun cachedDecrypt(note: Note): String?

    /** Decrypt a note's content, calling onReady when done. */
    fun decrypt(
        note: Note,
        onReady: (String) -> Unit,
    )

    fun markDonatedInThisVersion(): Boolean

    fun dismissPollNotification(noteId: String)

    fun hasViewedPollResults(noteId: String): Boolean

    fun markPollResultsViewed(
        noteId: String,
        pollEndsAt: Long?,
    )
}
