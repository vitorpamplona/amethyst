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
package com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * The account's own kind:30382 contact cards — one card per target user, signed
 * by the account's main key. This is how the user nicknames other users: the
 * petname and summary always live in the card's NIP-44 encrypted content.
 *
 * The same kind is used by trust providers for WoT scores; those cards are
 * ignored here because everything is keyed on the card author being the
 * account itself ([signer]'s pubkey).
 */
@Stable
class ContactCardsState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val decryptionCache: ContactCardDecryptionCache,
    val scope: CoroutineScope,
) {
    private val accountUser: User? by lazy { cache.getOrCreateUser(signer.pubKey) }

    fun createCardAddress(target: HexKey): Address = ContactCardEvent.createAddress(signer.pubKey, target)

    fun getCardNote(target: HexKey): AddressableNote = cache.getOrCreateAddressableNote(createCardAddress(target))

    fun getCard(target: HexKey): ContactCardEvent? = getCardNote(target).event as? ContactCardEvent

    /**
     * The account's own card about [target], as attached to the target user's
     * [UserCardsCache] when the event is consumed. Reads from the existing
     * received-cards map so displaying users without a card allocates nothing new.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun myCardFlow(target: User): Flow<ContactCardEvent?> =
        target
            .cards()
            .receivedCards
            .map { it[accountUser] }
            .distinctUntilChanged()
            .flatMapLatest { note ->
                note
                    ?.flow()
                    ?.metadata
                    ?.stateFlow
                    ?.map { it.note.event as? ContactCardEvent }
                    ?: flowOf(null)
            }

    /** The petname the account gave [target], decrypted from the card's content. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun petNameFlow(target: User): Flow<String?> =
        myCardFlow(target)
            .mapLatest { card -> card?.let { decryptionCache.petName(it) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    suspend fun petName(target: HexKey): String? = getCard(target)?.let { decryptionCache.petName(it) }

    suspend fun summary(target: HexKey): String? = getCard(target)?.let { decryptionCache.summary(it) }

    /**
     * Builds the new signed card for [target] with the given petname and summary
     * (both stored NIP-44 encrypted; `null` clears the field), preserving every
     * other tag of an existing card. The caller is responsible for publishing it.
     */
    suspend fun updatePetNameAndSummary(
        target: HexKey,
        petName: String?,
        summary: String?,
    ): ContactCardEvent {
        val existing = getCard(target)
        return if (existing != null) {
            ContactCardEvent.updatePetNameAndSummary(
                earlierVersion = existing,
                petName = petName,
                summary = summary,
                signer = signer,
            )
        } else {
            ContactCardEvent.create(
                targetUser = target,
                petName = petName,
                summary = summary,
                signer = signer,
            )
        }
    }
}
