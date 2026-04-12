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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Cross-platform interface for AccountViewModel.
 *
 * Provides the subset of AccountViewModel's API that can be expressed
 * using only commonMain types (IAccount, Note, User, HexKey, etc.).
 * The Android-specific AccountViewModel implements this interface so
 * that UI files migrated to commonMain can accept IAccountViewModel
 * as a parameter without depending on Android-only types.
 *
 * Usage pattern for migrated files:
 *   Before: fun MyScreen(accountViewModel: AccountViewModel, ...)
 *   After:  fun MyScreen(accountViewModel: IAccountViewModel, ...)
 */
interface IAccountViewModel {
    // ---- Core account access ----

    /** The underlying account, abstracted via IAccount for commonMain use. */
    val account: IAccount

    /** Coroutine scope tied to the ViewModel lifecycle. */
    val scope: CoroutineScope

    // ---- Identity helpers (used by 50+ files) ----

    /** Current user's profile. Delegates to account.userProfile(). */
    fun userProfile(): User

    /** Whether the current account can sign events. */
    fun isWriteable(): Boolean

    /** Check if the given pubkey is the logged-in user. */
    fun isLoggedUser(pubkeyHex: HexKey?): Boolean

    /** Check if the given user is the logged-in user. */
    fun isLoggedUser(user: User?): Boolean

    /** Check if the current account follows the given user. */
    fun isFollowing(user: User?): Boolean

    /** Check if the current account follows the given pubkey. */
    fun isFollowing(user: HexKey): Boolean

    // ---- Cache lookups (used by 30+ files) ----

    fun checkGetOrCreateUser(key: HexKey): User?

    fun getUserIfExists(hex: HexKey): User?

    fun checkGetOrCreateNote(key: HexKey): Note?

    fun getNoteIfExists(hex: HexKey): Note?

    fun getOrCreateAddressableNote(address: Address): AddressableNote?

    fun getAddressableNoteIfExists(key: String): AddressableNote?

    fun getAddressableNoteIfExists(key: Address): AddressableNote?

    // ---- Async helpers ----

    /** Launch a coroutine on IO dispatchers. */
    fun runOnIO(runOnIO: suspend () -> Unit)

    // ---- Actions (commonly used) ----

    fun follow(user: User)

    fun unfollow(user: User)

    fun hide(user: User)

    fun show(user: User)

    fun boost(note: Note)

    fun delete(note: Note)

    fun delete(notes: List<Note>)

    fun broadcast(note: Note)

    fun reactToOrDelete(note: Note)

    fun reactToOrDelete(
        note: Note,
        reaction: String,
    )

    fun decrypt(
        note: Note,
        onReady: (String) -> Unit,
    )

    fun cachedDecrypt(note: Note): String?

    // ---- Bookmark / Pin (used by several files) ----

    fun addPrivateBookmark(note: Note)

    fun addPublicBookmark(note: Note)

    fun removePrivateBookmark(note: Note)

    fun removePublicBookmark(note: Note)

    fun addPin(note: Note)

    fun removePin(note: Note)

    // ---- Navigation helpers ----

    fun loadReactionTo(note: Note?): String?

    fun loadAndMarkAsRead(
        routeForLastRead: String,
        createdAt: Long?,
    ): Boolean

    // ---- Settings delegates ----

    fun defaultZapType(): LnZapEvent.ZapType

    fun reactionChoices(): List<String>

    fun zapAmountChoices(): List<Long>

    fun showSensitiveContent(): MutableStateFlow<Boolean?>

    fun dontTranslateFrom(): Set<String>

    fun translateTo(): String
}
