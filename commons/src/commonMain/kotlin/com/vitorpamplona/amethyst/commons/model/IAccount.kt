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

import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupList
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.IPrivateZapsDecryptionCache
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.DualCase

/**
 * Interface for NIP-47 wallet connect signer state.
 * Used by Note.kt for checking NWC payment status.
 */
interface INwcSignerState {
    suspend fun decryptResponse(event: LnZapPaymentResponseEvent): Response?

    suspend fun decryptRequest(event: LnZapPaymentRequestEvent): Request?

    fun isNIP47Author(pubKey: String?): Boolean
}

/**
 * Hidden content settings for filtering notes.
 * Used by Note.isHiddenFor() to check if content should be hidden.
 */
data class LiveHiddenUsers(
    val showSensitiveContent: Boolean?,
    val hiddenWordsCase: List<DualCase>,
    val hiddenUsersHashCodes: Set<Int>,
    val spammersHashCodes: Set<Int>,
    // Raw sets for amethyst-specific usage
    val hiddenUsers: Set<String> = emptySet(),
    val spammers: Set<String> = emptySet(),
    val hiddenWords: Set<String> = emptySet(),
    val maxHashtagLimit: Int = 5,
) {
    fun isUserHidden(userHex: String) = hiddenUsers.contains(userHex) || spammers.contains(userHex)
}

/**
 * Interface for account operations needed by Note.kt.
 * Abstracts Android-specific Account class for use in commons.
 */
interface IAccount {
    /** NIP-47 wallet connect state for payment verification */
    val nip47SignerState: INwcSignerState

    /** Private zaps decryption cache */
    val privateZapsDecryptionCache: IPrivateZapsDecryptionCache

    /** Current user's profile */
    fun userProfile(): User

    /** Whether account has write permissions */
    fun isWriteable(): Boolean

    /** Nostr signer for signing/encrypting events */
    val signer: NostrSigner

    /** Current user's public key */
    val pubKey: String

    /** Content filter settings */
    val showSensitiveContent: Boolean?
    val hiddenWordsCase: List<DualCase>
    val hiddenUsersHashCodes: Set<Int>
    val spammersHashCodes: Set<Int>

    /** Set of followed user pubkeys (for feed ordering/highlighting) */
    fun followingKeySet(): Set<String>

    fun isHidden(user: User): Boolean

    /** Chatroom list for private DM conversations */
    val chatroomList: ChatroomList

    /** Marmot MLS group chat list */
    val marmotGroupList: MarmotGroupList

    /** Whether a note is acceptable (not hidden, not blocked, etc.) */
    fun isAcceptable(note: Note): Boolean

    /** Send a NIP-04 encrypted direct message */
    suspend fun sendNip04PrivateMessage(eventTemplate: EventTemplate<PrivateDmEvent>)

    /** Send a NIP-17 gift-wrapped direct message */
    suspend fun sendNip17PrivateMessage(template: EventTemplate<ChatMessageEvent>)

    /** Send a NIP-17 gift-wrapped encrypted file header */
    suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>)

    /** Broadcast pre-created gift wraps (e.g. reactions within group DMs) */
    suspend fun sendGiftWraps(wraps: List<GiftWrapEvent>)

    // =========================================================================
    // Signer wrapper methods
    // Non-inline suspend methods that encapsulate common launchSigner patterns.
    // These enable gradual migration from AccountViewModel.launchSigner { ... }
    // to direct IAccount method calls, supporting KMP/iOS usage.
    // =========================================================================

    // -- Follow / Unfollow --

    /** Follow a single user */
    suspend fun follow(user: User)

    /** Follow multiple users */
    suspend fun follow(users: List<User>)

    /** Unfollow a user */
    suspend fun unfollow(user: User)

    /** Follow a community */
    suspend fun follow(community: AddressableNote)

    /** Unfollow a community */
    suspend fun unfollow(community: AddressableNote)

    /** Follow a public chat channel */
    suspend fun follow(channel: PublicChatChannel)

    /** Unfollow a public chat channel */
    suspend fun unfollow(channel: PublicChatChannel)

    /** Follow an ephemeral chat channel */
    suspend fun follow(channel: EphemeralChatChannel)

    /** Unfollow an ephemeral chat channel */
    suspend fun unfollow(channel: EphemeralChatChannel)

    /** Follow a hashtag */
    suspend fun followHashtag(tag: String)

    /** Unfollow a hashtag */
    suspend fun unfollowHashtag(tag: String)

    /** Follow a geohash */
    suspend fun followGeohash(geohash: String)

    /** Unfollow a geohash */
    suspend fun unfollowGeohash(geohash: String)

    /** Follow a relay feed */
    suspend fun followRelayFeed(url: NormalizedRelayUrl)

    /** Unfollow a relay feed */
    suspend fun unfollowRelayFeed(url: NormalizedRelayUrl)

    // -- Notes: React, Boost, Delete, Broadcast --

    /** React to a note with the given reaction string */
    suspend fun reactTo(
        note: Note,
        reaction: String,
    )

    /** Boost (repost) a note */
    suspend fun boost(note: Note)

    /** Delete a single note */
    suspend fun delete(note: Note)

    /** Delete multiple notes */
    suspend fun delete(notes: List<Note>)

    /** Broadcast a note to relays */
    suspend fun broadcast(note: Note)

    // -- Reporting --

    /** Report a note */
    suspend fun report(
        note: Note,
        type: ReportType,
        content: String = "",
    )

    /** Report a user */
    suspend fun report(
        user: User,
        type: ReportType,
        content: String = "",
    )

    // -- Bookmarks --

    /** Add a note to bookmarks */
    suspend fun addBookmark(
        note: Note,
        isPrivate: Boolean,
    )

    /** Remove a note from bookmarks (specific privacy) */
    suspend fun removeBookmark(
        note: Note,
        isPrivate: Boolean,
    )

    /** Remove a note from bookmarks (both public and private) */
    suspend fun removeBookmark(note: Note)

    // -- Pins --

    /** Pin a note */
    suspend fun addPin(note: Note)

    /** Unpin a note */
    suspend fun removePin(note: Note)

    // -- Mute / Block --

    /** Hide a word from content */
    suspend fun hideWord(word: String)

    /** Show a previously hidden word */
    suspend fun showWord(word: String)

    /** Hide a user by pubkey */
    suspend fun hideUser(pubkeyHex: String)

    /** Show a previously hidden user */
    suspend fun showUser(pubkeyHex: String)
}
